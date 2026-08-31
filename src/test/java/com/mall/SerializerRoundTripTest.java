package com.mall;

import com.mall.vo.CategoryVO;
import com.mall.vo.PageResult;
import com.mall.vo.ProductDetailVO;
import com.mall.vo.ProductListVO;
import com.mall.vo.SeckillActivityVO;
import com.mall.vo.SkuVO;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 临时验证脚本：确认 GenericJacksonJsonRedisSerializer（Jackson 3）对本项目三个被缓存的
 * VO 结构能正确往返序列化。验证完成后可删除。
 */
public class SerializerRoundTripTest {

    public static void main(String[] args) {
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.builder()
                // 开启多态类型信息（@class），白名单限定为本项目类 + JDK 集合/基础类型，
                // 防御反序列化 gadget 攻击（比老版本的 LaissezFaire 全放行更安全）
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.mall.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.math.")
                        .build())
                .build();

        // 1. PageResult<ProductListVO> —— 商品列表缓存
        ProductListVO p = new ProductListVO();
        p.setId(1L);
        p.setProductName("测试商品");
        p.setMinPrice(new BigDecimal("9.90"));
        PageResult<ProductListVO> pr = PageResult.of(List.of(p), 1L, 1, 10);
        Object back1 = serializer.deserialize(serializer.serialize(pr));
        PageResult<?> pr2 = (PageResult<?>) back1;
        System.out.println("[列表] 外层=" + back1.getClass().getSimpleName()
                + " 元素=" + pr2.getList().get(0).getClass().getSimpleName()
                + " total=" + pr2.getTotal());

        // 2. ProductDetailVO（含 SKU 列表 + BigDecimal）—— 商品详情缓存
        SkuVO sku = new SkuVO();
        sku.setId(2L);
        sku.setSpecs("红色/L");
        sku.setPrice(new BigDecimal("19.90"));
        ProductDetailVO d = new ProductDetailVO();
        d.setId(1L);
        d.setProductName("测试商品");
        d.setSkus(List.of(sku));
        ProductDetailVO d2 = (ProductDetailVO) serializer.deserialize(serializer.serialize(d));
        System.out.println("[详情] 外层=" + d2.getClass().getSimpleName()
                + " SKU=" + d2.getSkus().get(0).getClass().getSimpleName()
                + " price=" + d2.getSkus().get(0).getPrice()
                + " specs=" + d2.getSkus().get(0).getSpecs());

        // 3. List<CategoryVO> —— 分类树缓存（递归结构）
        // 注意：必须用 ArrayList（与 CategoryServiceImpl 的 Collectors.toList() 一致）。
        // List.of() 返回的不可变集合是 final 类且无私有构造器，Jackson 无法反序列化重建
        CategoryVO child = new CategoryVO();
        child.setId(3L);
        child.setName("子分类");
        CategoryVO root = new CategoryVO();
        root.setId(2L);
        root.setName("父分类");
        root.setChildren(new ArrayList<>(List.of(child)));
        List<CategoryVO> tree = new ArrayList<>(List.of(root));
        List<?> tree2 = (List<?>) serializer.deserialize(serializer.serialize(tree));
        CategoryVO root2 = (CategoryVO) tree2.get(0);
        System.out.println("[分类树] 外层=" + tree2.getClass().getSimpleName()
                + " 元素=" + root2.getClass().getSimpleName()
                + " child=" + root2.getChildren().get(0).getClass().getSimpleName()
                + " name=" + root2.getChildren().get(0).getName());

        // 4. List<SeckillActivityVO> —— 秒杀活动列表缓存（首个含 LocalDateTime 字段的缓存 VO：
        //    白名单无 java.time.，验证字段按声明类型反序列化不经过白名单是否成立）
        SeckillActivityVO act = new SeckillActivityVO();
        act.setId(301L);
        act.setActivityName("iPhone 限时秒杀");
        act.setSeckillPrice(new BigDecimal("6999.00"));
        act.setStartTime(LocalDateTime.of(2026, 8, 27, 20, 0, 0));
        act.setEndTime(LocalDateTime.of(2026, 8, 27, 21, 0, 0));
        List<SeckillActivityVO> acts = new ArrayList<>(List.of(act));
        List<?> acts2 = (List<?>) serializer.deserialize(serializer.serialize(acts));
        SeckillActivityVO act2 = (SeckillActivityVO) acts2.get(0);
        System.out.println("[秒杀列表] 外层=" + acts2.getClass().getSimpleName()
                + " 元素=" + act2.getClass().getSimpleName()
                + " startTime=" + act2.getStartTime()
                + "（类型 " + (act2.getStartTime() == null ? "null" : act2.getStartTime().getClass().getSimpleName()) + "）"
                + " endTime=" + act2.getEndTime()
                + " price=" + act2.getSeckillPrice());
    }
}
