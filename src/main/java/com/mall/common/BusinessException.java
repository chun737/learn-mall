package com.mall.common;

import com.mall.enums.ErrorCode;

public class BusinessException extends RuntimeException{
    private final int code;   // 业务错误码

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
