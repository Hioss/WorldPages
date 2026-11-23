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

import java.util.ArrayList;
import java.util.List;

/**
 * 百度热搜
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-20
 * 
 */
public class GetBaiduNews implements PageProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(GetBaiduNews.class);
    private static final String BOARD_URL = "https://top.baidu.com/board?tab=realtime";
    private static final int LIMIT = 10;

    //爬虫结果
    private final List<HotItem> list = new ArrayList<>();

    //模拟浏览器的反爬虫设置
    private Site site = Site.me()
            .setCharset("UTF-8")  //设置网页编码
            .setRetryTimes(2)     //设置请求失败重试次数
            .setSleepTime(5000)   //设置每次请求的间隔时间（毫秒）
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");

    /**
     * 主要业务逻辑
     */
    @Override
    public void process(Page page) {
        List<HotItem> parsed = parseHotItems(page.getRawText());
        if (parsed.isEmpty()) {
            LOG.warn("百度热搜页面未解析出榜单条目，HTTP 状态码 {}", page.getStatusCode());
        }
        list.addAll(parsed);
    }

    static List<HotItem> parseHotItems(String html) {
        List<HotItem> result = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return result;
        }

        // 从标题的父链接取地址，避免依赖带构建后缀的类名。
        for (Element titleElement : Jsoup.parse(html, BOARD_URL).select("a[href] > div.c-single-text-ellipsis")) {
            Element anchor = titleElement.parent();
            String title = titleElement.text();
            if (title.isEmpty() || anchor.attr("href").isBlank()) {
                continue;
            }
            String link = anchor.absUrl("href");
            if (!link.startsWith("https://") && !link.startsWith("http://")) {
                continue;
            }
            result.add(new HotItem(title, link));
            if (result.size() == LIMIT) {
                break;
            }
        }
        return result;
    }

    @Override
    public Site getSite() {
        return site;
    }

    /**
     * 提供给外部调用的方法
     */
    public List<HotItem> start() {
        list.clear();
        Spider.create(this)
                .addUrl(BOARD_URL)
                .thread(1)
                .run();

        return new ArrayList<>(list);
    }
}
