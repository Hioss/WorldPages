package com.hioss.spider.news;

import com.hioss.spider.dto.HotItem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GetBaiduNewsTest {
    @Test
    public void pairsTitlesWithTheirOwnLinksDespiteChangedClassesAndMissingLinks() {
        String html = """
                <div class="category-wrap_YGL2I horizontal_jSSMn">
                  <a href="https://www.baidu.com/s?wd=image">图片</a>
                  <a class="title_2BcjE" href="https://www.baidu.com/s?wd=first&amp;sa=fyb_news">
                    <div class="c-single-text-ellipsis">  第一条 <!--37--></div>
                    <div class="c-text hot-tag_ZUKS0">热</div>
                  </a>
                </div>
                <a class="title_2BcjE"><div class="c-single-text-ellipsis">缺少链接</div></a>
                <a class="title_2BcjE" href=""><div class="c-single-text-ellipsis">空链接</div></a>
                <a class="title_other" href="//www.baidu.com/s?wd=second">
                  <div class="c-single-text-ellipsis">第二条</div>
                </a>
                """;

        List<HotItem> items = GetBaiduNews.parseHotItems(html);

        assertEquals(2, items.size());
        assertEquals("第一条", items.get(0).getTitle());
        assertEquals("https://www.baidu.com/s?wd=first&sa=fyb_news", items.get(0).getLink());
        assertEquals("第二条", items.get(1).getTitle());
        assertEquals("https://www.baidu.com/s?wd=second", items.get(1).getLink());
    }

    @Test
    public void keepsTheFirstTenInPageOrder() {
        StringBuilder html = new StringBuilder();
        for (int rank = 1; rank <= 12; rank++) {
            html.append("""
                    <a href="https://www.baidu.com/s?wd=%d">
                      <div class="c-single-text-ellipsis">热搜%d</div>
                    </a>
                    """.formatted(rank, rank));
        }

        List<HotItem> items = GetBaiduNews.parseHotItems(html.toString());

        assertEquals(10, items.size());
        assertEquals("热搜1", items.get(0).getTitle());
        assertEquals("热搜10", items.get(9).getTitle());
    }
}
