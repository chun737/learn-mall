package com.mall.vo;

import com.github.pagehelper.PageInfo;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果
 *
 * @param <T> 列表元素类型
 * @author 乐乐
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> list;

    /** 总记录数 */
    private Long total;

    /** 当前页码，从 1 开始 */
    private Integer pageNum;

    /** 每页条数 */
    private Integer pageSize;

    /** 总页数 */
    private Integer totalPages;

    /** 无参构造（JSON 反序列化需要） */
    public PageResult() {
    }

    /** 全参构造：自动计算 totalPages */
    public PageResult(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = calcTotalPages(total, pageSize);
    }

    /**
     * 从 PageHelper 的 PageInfo 转换（本项目分页统一入口）
     *
     * @param pageInfo PageHelper 分页信息对象
     * @param list     当前页数据
     */
    public static <T> PageResult<T> of(PageInfo<?> pageInfo, List<T> list) {
        PageResult<T> result = new PageResult<>();
        // 防御性拷贝：list 实际是 PageHelper 的 Page（非 java.util 类），
        // 直接持有会导致缓存序列化带上 Page 类型，反序列化时被类型白名单拒绝
        result.setList(new ArrayList<>(list));
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());
        result.setTotalPages(pageInfo.getPages());
        return result;
    }

    /** 手动构建分页结果 */
    public static <T> PageResult<T> of(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        return new PageResult<>(list, total, pageNum, pageSize);
    }

    /** 空结果（查询无数据时返回） */
    public static <T> PageResult<T> empty(Integer pageNum, Integer pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize);
    }

    /** 根据总记录数和每页条数计算总页数（向上取整） */
    private static Integer calcTotalPages(Long total, Integer pageSize) {
        if (total == null || pageSize == null || pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / pageSize);
    }
}
