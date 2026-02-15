package com.hioss.spider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hioss.spider.dto.HotItem;
import com.hioss.spider.dto.PathWithDate;
import com.hioss.spider.news.GetBbcNews;
import com.hioss.spider.news.GetBaiduNews;
import com.hioss.spider.news.GetToutiaoNews;
import com.hioss.spider.news.GetWeiboNews;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        // --- 微博 ---
        List<HotItem> weibo = fetchWeibo();

        // --- 创建 JSON ----
        ObjectNode root = MAPPER.createObjectNode();
        root.put("date", dateStr);
        root.set("BBC中文网热点", toArrayNode(bbc));
        root.set("百度热搜", toArrayNode(baidu));
        root.set("今日头条热榜", toArrayNode(toutiao));
        root.set("新浪微博热搜", toArrayNode(weibo));

        Path dataDir = Paths.get("docs", "data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }

        Path todayFile = dataDir.resolve("NewsPage-" + dateStr + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(todayFile.toFile(), root);

        // --- 清理旧文件 ---
        cleanOldFiles(dataDir);

        // --- date.json ---
        generateDateJson(dataDir);
    }

    // ===== BBC 中文网 =====
    private static List<HotItem> fetchBBC() {
        GetBbcNews spider = new GetBbcNews();
        return spider.start();
    }

    // ===== 百度热搜 =====
    private static List<HotItem> fetchBaidu() {
        GetBaiduNews spider = new GetBaiduNews();
        return spider.start();
    }

    // ===== 头条热榜 =====
    private static List<HotItem> fetchToutiao() {
        GetToutiaoNews spider = new GetToutiaoNews();
        return spider.start();
    }

    // ===== 微博热搜 =====
    private static List<HotItem> fetchWeibo() {
        GetWeiboNews spider = new GetWeiboNews();
        return spider.start();
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
    private static void cleanOldFiles(Path dir) throws IOException {
        final List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream
                    .filter(f -> f.getFileName().toString().startsWith("NewsPage-"))
                    .toList();
        }

        List<PathWithDate> list = new ArrayList<>();
        for (Path p : files) {
            try {
                String d = p.getFileName().toString().substring(9, 19);
                list.add(new PathWithDate(p, LocalDate.parse(d)));
            } catch (Exception ignored) {
            }
        }

        list.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        // json文件保存10天
        for (int i = 10; i < list.size(); i++) {
            Files.deleteIfExists(list.get(i).getPath());
        }
    }

    // ===== date.json =====
    private static void generateDateJson(Path dir) throws IOException {
        final List<PathWithDate> list;
        try (var stream = Files.list(dir)) {
            list = stream
                    .filter(f -> f.getFileName().toString().startsWith("NewsPage-"))
                    .map(f -> {
                        try {
                            String d = f.getFileName().toString().substring(9, 19);
                            return new PathWithDate(f, LocalDate.parse(d));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                    .toList();
        }

        ArrayNode arr = MAPPER.createArrayNode();
        for (PathWithDate pw : list) {
            arr.add(pw.getDate().toString());
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.set("dates", arr);

        Path dateJson = dir.resolve("date.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(dateJson.toFile(), root);
    }
}
