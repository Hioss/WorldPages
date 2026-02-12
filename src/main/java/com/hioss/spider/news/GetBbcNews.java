package com.hioss.spider.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.hioss.spider.dto.HotItem;

import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * BBC中文网 热门内容
 * 说明：改为抓取 RSS（繁体）前 10 条新闻
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-18
 *
 */
public class GetBbcNews implements PageProcessor {

    // 目标：取 10 条
    private static final int LIMIT = 10;

    // RSS（繁体）
    private static final String RSS_URL = "https://feeds.bbci.co.uk/zhongwen/trad/rss.xml";

    // 爬虫结果（⚠️ List 格式不变：仍然返回 List<HotItem>）
    private final List<HotItem> list = new ArrayList<>();

    // 模拟浏览器的反爬虫设置
    private Site site = Site.me()
            .setCharset("UTF-8")  // 设置网页编码
            .setRetryTimes(2)     // 设置请求失败重试次数
            .setSleepTime(1500)   // 设置每次请求的间隔时间（毫秒）
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/rss+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");

    /**
     * 找到JSON中items层级（网页中唯一的关键字【"items":】）
     *
     * @param node 待处理JSON
     * @return 完整的JSON字符串
     */
    public static List<JsonNode> findItems(JsonNode node) {
        List<JsonNode> result = new ArrayList<>();

        if (node != null && node.has("items")) {
            result.add(node.get("items"));
        }

        // 遍历所有子节点继续找
        if (node != null) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                result.addAll(findItems(it.next()));
            }
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
     * 主要业务逻辑：解析 RSS XML，取前 10 条
     */
    @Override
    public void process(Page page) {
        String xml = page.getRawText();
        if (xml == null || xml.trim().isEmpty()) {
            return;
        }

        try {
            // 安全配置：禁止外部实体（防 XXE）
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {
                // 某些 JRE/实现不支持特性时忽略
            }
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder builder = dbf.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList items = doc.getElementsByTagName("item");
            int count = 0;

            for (int i = 0; i < items.getLength() && count < LIMIT; i++) {
                Node n = items.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;

                Element item = (Element) n;

                String title = getText(item, "title");
                String link = getText(item, "link");

                if (title == null || title.trim().isEmpty()) continue;
                if (link == null || link.trim().isEmpty()) continue;

                HotItem dto = new HotItem();
                dto.setTitle(title.trim());
                dto.setLink(link.trim());
                list.add(dto);

                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getText(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n != null ? n.getTextContent() : null;
    }

    @Override
    public Site getSite() {
        return site;
    }

    /**
     * 提供给外部调用的方法（⚠️ 返回 List<HotItem> 不变）
     */
    public List<HotItem> start() {
        Spider.create(this)
                .addUrl(RSS_URL)
                .thread(1)
                .run();

        return this.list;
    }
}
