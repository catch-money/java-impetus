package io.github.jockerCN.param;

import io.github.jockerCN.jpa.pojo.JpaPojo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "PayEntity")
@Table(schema = "jpa", name = "pay")
public class PayEntity extends JpaPojo {

    @Column(name = "pay_id", nullable = false, unique = true)
    private String payId;

    @Column(name = "pay_tmp_no", nullable = false, unique = true)
    private String payTmpNo;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "payment_type", nullable = false)
    private Integer paymentType;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "order_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal orderPrice;

    @Column(name = "pay_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal payPrice;

    @Column(name = "payment_status", nullable = false)
    private Integer paymentStatus;

    @Column(name = "payment_time", nullable = false)
    private LocalDateTime paymentTime;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "remark", nullable = false, length = 255, columnDefinition = "VARCHAR(255) DEFAULT ''")
    private String remark;

    @Column(name = "trade_state", nullable = false)
    private String tradeState;

    @Column(name = "trade_type", nullable = false)
    private String tradeType;

    public static final String _payId = "payId";
    public static final String _orderId = "orderId";
    public static final String _paymentType = "paymentType";
    public static final String _customerName = "customerName";
    public static final String _customerPhone = "customerPhone";
    public static final String _orderPrice = "orderPrice";
    public static final String _payPrice = "payPrice";
    public static final String _notifyUrl = "notifyUrl";
    public static final String _paymentStatus = "paymentStatus";
    public static final String _paymentTime = "paymentTime";
    public static final String _description = "description";
    public static final String _remark = "remark";
}
