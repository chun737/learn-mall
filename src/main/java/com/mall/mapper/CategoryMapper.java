package com.mall.mapper;

import com.mall.entity.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 商品分类表（多级） Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

}
