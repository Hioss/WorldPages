package com.hioss.spider.dto;

/**
 * 保存新闻标题与链接的DTO类
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-18
 * 
 */
public class HotItem {
    private String title;
    private String link;

    public HotItem() {
    }

    public HotItem(String title, String link) {
        this.title = title;
        this.link = link;
    } 

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}