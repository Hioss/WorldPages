package com.hioss.spider.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hioss.spider.dto.HotItem;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 通过网上提供的API获取新闻JSON数据
 *
 * 说明：不同热榜API的 JSON 字段经常不一致，本工具做了“尽量兼容”的解析：
 * - 自动在 JSON 树里寻找最像“热榜列表”的数组节点（data/list/result/items 等）
 * - 标题字段兼容：title/name/hotword/word/keyword
 * - 链接字段兼容：url/link/href
 *
 * 返回值 List<HotItem> 格式保持不变。
 */
public class NewsJsonUtils {

    // 爬虫结果（保持原有成员变量，不改变返回 List 的类型/格式）
    private final List<HotItem> list = new ArrayList<>();

    /**
     * 调用API接口，返回原始JSON字符串
     */
    public static String fetchHotSearch(String api_url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(api_url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * 原始JSON字符串解析，提取标题与链接
     */
    public List<HotItem> getTitleUrl(String api_url) {

        // 重要：避免多次调用时累积旧数据
        this.list.clear();

        try {
            String json = fetchHotSearch(api_url);
            if (json == null || json.isBlank()) {
                return this.list;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode arrayNode = findBestItemsArray(root);
            if (arrayNode != null && arrayNode.isArray()) {
                int limit = Math.min(10, arrayNode.size());
                for (int i = 0; i < limit; i++) {
                    JsonNode item = arrayNode.get(i);
                    if (item == null || !item.isObject()) {
                        continue;
                    }

                    String title = firstText(item,
                            "title", "name", "hotword", "word", "keyword", "hotTitle", "hot_word");
                    String link = firstText(item,
                            "url", "link", "href", "shareUrl", "share_url", "jumpUrl", "jump_url");

                    // 有些接口把链接放在更深层结构里（例如 item.data.url）
                    if ((link == null || link.isBlank())) {
                        JsonNode nested = item.get("data");
                        if (nested != null && nested.isObject()) {
                            link = firstText(nested, "url", "link", "href");
                        }
                    }

                    if (title == null || title.isBlank()) {
                        continue;
                    }
                    if (link == null) {
                        link = "";
                    }

                    HotItem dto = new HotItem();
                    dto.setTitle(title);
                    dto.setLink(link);
                    list.add(dto);
                }
            }

        } catch (SSLHandshakeException e) {
            // 证书域名问题：直接返回空列表，避免影响其他爬虫
            System.err.println("SSLHandshakeException: " + e.getMessage());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            // 兜底：避免解析结构变化导致程序崩溃
            e.printStackTrace();
        }

        return this.list;
    }

    /**
     * 在 JSON 树里寻找“最像热榜 items”的数组节点。
     * 优先顺序：常见 key（data/list/result/items...） -> 全树扫描匹配。
     */
    private static JsonNode findBestItemsArray(JsonNode root) {
        if (root == null) return null;

        // 1) 常见路径快速命中
        JsonNode candidate = pickArray(root, "data");
        if (isGoodArray(candidate)) return candidate;

        // data.list / data.data
        JsonNode data = root.get("data");
        if (data != null) {
            candidate = pickArray(data, "list");
            if (isGoodArray(candidate)) return candidate;
            candidate = pickArray(data, "data");
            if (isGoodArray(candidate)) return candidate;
            candidate = pickArray(data, "items");
            if (isGoodArray(candidate)) return candidate;
            candidate = pickArray(data, "result");
            if (isGoodArray(candidate)) return candidate;
        }

        // 2) 其他常见 key
        String[] keys = {"list", "result", "items", "news", "hot", "hots", "dataList", "data_list"};
        for (String k : keys) {
            candidate = pickArray(root, k);
            if (isGoodArray(candidate)) return candidate;
        }

        // 3) 全树扫描：找一个“对象数组里含 title/name/url/link”等字段”的数组，取最优（元素最多）
        BestArray best = new BestArray();
        scanForBestArray(root, best);
        return best.node;
    }

    private static JsonNode pickArray(JsonNode node, String key) {
        if (node == null || key == null) return null;
        JsonNode v = node.get(key);
        return (v != null && v.isArray()) ? v : null;
    }

    private static boolean isGoodArray(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.size() == 0) return false;
        // 简单判断：前几项里是否像“包含 title/name 等字段的对象”
        int check = Math.min(3, arr.size());
        for (int i = 0; i < check; i++) {
            JsonNode it = arr.get(i);
            if (it != null && it.isObject()) {
                if (hasAny(it, "title", "name", "hotword", "word", "keyword") || hasAny(it, "url", "link", "href")) {
                    return true;
                }
                JsonNode nested = it.get("data");
                if (nested != null && nested.isObject() && (hasAny(nested, "url", "link", "href") || hasAny(nested, "title", "name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAny(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject()) return false;
        for (String k : keys) {
            JsonNode v = obj.get(k);
            if (v != null && !v.isNull() && v.isValueNode() && !v.asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static String firstText(JsonNode obj, String... keys) {
        if (obj == null || !obj.isObject()) return "";
        for (String k : keys) {
            JsonNode v = obj.get(k);
            if (v != null && !v.isNull() && v.isValueNode()) {
                String s = v.asText("");
                if (s != null && !s.isBlank()) return s;
            }
        }
        return "";
    }

    private static class BestArray {
        JsonNode node;
        int score;
    }

    private static void scanForBestArray(JsonNode node, BestArray best) {
        if (node == null) return;

        if (node.isArray()) {
            if (isGoodArray(node)) {
                int s = node.size();
                if (s > best.score) {
                    best.score = s;
                    best.node = node;
                }
            }
            return;
        }

        if (node.isObject()) {
            Iterator<JsonNode> it = node.elements();
            while (it.hasNext()) {
                scanForBestArray(it.next(), best);
            }
        }
    }
}
