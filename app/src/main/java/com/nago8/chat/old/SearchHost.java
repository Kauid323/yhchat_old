package com.nago8.chat.old;

/**
 * 搜索回调接口，由会话 Fragment 实现。
 * 顶栏搜索框输入后，通过此接口让 Fragment 执行搜索。
 */
public interface SearchHost {
    /** 顶栏搜索框提交搜索词时回调 */
    void onSearch(String word);

    /** 顶栏搜索框关闭时回调，Fragment 应退出搜索模式并重新加载列表 */
    void onSearchClosed();
}
