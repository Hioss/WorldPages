package com.hioss.spider.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hioss.spider.dto.HotItem;

/**
 * 通过网上提供的API获取新闻JSON数据
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-19
 * 
 */
public class NewsJsonUtils {

    //爬虫结果
    private final List<HotItem> list = new ArrayList<>();

    /**
     * 调用API接口，返回原始JSON字符串
     */
    public static String fetchHotSearch(String api_url) throws IOException, InterruptedException {
        // 创建 HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 创建请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(api_url))
                .GET()
                .build();

        // 发送请求并获取响应
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 返回响应内容
        return response.body();
    }

    /**
     * 原始JSON字符串解析，提取标题与链接
     */
    public List<HotItem> getTitleUrl(String api_url)  {
        
        try {
            // 获取接口返回的JSON字符串
            String json = fetchHotSearch(api_url);

            // 使用Jackson解析
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode dataArray = root.get("data");

            if (dataArray != null && dataArray.isArray()) {
                int limit = Math.min(10, dataArray.size());
                for (int i = 0; i < limit; i++) {
                    JsonNode item = dataArray.get(i);
                    String title = item.get("title").asText();
                    String link = item.get("url").asText();
                    
                    HotItem dto = new HotItem();
                    dto.setTitle(title);
                    dto.setLink(link);
                        
                    list.add(dto);
                }
            } 
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return this.list;
    }
}
