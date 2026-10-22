package com.hioss.spider.news;

import com.hioss.spider.dto.HotItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** IT之家首页日榜前 10 条。 */
public class GetIthomeDailyNews implements PageProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(GetIthomeDailyNews.class);
    private static final int LIMIT = 10;
    private static final int MAX_ATTEMPTS = 3;
    private static final String HOME_URL = "https://www.ithome.com/";

    private final String homeUrl;
    private final long retryDelayMillis;
    private final List<HotItem> list = new ArrayList<>();
    private final Site site = Site.me()
            .setCharset("UTF-8")
            .setTimeOut(30_000)
            // start 统一重试下载失败和无效内容，避免底层重试叠加。
            .setRetryTimes(0)
            .setSleepTime(0)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Referer", HOME_URL);

    public GetIthomeDailyNews() {
        this(HOME_URL, 2_000);
    }

    // 回归测试通过本地 HTTP 服务复现异常响应。
    GetIthomeDailyNews(String homeUrl, long retryDelayMillis) {
        this.homeUrl = homeUrl;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public void process(Page page) {
        page.setSkip(true);
        List<HotItem> parsed = parseDailyRank(page.getRawText());
        LOG.info("IT之家日榜响应: url={}, status={}, chars={}, items={}",
                homeUrl, page.getStatusCode(),
                page.getRawText() == null ? 0 : page.getRawText().length(), parsed.size());
        // 不把残缺榜单、拦截页或周/月榜当作成功结果。
        if (parsed.size() == LIMIT) {
            list.clear();
            list.addAll(parsed);
        }
    }

    static List<HotItem> parseDailyRank(String html) {
        List<HotItem> result = new ArrayList<>();
        if (html == null || html.isBlank()) return result;
        Element daily = Jsoup.parse(html, HOME_URL).selectFirst("#rank #d-1");
        if (daily == null) return result;

        Set<String> seen = new HashSet<>();
        for (Element anchor : daily.select("li a[href]")) {
            String title = anchor.text().replace('\u00a0', ' ').trim();
            String link = normalizeNewsUrl(anchor.absUrl("href"));
            if (title.isEmpty() || link == null || !seen.add(link)) continue;
            result.add(new HotItem(title, link));
            if (result.size() == LIMIT) break;
        }
        return result;
    }

    private static String normalizeNewsUrl(String link) {
        try {
            URI uri = URI.create(link);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !"www.ithome.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getPath() == null || !uri.getPath().matches("/(?:\\d+/)+\\d+\\.htm")) {
                return null;
            }
            return "https://www.ithome.com" + uri.getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Site getSite() {
        return site;
    }

    public List<HotItem> start() {
        list.clear();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 新调度器保证相同 URL 不会被上一轮请求的去重记录跳过。
            Spider spider = Spider.create(this).addUrl(homeUrl).thread(1);
            try {
                spider.run();
            } finally {
                spider.close();
            }
            if (list.size() == LIMIT) return new ArrayList<>(list);

            LOG.warn("IT之家日榜抓取失败或榜单不完整: url={}, attempt={}/{}",
                    homeUrl, attempt, MAX_ATTEMPTS);
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(retryDelayMillis * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("IT之家日榜重试被中断", e);
                }
            }
        }
        // 阻止把空榜作为当天有效数据发布。
        throw new IllegalStateException("IT之家日榜连续 " + MAX_ATTEMPTS + " 次抓取失败: " + homeUrl);
    }
}
