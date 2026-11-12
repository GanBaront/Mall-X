package com.mall.mgr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物订单实体（用户侧交易履约单，承载订单状态机）。
 * 记录用户在移动端下单后的订单信息与履约状态流转。
 * 对应数据库表：biz_order。所属模块：mall-mgr（平台管理服务）。
 * 订单状态（state）：0=待支付, 1=待发货, 2=待收货, 3=已完成, 4=已关闭。
 */
@Data
@Entity
@Table(name = "biz_order")
public class BizOrder {
    /** 订单编号（主键） */
    @Id
    private String orderNum;

    /** 关联的支付订单ID */
    private String payOrderId;

    /** 买家用户编号 */
    private String userNum;

    /** 商户/店铺编号 */
    private String shopNum;

    /** 商品ID */
    private Long productId;

    /** 商品名称 */
    private String goodsName;

    /** 商品图片 */
    private String image;

    /** 购买数量 */
    private Integer num;

    /** 商品单价 */
    private BigDecimal price;

    /** 订单总金额 */
    private BigDecimal total;

    /** 订单状态：0=待支付, 1=待发货, 2=待收货, 3=已完成, 4=已关闭 */
    private Integer state;

    /** 支付方式编码 */
    private String wayCode;

    /** 收货人姓名 */
    private String buyer;

    /** 收货地址 */
    private String address;

    /** 物流单号 */
    private String logisticsNo;

    /** 付款时间（支付回调成功时写入，未支付为空） */
    private LocalDateTime payTime;

    /** 发货时间（商户发货时写入，未发货为空） */
    private LocalDateTime shipTime;

    /** 成交时间（用户确认收货时写入，未完成为空） */
    private LocalDateTime finishTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
