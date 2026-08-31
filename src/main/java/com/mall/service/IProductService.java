package com.mall.service;

import com.mall.dto.ProductDTO;
import com.mall.dto.SkuDTO;
import com.mall.entity.Product;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 商品表（SPU） 服务类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
public interface IProductService extends IService<Product> {

    /**
     * 商品列表分页查询（PageHelper 分页）
     *
     * @param pageNum    页码，从 1 开始
     * @param pageSize   每页条数
     * @param categoryId 分类 ID（可空）
     * @param keyword    关键词（可空）
     * @param sortBy     排序方式：default/priceAsc/priceDesc/salesDesc/newest
     */
    PageResult<ProductListVO> listProducts(Integer pageNum, Integer pageSize,
                                           Long categoryId, String keyword, String sortBy);

    ProductDetailVO getDPs(Long id);

    List<String> getHot10(String keyword);


    PageResult<ProductListAdminVO> listProdctsAdmin(Integer pageNum, Integer pageSize, Long categoryId, String keyword, Integer status, LocalDateTime startTime, LocalDateTime endTime);

    ProductDetailVO addProduct(ProductDTO productDTO);

    void modifyProduct(Long id, ProductDTO productDTO);

    String modifyStatus(Long id, Integer status);

    void deleteProduct(Long id);

    List<AdminSkuVO> getSKUs(Long id);

    void addSkus(@NotNull Long id, @Valid SkuDTO skuDTO);
}
