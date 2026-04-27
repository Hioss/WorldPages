package com.hioss.spider.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hioss.spider.dto.HotItem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 头条热榜
 * <p>直接从今日头条官网域名下的热榜地址抓取数据：
 * <a href="https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc">官方热榜接口</a>
 * @author      程春海
 * @version     1.0
 * @since       2025-11-19
 */
public class GetToutiaoNews {

    private static final int LIMIT = 10;
    private static final String HOT_BOARD_URL = "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc";
    private static final String REFERER_URL = "https://www.toutiao.com/";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 提供给外部调用的方法
     */
    public List<HotItem> start() {
        String json = fetchHotBoardJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return List.of();
            }

            List<HotItem> list = new ArrayList<>(LIMIT);
            int count = Math.min(LIMIT, data.size());

            for (int i = 0; i < count; i++) {
                JsonNode item = data.get(i);
                if (item == null || !item.isObject()) {
                    continue;
                }

                String title = textOrNull(item, "Title");
                if (title == null) {
                    title = textOrNull(item, "QueryWord");
                }

                String link = textOrNull(item, "Url");
                if (title == null || link == null) {
                    continue;
                }

                HotItem dto = new HotItem();
                dto.setTitle(title.trim());
                dto.setLink(link.trim());
                list.add(dto);
            }

            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String fetchHotBoardJson() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HOT_BOARD_URL))
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

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }

        String text = value.asText();
        return (text == null || text.isBlank()) ? null : text;
    }
}
