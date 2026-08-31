package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Constants;
import com.mall.dto.CategoryDTO;
import com.mall.entity.Category;
import com.mall.entity.Product;
import com.mall.enums.ErrorCode;
import com.mall.mapper.CategoryMapper;
import com.mall.mapper.ProductMapper;
import com.mall.service.ICategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.vo.CategoryVO;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品分类表（多级） 服务实现类
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {
        private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper, ProductMapper productMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    @Override
    @Cacheable(cacheNames = Constants.CACHE_NAME_CATEGORY, key = "'tree'")
    public List<CategoryVO> categoryTree() {
        // 1. 一次查出所有启用且未删除的分类，按 sort 升序
        List<Category> all = lambdaQuery()
                .eq(Category::getStatus, Constants.PRODUCT_STATUS_ON_SHELF)
                .eq(Category::getDeleted, Constants.NOT_DELETED)
                .orderByAsc(Category::getSort)
                .list();

        // 2. 按 parentId 分组（排除顶级分类），建立父子映射
        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(Category::getParentId));

        // 3. 从顶级分类（parentId=0）开始递归组装树
        return all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .map(root -> buildNode(root, childrenMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = Constants.CACHE_NAME_CATEGORY, key = "'tree'")
    public void addCategory(CategoryDTO categoryDTO) {
        String name = categoryDTO.getName();
        // 1. 参数非空校验
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        name = name.trim();

        // 2. 重名校验：仅查未删除的同名分类，用 selectCount 避免多条结果异常
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, name)
                        .eq(Category::getDeleted, Constants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXIST);
        }

        // 3. 根据父分类自动推导层级（不信任前端传入的 level）
        Long parentId = categoryDTO.getParentId();
        int level;
        if (parentId == null || parentId == 0) {
            level = 1; // 顶级分类
        } else {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null || Constants.DELETED == parent.getDeleted()) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            level = parent.getLevel() + 1;
        }
        if (level < 1 || level > 3) {
            throw new BusinessException(ErrorCode.CATEGORY_LEVEL_ERROR);
        }

        // 4. 组装实体落库
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setName(name);
        category.setParentId(parentId == null ? 0L : parentId);
        category.setLevel(level);
        category.setSort(categoryDTO.getSort() == null ? 0 : categoryDTO.getSort());
        category.setStatus(categoryDTO.getStatus() == null ? 1 : categoryDTO.getStatus());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        category.setDeleted(Constants.NOT_DELETED);
        categoryMapper.insert(category);
    }

    @Override
    public List<CategoryVO> listAllCategoryTree() {
        // 1. 一次查出所有启用且未删除的分类，按 sort 升序
        List<Category> all = lambdaQuery()
                .eq(Category::getStatus, Constants.PRODUCT_STATUS_ON_SHELF)
                .eq(Category::getDeleted, Constants.NOT_DELETED)
                .orderByAsc(Category::getSort)
                .list();

        // 2. 按 parentId 分组（排除顶级分类），建立父子映射
        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(Category::getParentId));

        // 3. 从顶级分类（parentId=0）开始递归组装树
        return all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == 0)
                .map(root -> buildNode(root, childrenMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = Constants.CACHE_NAME_CATEGORY, key = "'tree'")
    public void modifyCategory(Long id, CategoryDTO categoryDTO) {
        // 1. 校验目标分类存在且未删除
        Category category = categoryMapper.selectById(id);
        if (category == null || Constants.DELETED == category.getDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        String name = categoryDTO.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        name = name.trim();

        // 2. 重名校验：排除自身（同名的其他未删除分类才冲突）
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, name)
                        .eq(Category::getDeleted, Constants.NOT_DELETED)
                        .ne(Category::getId, id));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXIST);
        }

        // 3. 处理父分类变更：重新推导层级 + 校验合法性
        Long parentId = categoryDTO.getParentId();
        if (parentId != null) {
            // 3.1 不能把分类挂到自己或自己的子孙分类下（防循环引用）
            if (parentId.equals(id) || isDescendant(parentId, id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            // 3.2 校验父分类存在
            if (parentId != 0) {
                Category parent = categoryMapper.selectById(parentId);
                if (parent == null || Constants.DELETED == parent.getDeleted()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                int level = parent.getLevel() + 1;
                if (level < 1 || level > 3) {
                    throw new BusinessException(ErrorCode.CATEGORY_LEVEL_ERROR);
                }
                category.setLevel(level);
            } else {
                category.setLevel(1); // 移到顶级
            }
            category.setParentId(parentId);
        }

        // 4. 更新允许修改的字段
        category.setName(name);
        if (categoryDTO.getSort() != null) {
            category.setSort(categoryDTO.getSort());
        }
        if (categoryDTO.getIcon() != null) {
            category.setIcon(categoryDTO.getIcon());
        }
        if (categoryDTO.getStatus() != null) {
            category.setStatus(categoryDTO.getStatus());
        }
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = Constants.CACHE_NAME_CATEGORY, key = "'tree'")
    public void deleteCategory(Long id) {
        // 1. 校验分类存在且未删除
        Category category = categoryMapper.selectById(id);
        if (category == null || Constants.DELETED == category.getDeleted()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 2. 校验是否存在未删除的子分类
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, id)
                        .eq(Category::getDeleted, Constants.NOT_DELETED));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_DELETE_FORBIDDEN);
        }

        // 3. 校验是否存在未删除的关联商品
        Long productCount = productMapper.selectCount(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getCategoryId, id)
                        .eq(Product::getDeleted, Constants.NOT_DELETED));
        if (productCount != null && productCount > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_DELETE_FORBIDDEN);
        }

        // 4. 逻辑删除
        category.setDeleted(Constants.DELETED);
        category.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(category);
    }

    /**
     * 判断 candidateId 是否为 rootId 的子孙分类（用于防止循环引用）
     */
    private boolean isDescendant(Long candidateId, Long rootId) {
        Long current = candidateId;
        int guard = 0;
        while (current != null && current != 0) {
            if (current.equals(rootId)) {
                return true;
            }
            Category parent = categoryMapper.selectById(current);
            if (parent == null) {
                break;
            }
            current = parent.getParentId();
            // 防御性限制循环次数，避免数据异常导致死循环
            if (++guard > 10) {
                break;
            }
        }
        return false;
    }

    /**
     * 递归构建分类树节点
     */
    private CategoryVO buildNode(Category category, Map<Long, List<Category>> childrenMap) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);

        List<Category> children = childrenMap.get(category.getId());
        if (children != null && !children.isEmpty()) {
            vo.setChildren(children.stream()
                    .map(c -> buildNode(c, childrenMap))
                    .collect(Collectors.toList()));
        }
        return vo;
    }
}
