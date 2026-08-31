package com.mall.mapper;

import com.mall.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.vo.UserProfileVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户账号表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
