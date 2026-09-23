package com.mall.common;

/**
 * ES 不可用异常（连接失败 / 查询失败 / 处于熔断窗口）。
 *
 * 为什么不直接抛 RuntimeException，而要单独定义一个类型：
 *   ES 在本项目里只是"搜索副本"——MySQL 才是主存储，它挂掉绝不能影响商品主流程。
 *   查询路径抛出本异常，是为了让上层（ProductServiceImpl.listProducts）能明确识别
 *   "这是 ES 的问题"，从而降级走 MySQL 把数据搜出来；
 *   如果直接抛 RuntimeException，上层就得靠"捕获所有异常"来兜底，语义是模糊的。
 *
 * 注意：写入路径（syncProductById / deleteByProductId）不抛本异常，只记日志静默跳过——
 * 副本同步失败可以靠全量重灌兜底，不值得打断商品增删改。
 *
 * @author 乐乐
 */
public class EsUnavailableException extends RuntimeException {

    public EsUnavailableException(String message) {
        super(message);
    }

    public EsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
