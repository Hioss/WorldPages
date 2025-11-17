package com.hioss.spider.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hioss.spider.dto.HotItem;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * BBC中文网 热门内容
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-18
 * 
 */
public class GetBbcNews  implements PageProcessor {

    //爬虫结果
    private final List<HotItem> list = new ArrayList<>();

    //模拟浏览器的反爬虫设置
    private Site site = Site.me()
            .setCharset("UTF-8")  //设置网页编码
            .setRetryTimes(2)     //设置请求失败重试次数
            .setSleepTime(5000)   //设置每次请求的间隔时间（毫秒）
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");

    /**
     * 找到JSON中items层级（网页中唯一的关键字【"items":】）
     *
     * @param node 待处理JSON
     * @return 完整的JSON字符串
     */
    public static List<JsonNode> findItems(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();

        if (node.has("items")) {
            result.add(node.get("items"));
        }

        // 遍历所有子节点继续找
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            result.addAll(findItems(it.next()));
        }

        return result;
    }

    /**
     * 找到 JSON 对象的完整结束位置
     *
     * @param text 待处理字符串
     * @param startIndex 开始位置
     * @return 完整的JSON字符串
     */
    private String extractJsonObject(String text, int startIndex) {
        int braceCount = 0;
        int endIndex = startIndex;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;

            if (braceCount == 0 && i > startIndex) {
                endIndex = i + 1;
                break;
            }
        }

        return text.substring(startIndex, endIndex);
    }

    /**
     * 主要业务逻辑
     */
    @Override
    public void process(Page page) {
        //判断是否取得了网页源代码
        String html = page.getRawText();
        if (html == null || html.trim().isEmpty()) {
            return;
        }
        // 提取 JSON 内容
        int pos = html.indexOf("\"status\":200");
        if (pos == -1) {
            return;
        }

        //第一个{开始的位置
        int start = html.lastIndexOf("{", pos);

        //得到完整的JSON
        String jsonText = extractJsonObject(html, start);

        try {
            // 解析 JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode arrayNode = mapper.readTree(jsonText);

            // 找到所有 items
            List<JsonNode> itemsList = findItems(arrayNode);

            //输出热门内容的标题和链接
            for (JsonNode items : itemsList) {
                for (JsonNode item : items) {
                    if (item.has("title")) {
                        String title = item.get("title").asText();
                        String link = item.get("href").asText();

                        HotItem dto = new HotItem();
                        dto.setTitle(title);
                        dto.setLink(link);
                        
                        list.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                .addUrl("https://www.bbc.com/zhongwen/simp")
                .thread(1)
                .run();

        return this.list;
    }
}