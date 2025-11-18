package com.hioss.spider.news;

import java.util.List;

import com.hioss.spider.common.NewsJsonUtils;
import com.hioss.spider.dto.HotItem;

/**
 * 百度热搜
 *
 * @author      程春海
 * @version     1.0
 * @since       2025-11-19
 * 
 */
public class GetBaiduNews {
    
    /**
     * 提供给外部调用的方法
     */
    public List<HotItem> start() {
        // 百度热搜API接口
        String api_url = "https://zj.v.api.aa1.cn/api/baidu-rs/";

        NewsJsonUtils newsJsonUtils = new NewsJsonUtils();
        return newsJsonUtils.getTitleUrl(api_url);
    }
}