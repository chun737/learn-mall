package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Constants;
import com.mall.entity.Cart;
import com.mall.entity.Product;
import com.mall.entity.ProductSku;
import com.mall.mapper.CartMapper;
import com.mall.mapper.ProductMapper;
import com.mall.mapper.ProductSkuMapper;
import com.mall.service.ICartService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.util.SecurityUtils;
import com.mall.vo.CartItemVO;
import com.mall.vo.CartVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mall.enums.ErrorCode.NOT_FOUND;
import static com.mall.enums.ErrorCode.PARAM_ERROR;
import static com.mall.enums.ErrorCode.PRODUCT_OFF_SHELF;
import static com.mall.enums.ErrorCode.STOCK_NOT_ENOUGH;

/**
 * <p>
 * 购物车表 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements ICartService {
    private final CartMapper cartMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    public CartServiceImpl(CartMapper cartMapper, ProductSkuMapper productSkuMapper, ProductMapper productMapper) {
        this.cartMapper = cartMapper;
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
    }

    @Override
    public CartVO listCarts() {
        Long userId = SecurityUtils.getUserId();
        // 查询购物车明细项（已连表带出商品/SKU信息）
        List<CartItemVO> items = cartMapper.listCarts(userId);
        if (items == null) {
            items = new ArrayList<>();
        }

        // 聚合统计：全部数量、勾选数量、勾选金额
        int totalQuantity = 0;
        int checkedQuantity = 0;
        BigDecimal checkedAmount = BigDecimal.ZERO;
        for (CartItemVO item : items) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            totalQuantity += qty;
            if (item.getChecked() != null && item.getChecked() == 1) {
                checkedQuantity += qty;
                if (item.getPrice() != null) {
                    checkedAmount = checkedAmount.add(item.getPrice().multiply(BigDecimal.valueOf(qty)));
                }
            }
        }

        CartVO cartVO = new CartVO();
        cartVO.setItems(items);
        cartVO.setTotalQuantity(totalQuantity);
        cartVO.setCheckedQuantity(checkedQuantity);
        cartVO.setCheckedAmount(checkedAmount);
        return cartVO;
    }

    @Override
    @Transactional
    public void addCart(Integer skuId, Integer quantity) {
        // 数量必须为正：负数/0 会穿透库存 CAS（stock >= qty 对负数恒成立，导致反向加库存）
        if (quantity == null || quantity < 1) {
            throw new BusinessException(PARAM_ERROR);
        }
        ProductSku productSku = productSkuMapper.selectById(skuId);
        Long userId = SecurityUtils.getUserId();
        if(productSku == null){
            throw new BusinessException(PRODUCT_OFF_SHELF);
        }
        if(productSku.getStatus() == Constants.PRODUCT_STATUS_OFF_SHELF || productSku.getDeleted() == Constants.DELETED){
            throw new BusinessException(PRODUCT_OFF_SHELF);
        }
        // 校验父商品未删除（防止已删商品的 SKU 进入购物车，结账时 NPE）
        Product product = productMapper.selectById(productSku.getProductId());
        if (product == null || product.getDeleted() == Constants.DELETED) {
            throw new BusinessException(PRODUCT_OFF_SHELF);
        }
        if(productSku.getStock() < 1 ){
            throw new BusinessException(STOCK_NOT_ENOUGH);
        }
        cartMapper.addCart(skuId,quantity,productSku.getProductId(),userId);
    }

    @Override
    @Transactional
    public void modifyQ(Integer quantity, Integer id) {
        Long userId = SecurityUtils.getUserId();
        // 数量必须为正，防止负数穿透库存校验
        if (quantity == null || quantity < 1) {
            throw new BusinessException(PARAM_ERROR);
        }

        // 越权校验：购物车项必须存在且属于当前用户
        Cart cart = cartMapper.selectById(id);
        if (cart == null || !cart.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 校验库存
        ProductSku productSku = productSkuMapper.selectById(cart.getSkuId());
        if (productSku == null || productSku.getStock() < quantity) {
            throw new BusinessException(STOCK_NOT_ENOUGH);
        }

        // 更新数量（带 user_id 双重条件）
        QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>()
                .eq("id", id)
                .eq("user_id", userId);
        cart.setQuantity(quantity);
        cartMapper.update(cart, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modifyC(Integer id, Integer checked) {
        ifchecked(checked);
        Long userId = SecurityUtils.getUserId();

        // 越权校验：购物车项必须存在且属于当前用户
        Cart cart = cartMapper.selectById(id);
        if (cart == null || !cart.getUserId().equals(userId)) {
            throw new BusinessException(NOT_FOUND);
        }

        // 更新勾选状态（带 user_id 双重条件）
        QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>()
                .eq("id", id)
                .eq("user_id", userId);
        cart.setChecked(checked);
        cartMapper.update(cart, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modifyAll(Integer checked) {
        ifchecked(checked);
        Long userId = SecurityUtils.getUserId();
       cartMapper.updateByUserId(userId,checked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteC(Integer checked) {
        ifchecked(checked);
        Long userId = SecurityUtils.getUserId();
        if(checked == 0){
            QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>().eq("user_id",userId);
            cartMapper.delete(wrapper);
        }
        else {
            QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>()
                    .eq("user_id",userId)
                    .eq("checked",1);
            cartMapper.delete(wrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCartById(Integer id) {
        if (id == null || id<1){
            throw new BusinessException(PARAM_ERROR);
        }
        Long userId = SecurityUtils.getUserId();
        QueryWrapper<Cart> wrapper = new QueryWrapper<Cart>()
                .eq("id",id)
                .eq("user_id",userId);
        cartMapper.delete(wrapper);
    }

    private void ifchecked(Integer checked){
        if(checked == null || checked >1 || checked < 0){
            throw  new BusinessException(PARAM_ERROR);
        }
    }
}
