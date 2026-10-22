package com.hioss.spider.news;

import com.hioss.spider.dto.HotItem;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class GetIthomeDailyNewsTest {
    private static String items(int count, String prefix) {
        StringBuilder html = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            html.append("<li><a href='/0/123/").append(i).append(".htm'>")
                    .append(prefix).append(i).append("</a></li>");
        }
        return html.toString();
    }

    private static String rank(String contents) {
        return "<div id='rank'><ul class='bar'><li>日榜</li><li>周榜</li></ul>"
                + "<ul id='d-1'>" + contents + "</ul>"
                + "<ul id='d-2'>" + items(12, "周榜") + "</ul></div>";
    }

    @Test
    public void readsOnlyDailyRankInOrderAndLimitsToTen() {
        List<HotItem> result = GetIthomeDailyNews.parseDailyRank(
                "<p>日榜</p><a href='https://www.ithome.com/0/123/999.htm'>无关</a>"
                        + rank(items(12, "日榜")));
        assertEquals(10, result.size());
        assertEquals("日榜1", result.get(0).getTitle());
        assertEquals("日榜10", result.get(9).getTitle());
        assertEquals("https://www.ithome.com/0/123/1.htm", result.get(0).getLink());
    }

    @Test
    public void handlesEntitiesQuotesRelativeUrlsAndDuplicates() {
        List<HotItem> result = GetIthomeDailyNews.parseDailyRank(rank(
                "<li><a href='//www.ithome.com/0/123/1.htm'><b>A</b> &amp; &#x4E2D;</a></li>"
                        + "<li><a href='http://www.ithome.com/0/123/1.htm?from=rank#top'>重复</a></li>"
                        + "<li><a href='/0/123/2.htm'>第二条</a></li>"));
        assertEquals(2, result.size());
        assertEquals("A & 中", result.get(0).getTitle());
    }

    @Test
    public void rejectsAdvertisementsAndLookalikeHosts() {
        assertTrue(GetIthomeDailyNews.parseDailyRank(rank(
                "<li><a href='https://www.ithome.com.evil.test/0/123/1.htm'>假域名</a></li>"
                        + "<li><a href='https://evil.test/?ithome.com'>外站</a></li>"
                        + "<li><a href='https://lapin.ithome.com/0/123/1.htm'>广告</a></li>"
                        + "<li><a href='/about.htm'>关于</a></li>"
                        + "<li><a href='javascript:void(0)'>无效</a></li>")).isEmpty());
    }

    @Test
    public void missingDailyRankDoesNotFallThroughToWeeklyRank() {
        assertTrue(GetIthomeDailyNews.parseDailyRank(null).isEmpty());
        assertTrue(GetIthomeDailyNews.parseDailyRank("<h1>请稍后重试</h1>").isEmpty());
        assertTrue(GetIthomeDailyNews.parseDailyRank(
                "<div id='rank'>日榜<ul id='d-2'>" + items(12, "周榜") + "</ul></div>").isEmpty());
    }

    @Test
    public void retriesHttpErrorsAndInvalidSuccessfulResponses() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, 503, "<html>temporarily unavailable</html>",
                "<html>请稍后重试</html>", rank(items(12, "恢复")));
        try {
            List<HotItem> result = crawler(server).start();
            assertEquals(3, requests.get());
            assertEquals(10, result.size());
            assertEquals("恢复1", result.get(0).getTitle());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void retriesForbiddenResponse() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, 403, "Forbidden", rank(items(10, "恢复")));
        try {
            assertEquals(10, crawler(server).start().size());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void retriesReadTimeout() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        var executor = Executors.newCachedThreadPool();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            int index = requests.getAndIncrement();
            byte[] body = rank(items(10, "恢复")).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) {
                if (index == 0) {
                    stream.write(body, 0, 1);
                    stream.flush();
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    stream.write(body, 1, body.length - 1);
                } else {
                    stream.write(body);
                }
            }
        });
        server.start();
        try {
            GetIthomeDailyNews crawler = crawler(server);
            crawler.getSite().setTimeOut(500);
            assertEquals(10, crawler.start().size());
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    public void exhaustedRetriesFailInsteadOfPublishingEmptyOrPartialData() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, 200, rank(items(2, "残缺")));
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> crawler(server).start());
            assertTrue(error.getMessage().contains("连续 3 次"));
            assertEquals(3, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void repeatedStartFetchesFreshResultsWithoutMutatingPreviousResult() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, 200, rank(items(10, "第一天")), rank(items(10, "第二天")));
        try {
            GetIthomeDailyNews crawler = crawler(server);
            List<HotItem> first = crawler.start();
            List<HotItem> second = crawler.start();
            assertEquals(2, requests.get());
            assertEquals("第一天1", first.get(0).getTitle());
            assertEquals("第二天1", second.get(0).getTitle());
        } finally {
            server.stop(0);
        }
    }

    private static GetIthomeDailyNews crawler(HttpServer server) {
        return new GetIthomeDailyNews("http://127.0.0.1:" + server.getAddress().getPort() + "/", 0);
    }

    private static HttpServer server(AtomicInteger requests, int firstStatus, String... responses) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int index = requests.getAndIncrement();
            byte[] body = responses[Math.min(index, responses.length - 1)].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(index == 0 ? firstStatus : 200, body.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(body);
            }
        });
        server.start();
        return server;
    }
}
