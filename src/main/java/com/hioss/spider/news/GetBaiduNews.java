package com.hioss.spider.news;

import com.hioss.spider.dto.HotItem;

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
        // 标题列表
        List<String> titles = page.getHtml()
                .css("div.c-single-text-ellipsis", "text")
                .all();

        // 链接列表
        List<String> links = page.getHtml()
                .css("div.category-wrap_iQLoo .title_dIF3B", "href")
                .all();

        // 保留前 10 条
        int count = Math.min(10, titles.size());

        for (int i = 0; i < count; i++) {
            HotItem dto = new HotItem();
            dto.setTitle(titles.get(i));
            dto.setLink(links.get(i));

            list.add(dto);
        }
    }

    @Override
    public Site getSite() {
        return site;
    }

    /**
     * 提供给外部调用的方法
     */
    public List<HotItem> start() {
        Spider.create(this)
                .addUrl("https://top.baidu.com/board?tab=realtime")
                .thread(1)
                .run();

        return this.list;
    }
}