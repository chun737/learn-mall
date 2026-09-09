package com.mall.mapper;

import com.mall.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.ProductDetailVO;
import com.mall.vo.ProductListAdminVO;
import com.mall.vo.ProductListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.parameters.P;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 商品表（SPU） Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 商品列表分页查询（配合 PageHelper 使用，SQL 本身不写 LIMIT）
     *
     * @param categoryId 分类 ID（可空）
     * @param keyword    关键词，模糊匹配商品名/副标题（可空）
     * @param sortBy     排序方式：default/priceAsc/priceDesc/salesDesc/newest
     */
    List<ProductListVO> selectProductList(@Param("categoryId") Long categoryId,
                                          @Param("keyword") String keyword,
                                          @Param("sortBy") String sortBy);

    ProductDetailVO getDPs(@Param("id")Long id);

    List<String> getHot10(@Param("keyword")String keyword);

    /**
     * 后台商品列表分页查询（配合 PageHelper 使用，SQL 本身不写 LIMIT）
     *
     * @param categoryId 分类 ID（可空）
     * @param keyword    关键词（可空）
     * @param status     商品状态（可空）
     * @param startTime  创建时间起始（可空）
     * @param endTime    创建时间截止（可空）
     */
    List<ProductListAdminVO> ListProduct(@Param("categoryId") Long categoryId,
                                         @Param("keyword") String keyword,
                                         @Param("status") Integer status,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    void deleteProduct(@Param("id") Long id);

    /**
     * 布隆过滤器预热数据源：全部未删除商品 ID（ProductBloomIndex.loadAllIds 调用）
     */
    List<Long> selectAllIds();

    /**
     * 逻辑删除商品下的所有 SKU（与 deleteProduct 在同一事务中执行）
     */
    void deleteProductSkus(@Param("id") Long id);
    /** ES 全量同步：分页取上架商品（含价格区间、销量） */
    List<ProductListVO> selectAllForSearch(@Param("offset") int offset,
                                           @Param("limit") int limit);

    /** ES 同步单条 */
    List<ProductListVO> selectAllForSearchById(@Param("productId") Long productId);
}
