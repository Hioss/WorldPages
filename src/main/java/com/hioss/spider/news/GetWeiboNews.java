package com.hioss.spider.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hioss.spider.dto.HotItem;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 微博热搜
 * <p>直接从微博官网热搜页对应的官方接口抓取数据：
 * <a href="https://weibo.com/hot/search">微博热搜页面</a>
 * <a href="https://weibo.com/ajax/side/hotSearch">微博热搜接口</a>
 * @author      程春海
 * @version     1.0
 * @since       2026-02-15
 */
public class GetWeiboNews {

    private static final int LIMIT = 10;
    private static final String HOT_SEARCH_URL = "https://weibo.com/ajax/side/hotSearch";
    private static final String REFERER_URL = "https://weibo.com/hot/search";
    private static final String SEARCH_LINK_PREFIX = "https://s.weibo.com/weibo?q=";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 提供给外部调用的方法
     */
    public List<HotItem> start() {
        String json = fetchHotSearchJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode realtime = root.path("data").path("realtime");
            if (!realtime.isArray() || realtime.isEmpty()) {
                return List.of();
            }

            List<HotItem> list = new ArrayList<>(LIMIT);
            for (JsonNode item : realtime) {
                if (list.size() >= LIMIT) {
                    break;
                }
                if (item == null || !item.isObject()) {
                    continue;
                }

                // 跳过广告项
                if (item.path("is_ad").asInt(0) == 1 || item.path("topic_ad").asInt(0) == 1) {
                    continue;
                }

                String title = textOrNull(item, "note");
                if (title == null) {
                    title = textOrNull(item, "word");
                }
                if (title == null) {
                    continue;
                }

                String wordScheme = textOrNull(item, "word_scheme");
                String link = buildLink(wordScheme, title);

                HotItem dto = new HotItem();
                dto.setTitle(title.trim());
                dto.setLink(link);
                list.add(dto);
            }

            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String fetchHotSearchJson() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HOT_SEARCH_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .header("Accept", "application/json,text/plain,*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", REFERER_URL)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                byte[] body = response.body();
                if (body != null && body.length > 0) {
                    return new String(body, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
            // 获取失败时返回空，避免影响其他爬虫
        }

        return null;
    }

    private String buildLink(String wordScheme, String fallbackTitle) {
        String keyword = (wordScheme == null || wordScheme.isBlank()) ? fallbackTitle : wordScheme;
        return SEARCH_LINK_PREFIX + URLEncoder.encode(keyword, StandardCharsets.UTF_8);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();
        return (text == null || text.isBlank()) ? null : text;
    }
}
