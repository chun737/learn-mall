package com.mall.util;

import com.mall.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static com.mall.enums.ErrorCode.UNAUTHORIZED;
 @Component
public class SecurityUtils {
    public static Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED);
        }
        return (Long) auth.getPrincipal();  // 你项目里 principal 就是 Long
    }
}
