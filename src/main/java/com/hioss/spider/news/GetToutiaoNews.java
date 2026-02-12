package com.hioss.spider.news;

import java.util.List;

import com.hioss.spider.common.NewsJsonUtils;
import com.hioss.spider.dto.HotItem;

/**
 * 头条热榜
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-19
 *
 */
public class GetToutiaoNews {

    /**
     * 提供给外部调用的方法
     */
    public List<HotItem> start() {
        // 头条热榜API接口（已更换）
        String api_url = "https://www.cunyuapi.top/jrtthot";

        NewsJsonUtils newsJsonUtils = new NewsJsonUtils();
        // 返回值 List<HotItem> 格式保持不变
        return newsJsonUtils.getTitleUrl(api_url);
    }
}
