CREATE DATABASE IF NOT EXISTS jpa;
USE jpa;
-- Drop the table if it exists
DROP TABLE IF EXISTS jpa.pay;
create table jpa.pay
(
    id             int auto_increment
        primary key,
    pay_id         varchar(24)                                 not null comment '支付id',
    pay_tmp_no     varchar(24)                                 not null comment '临时支付单号',
    transaction_id varchar(32)    default ''                   not null comment '微信支付订单号',
    order_id       varchar(24)                                 not null comment '订单id',
    payment_type   tinyint                                     not null comment '支付类型 1.微信支付',
    customer_name  varchar(32)                                 not null comment '客户姓名',
    customer_phone varchar(22)                                 not null comment '客户手机号',
    order_price    decimal(12, 4) default 0.0000               not null comment '订单总价',
    pay_price      decimal(12, 4) default 0.0000               not null comment '支付价格',
    bank_type      varchar(32)    default ''                   not null comment '付款银行',
    trade_state    varchar(12)    default ''                   not null comment '微信交易状态',
    trade_type     varchar(12)    default ''                   not null comment '微信交易类型',
    payment_status tinyint        default 0                    not null comment '支付状态 0 待支付; 1 支付完成; 2 支付失败',
    active         TINYINT(1)     DEFAULT 1                    NOT NULL COMMENT '活跃状态，1为活跃，0为不活跃',
    description    varchar(255)   default ''                   not null comment '商品描述',
    payment_time   timestamp(3)                                null comment '支付完成时间',
    remark         varchar(255)   default ''                   not null comment '备注',
    create_time    timestamp(3)   default CURRENT_TIMESTAMP(3) not null,
    update_time    timestamp(3)   default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3),
    deleted        tinyint        default 1                    not null,
    constraint pay_id
        unique (pay_id),
    constraint pay_tmp_no
        unique (pay_tmp_no)
);

create index idx_order_id
    on pay (order_id);

create index idx_pay_id
    on pay (pay_id);
