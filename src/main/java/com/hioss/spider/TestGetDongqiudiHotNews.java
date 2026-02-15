package com.hioss.spider;

import com.hioss.spider.dto.HotItem;
import com.hioss.spider.news.GetDongqiudiHotNews;

import java.util.List;

public class TestGetDongqiudiHotNews {

    public static void main(String[] args) {
        System.out.println("开始抓取 懂球帝 热门推荐(接口版) ...");

        GetDongqiudiHotNews spider = new GetDongqiudiHotNews();
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