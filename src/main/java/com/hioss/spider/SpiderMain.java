package com.hioss.spider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class SpiderMain {

    private static final String BBC_URL = "https://www.bbc.com/zhongwen/simp";
    private static final String BAIDU_API = "https://zj.v.api.aa1.cn/api/baidu-rs/";
    private static final String TOUTIAO_API = "https://dabenshi.cn/other/api/hot.php?type=toutiaoHot";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        String dateStr = today.toString();

        // --- BBC ---
        List<HotItem> bbc = fetchBBC();

        // --- 百度 ---
        List<HotItem> baidu = fetchApi(BAIDU_API);

        // --- 头条 ---
        List<HotItem> toutiao = fetchApi(TOUTIAO_API);

        // --- 创建 JSON ----
        ObjectNode root = MAPPER.createObjectNode();
        root.put("date", dateStr);
        root.set("bbc", toArrayNode(bbc));
        root.set("baidu", toArrayNode(baidu));
        root.set("toutiao", toArrayNode(toutiao));

        Path dataDir = Paths.get("docs", "data");
        if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

        Path todayFile = dataDir.resolve("hot-" + dateStr + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(todayFile.toFile(), root);

        // --- 清理旧文件 ---
        cleanOldFiles(dataDir, 10);

        // --- index.json ---
        generateIndexJson(dataDir);
    }

    // ===== BBC 中文网 =====
    private static List<HotItem> fetchBBC() {
        List<HotItem> list = new ArrayList<>();

        PageProcessor processor = new PageProcessor() {
            Site site = Site.me().setRetryTimes(3).setSleepTime(1000).setCharset("UTF-8");

            @Override
            public void process(Page page) {
                List<String> titles = page.getHtml()
                        .css("a:matchesOwn(.*)", "text")
                        .regex(".*") // 可以后续调优
                        .all();

                List<String> links = page.getHtml()
                        .links()
                        .regex("https://www\\.bbc\\.com/zhongwen/simp/.*")
                        .all();

                // 一起匹配
                for (int i = 0; i < Math.min(titles.size(), links.size()); i++) {
                    String title = titles.get(i).trim();
                    String link = links.get(i).trim();
                    if (!title.isBlank() && !link.isBlank()) {
                        list.add(new HotItem(title, link));
                    }
                }
            }

            @Override
            public Site getSite() { return site; }
        };

        Spider.create(processor).addUrl(BBC_URL).thread(1).run();
        return list;
    }

    // ===== API 抓取 =====
    private static List<HotItem> fetchApi(String url) {
        List<HotItem> list = new ArrayList<>();
        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            String body = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();

            JsonNode root = MAPPER.readTree(body);
            JsonNode arr = null;

            if (root.isArray()) arr = root;
            else if (root.has("data") && root.get("data").isArray()) arr = root.get("data");
            else if (root.has("list") && root.get("list").isArray()) arr = root.get("list");

            if (arr != null) {
                for (JsonNode item : arr) {
                    String title = getField(item, "title", "word", "name");
                    String link = getField(item, "url", "link", "href");
                    if (title != null && link != null) {
                        list.add(new HotItem(title, link));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static String getField(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText();
                if (!v.isBlank()) return v;
            }
        }
        return null;
    }

    private static ArrayNode toArrayNode(List<HotItem> list) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (HotItem i : list) {
            ObjectNode o = MAPPER.createObjectNode();
            o.put("title", i.title);
            o.put("link", i.link);
            arr.add(o);
        }
        return arr;
    }

    // ===== 清理旧文件 =====
    private static void cleanOldFiles(Path dir, int keepDays) throws IOException {
        List<Path> files = Files.list(dir)
                .filter(f -> f.getFileName().toString().startsWith("hot-"))
                .collect(Collectors.toList());

        List<PathWithDate> list = new ArrayList<>();
        for (Path p : files) {
            try {
                String d = p.getFileName().toString().substring(4, 14);
                list.add(new PathWithDate(p, LocalDate.parse(d)));
            } catch (Exception ignored) {}
        }

        list.sort((a, b) -> b.date.compareTo(a.date));

        for (int i = keepDays; i < list.size(); i++) {
            Files.deleteIfExists(list.get(i).path);
        }
    }

    // ===== index.json =====
    private static void generateIndexJson(Path dir) throws IOException {
        List<PathWithDate> list = Files.list(dir)
                .filter(f -> f.getFileName().toString().startsWith("hot-"))
                .map(f -> {
                    try {
                        String d = f.getFileName().toString().substring(4, 14);
                        return new PathWithDate(f, LocalDate.parse(d));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull)
                .sorted((a, b) -> b.date.compareTo(a.date))
                .collect(Collectors.toList());

        ArrayNode arr = MAPPER.createArrayNode();
        for (PathWithDate pw : list) arr.add(pw.date.toString());

        ObjectNode root = MAPPER.createObjectNode();
        root.set("dates", arr);

        Path indexJson = dir.resolve("index.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(indexJson.toFile(), root);
    }

    // ===== 辅助类 =====
    static class HotItem {
        public String title;
        public String link;

        HotItem(String title, String link) {
            this.title = title;
            this.link = link;
        }
    }

    static class PathWithDate {
        Path path;
        LocalDate date;

        PathWithDate(Path path, LocalDate date) {
            this.path = path;
            this.date = date;
        }
    }
}
