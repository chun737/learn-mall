package com.mall.common;

import com.github.pagehelper.PageHelper;

/**
 * 分页启动统一入口：页码/页大小规整（页码从 1 起，页大小钳制到 MAX_PAGE_SIZE）。
 * 所有 PageHelper.startPage 调用必须走这里，防止前端传超大 pageSize 拖垮查询（DoS 面）。
 *
 * @author 乐乐
 */
public final class PageUtils {

    private PageUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 规整后开启分页：pageNum 非法回退 1，pageSize 非法回退 10，上限 Constants.MAX_PAGE_SIZE
     */
    public static void startPage(Integer pageNum, Integer pageSize) {
        int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, Constants.MAX_PAGE_SIZE);
        PageHelper.startPage(page, size);
    }
}
