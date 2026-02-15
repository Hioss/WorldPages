package com.hioss.spider.news;

import com.hioss.spider.dto.HotItem;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IT之家（ithome.com）日榜
 * 说明：参照 GetBbcNews.java 的写法，用 WebMagic 抓取首页的“日榜”列表前 N 条。
 *
 * @author      程春海
 * @version     1.0
 * @since       2026-02-15
 */
public class GetIthomeDailyNews implements PageProcessor {

    // 目标：取前 12 条（与你截图一致）
    private static final int LIMIT = 10;

    // IT之家首页
    private static final String HOME_URL = "https://www.ithome.com/";

    // 结果（⚠️ List 格式不变：仍然返回 List<HotItem>）
    private final List<HotItem> list = new ArrayList<>();

    // 用于去重（避免抓到重复链接/标题）
    private final Set<String> seen = new HashSet<>();

    // 模拟浏览器的反爬虫设置
    private final Site site = Site.me()
            .setCharset("UTF-8")
            .setRetryTimes(2)
            .setSleepTime(1500)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .addHeader("Referer", "https://www.ithome.com/");

    /**
     * 解析首页 HTML：定位“日榜”区域后，抓取其后的链接标题
     */
    @Override
    public void process(Page page) {
        String html = page.getRawText();
        if (html == null || html.trim().isEmpty()) return;

        // 1) 找到“日榜”出现的位置（首页会同时出现“日榜/周榜/月榜”）
        int start = indexOfIgnoreCase(html, "日榜");
        if (start < 0) return;

        // 2) 从“日榜”附近开始，用正则扫 <a href="...">title</a>
        //    （不依赖具体 DOM class，适配页面结构轻微变动）
        String sub = html.substring(start);

        Pattern aTag = Pattern.compile(
                "<a\\s+[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher m = aTag.matcher(sub);
        while (m.find() && list.size() < LIMIT) {
            String href = m.group(1);
            String titleHtml = m.group(2);

            String title = cleanText(titleHtml);
            String link = normalizeUrl(href);

            if (!isValidItem(title, link)) continue;

            String key = title + "||" + link;
            if (seen.contains(key)) continue;
            seen.add(key);

            HotItem dto = new HotItem();
            dto.setTitle(title);
            dto.setLink(link);
            list.add(dto);
        }
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
                .addUrl(HOME_URL)
                .thread(1)
                .run();

        return this.list;
    }

    // ----------------- 工具方法 -----------------

    private boolean isValidItem(String title, String link) {
        if (title == null || title.isEmpty()) return false;
        if (link == null || link.isEmpty()) return false;

        // 过滤“日榜/周榜/月榜”等 tab 文案
        if ("日榜".equals(title) || "周榜".equals(title) || "月榜".equals(title)) return false;

        // 过滤无效链接
        String l = link.toLowerCase();
        if (l.startsWith("javascript:")) return false;
        if (l.startsWith("#")) return false;

        // 只保留 ithome 的新闻链接（避免抓到广告/跳转）
        // 如果你希望保留站外链接，可以删除这一段。
        if (!(l.contains("ithome.com"))) return false;

        return true;
    }

    private String normalizeUrl(String href) {
        if (href == null) return null;
        href = href.trim();
        if (href.isEmpty()) return href;

        // //www.ithome.com/xxx
        if (href.startsWith("//")) return "https:" + href;

        // /xxx
        if (href.startsWith("/")) return "https://www.ithome.com" + href;

        // 已经是绝对地址
        return href;
    }

    private String cleanText(String htmlFragment) {
        if (htmlFragment == null) return null;

        // 去掉标签
        String s = htmlFragment.replaceAll("(?is)<script.*?>.*?</script>", "");
        s = s.replaceAll("(?is)<style.*?>.*?</style>", "");
        s = s.replaceAll("(?is)<[^>]+>", "");

        // HTML 实体解码（基础 + 数字实体）
        s = unescapeHtml(s);

        // 压缩空白
        s = s.replace('\u00A0', ' '); // &nbsp;
        s = s.replaceAll("[ \\t\\r\\n]+", " ").trim();

        return s;
    }

    private int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) return -1;
        String t = text.toLowerCase();
        String n = needle.toLowerCase();
        return t.indexOf(n);
    }

    private String unescapeHtml(String s) {
        if (s == null) return null;

        // 常见实体
        s = s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ");

        // 数字实体：&#12345;
        Pattern dec = Pattern.compile("&#(\\d+);");
        Matcher mDec = dec.matcher(s);
        StringBuffer sbDec = new StringBuffer();
        while (mDec.find()) {
            try {
                int code = Integer.parseInt(mDec.group(1));
                mDec.appendReplacement(sbDec, Matcher.quoteReplacement(new String(Character.toChars(code))));
            } catch (Exception e) {
                mDec.appendReplacement(sbDec, Matcher.quoteReplacement(mDec.group(0)));
            }
        }
        mDec.appendTail(sbDec);
        s = sbDec.toString();

        // 十六进制实体：&#x1F600;
        Pattern hex = Pattern.compile("&#x([0-9a-fA-F]+);");
        Matcher mHex = hex.matcher(s);
        StringBuffer sbHex = new StringBuffer();
        while (mHex.find()) {
            try {
                int code = Integer.parseInt(mHex.group(1), 16);
                mHex.appendReplacement(sbHex, Matcher.quoteReplacement(new String(Character.toChars(code))));
            } catch (Exception e) {
                mHex.appendReplacement(sbHex, Matcher.quoteReplacement(mHex.group(0)));
            }
        }
        mHex.appendTail(sbHex);

        // 避免奇怪编码问题（有些页面会混入不可见字符）
        return new String(sbHex.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).trim();
    }
}
