package com.hioss.spider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hioss.spider.dto.HotItem;
import com.hioss.spider.dto.PathWithDate;
import com.hioss.spider.news.GetBbcNews;
import com.hioss.spider.news.GetBaiduNews;
import com.hioss.spider.news.GetToutiaoNews;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 全球资讯
 *
 * <p>该类通过新闻爬虫从著名网站抓取热点新闻标题与链接，
 * 并将抓取结果以JSON形式写入docs文件夹。
 *
 * <p>程序被定时任务（GitHub Actions每天上午11点执行一次，
 * 用于自动更新热点新闻数据。
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-18
 * 
 */
public class SpiderMain {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Tokyo"));
        String dateStr = today.toString();

        // --- BBC ---
        List<HotItem> bbc = fetchBBC();

        // --- 百度 ---
        List<HotItem> baidu = fetchBaidu();

        // --- 头条 ---
        List<HotItem> toutiao = fetchToutiao();

        // --- 创建 JSON ----
        ObjectNode root = MAPPER.createObjectNode();
        root.put("date", dateStr);
        root.set("bbc", toArrayNode(bbc));
        root.set("baidu", toArrayNode(baidu));
        root.set("toutiao", toArrayNode(toutiao));

        Path dataDir = Paths.get("docs", "data");
        if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

        Path todayFile = dataDir.resolve("NewsPage-" + dateStr + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(todayFile.toFile(), root);

        // --- 清理旧文件 ---
        cleanOldFiles(dataDir, 10);

        // --- date.json ---
        generateIndexJson(dataDir);
    }

    // ===== BBC 中文网 =====
    private static List<HotItem> fetchBBC() {
        
        // 创建爬虫对象
        GetBbcNews spider = new GetBbcNews();

        // 调用 start() 执行爬虫，并返回结果
        List<HotItem> result = spider.start();
        
        return result;
    }

    // ===== 百度热搜 =====
    private static List<HotItem> fetchBaidu() {
        
        // 创建爬虫对象
        GetBaiduNews spider = new GetBaiduNews();

        // 调用 start() 执行爬虫，并返回结果
        List<HotItem> result = spider.start();
        
        return result;
    }

    // ===== 头条热榜 =====
    private static List<HotItem> fetchToutiao() {
        
        // 创建爬虫对象
        GetToutiaoNews spider = new GetToutiaoNews();

        // 调用 start() 执行爬虫，并返回结果
        List<HotItem> result = spider.start();
        
        return result;
    }

    private static ArrayNode toArrayNode(List<HotItem> list) {
        ArrayNode arr = MAPPER.createArrayNode();
        for (HotItem i : list) {
            ObjectNode o = MAPPER.createObjectNode();
            o.put("title", i.getTitle());
            o.put("link", i.getLink());
            arr.add(o);
        }
        return arr;
    }

    // ===== 清理旧文件 =====
    private static void cleanOldFiles(Path dir, int keepDays) throws IOException {
        List<Path> files = Files.list(dir)
                .filter(f -> f.getFileName().toString().startsWith("NewsPage-"))
                .collect(Collectors.toList());

        List<PathWithDate> list = new ArrayList<>();
        for (Path p : files) {
            try {
                String d = p.getFileName().toString().substring(4, 14);
                list.add(new PathWithDate(p, LocalDate.parse(d)));
            } catch (Exception ignored) {}
        }

        list.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        for (int i = keepDays; i < list.size(); i++) {
            Files.deleteIfExists(list.get(i).getPath());
        }
    }

    // ===== date.json =====
    private static void generateIndexJson(Path dir) throws IOException {
        List<PathWithDate> list = Files.list(dir)
                .filter(f -> f.getFileName().toString().startsWith("NewsPage-"))
                .map(f -> {
                    try {
                        String d = f.getFileName().toString().substring(4, 14);
                        return new PathWithDate(f, LocalDate.parse(d));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull)
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .collect(Collectors.toList());

        ArrayNode arr = MAPPER.createArrayNode();
        for (PathWithDate pw : list) arr.add(pw.getDate().toString());

        ObjectNode root = MAPPER.createObjectNode();
        root.set("dates", arr);

        Path indexJson = dir.resolve("date.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(indexJson.toFile(), root);
    }
}