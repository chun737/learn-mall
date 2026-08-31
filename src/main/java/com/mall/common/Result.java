package com.mall.common;

import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 业务数据类型
 *
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    public static final int SUCCESS_CODE = 200;

    /** 失败状态码 */
    public static final int ERROR_CODE = 500;

    /** 状态码：200 成功，其它为失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, "操作成功", null);
    }

    /**
     * 成功（携带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data);
    }

    /**
     * 成功（自定义提示，无数据）
     */
    public static <T> Result<T> success(String message) {
        return new Result<>(SUCCESS_CODE, message, null);
    }

    /**
     * 成功（自定义提示 + 数据）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data);
    }

    /**
     * 失败（默认状态码 500）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ERROR_CODE, message, null);
    }

    /**
     * 失败（自定义状态码 + 提示）
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
