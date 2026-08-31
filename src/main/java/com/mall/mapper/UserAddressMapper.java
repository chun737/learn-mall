package com.mall.mapper;

import com.mall.dto.AddressDTO;
import com.mall.entity.UserAddress;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;

/**
 * <p>
 * 用户收货地址表 Mapper 接口
 * </p>
 *
 * @author 乐乐
 * @since 2026-08-16
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {

    ArrayList<UserAddress> listAddresses(@Param("userId") Long userId);


    AddressDTO getUserById(@Param("id") Long id);
}
