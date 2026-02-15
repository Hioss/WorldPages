package com.hioss.spider;

import com.hioss.spider.dto.HotItem;
import com.hioss.spider.news.GetIthomeDailyNews;

import java.util.List;

/**
 * 测试：抓取 IT之家 日榜 新闻
 *
 * 直接运行 main 方法即可在控制台看到抓取结果
 */
public class TestGetIthomeDailyNews {

    public static void main(String[] args) {
        System.out.println("开始抓取 IT之家 日榜新闻...");

        GetIthomeDailyNews spider = new GetIthomeDailyNews();
        List<HotItem> list = spider.start();

        System.out.println("抓取完成，共获取 " + list.size() + " 条：");
        System.out.println("--------------------------------------------------");

        int i = 1;
        for (HotItem item : list) {
            System.out.println(i + ". " + item.getTitle());
            System.out.println("   " + item.getLink());
            i++;
        }

        System.out.println("--------------------------------------------------");
        System.out.println("测试结束");
    }
}