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
import java.util.Locale;

/**
 * 懂球帝：抓取「热门推荐/热门」前 10 条（接口版，更稳定）
 * 说明：
 * - 懂球帝网页端首页多为前端动态渲染，直接抓 HTML 常常拿不到“热门推荐”列表
 * - 改为请求懂球帝 App 的 tab 接口（热门：104），解析 JSON 获取文章标题与 share 链接
 * 返回值：List<HotItem>（不改变格式）
 */
public class GetDongqiudiHotNews {

    private static final int LIMIT = 10;

    // 备用多个 URL：有的环境/version 参数可能要求不同，逐个尝试
    private static final String[] CANDIDATE_URLS = new String[]{
            "https://api.dongqiudi.com/app/tabs/iphone/104.json?mark=gif&version=500",
            "https://api.dongqiudi.com/app/tabs/iphone/104.json",
            "https://api.dongqiudi.com/app/tabs/iphone/104.json?version=500"
    };

    private final ObjectMapper mapper = new ObjectMapper();

    public List<HotItem> start() {
        String json = fetchJson();
        if (json == null || json.isBlank()) return List.of();

        try {
            JsonNode root = mapper.readTree(json);

            // 数据结构（常见）：root.contents[0].articles[*].title / share
            JsonNode contents = root.path("contents");
            if (!contents.isArray() || contents.isEmpty()) return List.of();

            List<HotItem> out = new ArrayList<>(LIMIT);

            // 有时 articles 在多天分组里：contents[i].articles
            for (int i = 0; i < contents.size() && out.size() < LIMIT; i++) {
                JsonNode articles = contents.get(i).path("articles");
                if (!articles.isArray()) continue;

                for (JsonNode a : articles) {
                    if (out.size() >= LIMIT) break;

                    String title = textOrNull(a, "title");
                    String share = textOrNull(a, "share");

                    // 有些字段可能叫 url / url1（但 share 通常就是可打开的文章链接）
                    if (share == null || share.isBlank()) {
                        share = textOrNull(a, "url");
                    }
                    if (share == null || share.isBlank()) {
                        share = textOrNull(a, "url1");
                    }

                    if (title == null || title.isBlank()) continue;
                    if (share == null || share.isBlank()) continue;

                    // 只保留懂球帝文章链接
                    String lower = share.toLowerCase(Locale.ROOT);
                    if (!lower.contains("dongqiudi.com")) continue;

                    HotItem item = new HotItem();
                    item.setTitle(title.trim());
                    item.setLink(share.trim());
                    out.add(item);
                }
            }

            return out;
        } catch (Exception e) {
            // 解析失败就返回空，避免影响主流程
            return List.of();
        }
    }

    private String fetchJson() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        for (String url : CANDIDATE_URLS) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        // 模拟常见客户端请求头
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                        .header("Accept", "application/json,text/plain,*/*")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Referer", "https://www.dongqiudi.com/")
                        .GET()
                        .build();

                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    byte[] body = resp.body();
                    if (body != null && body.length > 0) {
                        return new String(body, StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception ignored) {
                // 换下一个候选 URL
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s;
    }
}
