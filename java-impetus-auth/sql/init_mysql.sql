DROP TABLE IF EXISTS template;
CREATE TABLE template
(
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator     VARCHAR(15)  NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater     VARCHAR(15)  NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted     TINYINT      NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS user_account;
CREATE TABLE user_account
(
    id                    INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_code             VARCHAR(24) UNIQUE                                           NOT NULL DEFAULT '' COMMENT '用户编码，24位字符串，唯一标识用户',
    username              VARCHAR(64) UNIQUE                                           NOT NULL DEFAULT '' COMMENT '用户名，64位字符串，唯一，用于登录或标识用户',
    email                 VARCHAR(128)                                                 NOT NULL DEFAULT '' COMMENT '邮箱地址，支持邮箱注册或找回密码',
    phone                 VARCHAR(24)                                                  NOT NULL DEFAULT '' COMMENT '手机号，可选，支持手机号注册或多因素验证',
    id_card_number        VARCHAR(24)                                                  NOT NULL DEFAULT '' COMMENT '身份证号，唯一，用于身份验证或登录',
    algorithm_code        VARCHAR(24)                                                  NOT NULL DEFAULT '' COMMENT '加密算法编码',
    password              VARCHAR(255)                                                 NOT NULL DEFAULT '' COMMENT '用户密码，建议存储为加密后的哈希值',
    status                ENUM ('PENDING','ACTIVE', 'INACTIVE', 'LOCKED', 'SUSPENDED') NOT NULL COMMENT '用户状态（如 active, inactive, locked, suspended）',
    last_login            BIGINT                                                       NOT NULL DEFAULT 0 COMMENT '最后一次登录时间（时间戳）',
    failed_login_attempts INT                                                          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数，用于登录保护机制',
    locked_until          BIGINT                                                       NOT NULL DEFAULT 0 COMMENT '锁定到期时间，记录账户何时可重新尝试登录（时间戳）',
    user_type             VARCHAR(24)                                                  NOT NULL DEFAULT '' COMMENT '用户类型',
    create_time           TIMESTAMP(3)                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time           TIMESTAMP(3)                                                 NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator               VARCHAR(15)                                                  NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater               VARCHAR(15)                                                  NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted               TINYINT                                                      NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS user_settings;
CREATE TABLE user_settings
(
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_code   VARCHAR(24) UNIQUE NOT NULL DEFAULT '' COMMENT '用户编码，24位字符串，唯一标识用户',
    username    VARCHAR(64) UNIQUE NOT NULL DEFAULT '' COMMENT '用户名，64位字符串，唯一，用于登录或标识用户',
    sso_enabled BOOLEAN            NOT NULL DEFAULT FALSE COMMENT '是否启用单点登录（true/false）',
    2fa_enabled BOOLEAN            NOT NULL DEFAULT FALSE COMMENT '是否启用双重验证（true/false）',
    2fa_method  VARCHAR(24)        NOT NULL DEFAULT '' COMMENT '主验证方式（如 SMS, Email, TOTP）',
    create_time TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator     VARCHAR(15)        NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater     VARCHAR(15)        NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted     TINYINT            NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS user_profile;
CREATE TABLE user_profile
(
    id                  INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    full_name           VARCHAR(255)                     DEFAULT NULL COMMENT '真实姓名',
    user_code           VARCHAR(24) UNIQUE NOT NULL      DEFAULT '' COMMENT '用户编码，24位字符串，唯一标识用户',
    gender              ENUM ('MALE', 'FEMALE', 'OTHER') DEFAULT NULL COMMENT '性别',
    birthdate           DATE                             DEFAULT NULL COMMENT '出生日期',
    locale              VARCHAR(10)        NOT NULL      DEFAULT 'zh_CN' COMMENT '用户语言偏好',
    timezone            VARCHAR(50)        NOT NULL      DEFAULT 'UTC+8' COMMENT '用户时区',
    profile_url         TEXT                             DEFAULT NULL COMMENT '用户头像',
    id_card_number      VARCHAR(24)        NOT NULL      DEFAULT '' COMMENT '加密后的身份证号码',
    id_card_name        VARCHAR(32)        NOT NULL      DEFAULT '' COMMENT '身份证姓名',
    id_card_issued_date DATE                             DEFAULT NULL COMMENT '身份证签发日期',
    id_card_expiration  DATE                             DEFAULT NULL COMMENT '身份证有效期截止日期',
    address             VARCHAR(255)       NOT NULL      DEFAULT '' COMMENT '通信地址',
    create_time         TIMESTAMP(3)       NOT NULL      DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time         TIMESTAMP(3)       NOT NULL      DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator             VARCHAR(15)        NOT NULL      DEFAULT '' COMMENT '当前记录的创建人',
    updater             VARCHAR(15)        NOT NULL      DEFAULT '' COMMENT '当前记录的更新人',
    deleted             TINYINT            NOT NULL      DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS user_login_info;
CREATE TABLE user_login_info
(
    id                      INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_code               VARCHAR(24)  NOT NULL UNIQUE      DEFAULT '' COMMENT '用户编码',
    username                VARCHAR(64)  NOT NULL UNIQUE      DEFAULT '' COMMENT '用户名，唯一',
    login_method            VARCHAR(32)  NOT NULL COMMENT '登录方式',
    device_info             VARCHAR(128) NOT NULL             DEFAULT '' COMMENT '登录设备（如设备类型、操作系统等）',
    access_token            VARCHAR(512) NOT NULL UNIQUE COMMENT '访问token',
    refresh_token           VARCHAR(512) NOT NULL             DEFAULT '' COMMENT '刷新token',
    login_time              BIGINT       NOT NULL             DEFAULT 0 COMMENT '登录时间',
    expiration_time         BIGINT       NOT NULL             DEFAULT 0 COMMENT 'token过期时间',
    refresh_expiration_time BIGINT       NOT NULL             DEFAULT 0 COMMENT '刷新token时间',
    last_refresh_time       BIGINT       NOT NULL             DEFAULT 0 COMMENT '最近一次刷新时间',
    token_expiry_strategy   ENUM ('AUTO_REFRESH', 'RE_LOGIN') DEFAULT 'RE_LOGIN' COMMENT 'token到期策略',
    ip_address              VARCHAR(45)                       DEFAULT NULL COMMENT '登录的IP地址',
    user_agent              VARCHAR(512)                      DEFAULT NULL COMMENT '用户代理信息（浏览器和操作系统的详细信息）',
    create_time             TIMESTAMP(3) NOT NULL             DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time             TIMESTAMP(3) NOT NULL             DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator                 VARCHAR(15)  NOT NULL             DEFAULT '' COMMENT '当前记录的创建人',
    updater                 VARCHAR(15)  NOT NULL             DEFAULT '' COMMENT '当前记录的更新人',
    deleted                 TINYINT      NOT NULL             DEFAULT 1 COMMENT '1. 正常 0. 被删除'
) COMMENT ='存储用户的登录信息';


DROP TABLE IF EXISTS third_party_login;
CREATE TABLE third_party_login
(
    id               INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_code        VARCHAR(24)  NOT NULL DEFAULT '' COMMENT '关联用户主表的外键，用于标识绑定的用户',
    provider         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '第三方登录提供商（如 Google, Facebook, 微信，支付宝）',
    provider_id      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '第三方平台的用户唯一标识（如 OpenID, UnionID）',
    access_token     VARCHAR(512) NOT NULL DEFAULT '' COMMENT '第三方登录的访问令牌（可选，用于快速获取用户信息）',
    refresh_token    VARCHAR(512) NOT NULL DEFAULT '' COMMENT '第三方登录的刷新令牌（可选，用于更新访问令牌）',
    expires_at       BIGINT       NOT NULL DEFAULT 0 COMMENT '访问令牌的过期时间（可选）',
    linked_at        BIGINT       NOT NULL DEFAULT 0 COMMENT '关联时间，记录何时绑定了第三方账户',
    third_username   VARCHAR(128) NOT NULL DEFAULT '' COMMENT '第三方平台的用户名称（如 Google 的昵称）',
    third_avatar_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '第三方平台的用户头像URL',
    specific_info    TEXT                  DEFAULT NULL COMMENT '存储平台特定的额外信息（如权限、地区、性别等）',
    create_time      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator          VARCHAR(15)  NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater          VARCHAR(15)  NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted          TINYINT      NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除',
    UNIQUE (user_code, provider)
) COMMENT ='用户第三方登录绑定表';


DROP TABLE IF EXISTS encryption_data;
CREATE TABLE encryption_data
(
    id                  INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，记录的唯一标识符',
    algorithm_desc      VARCHAR(32)                           NOT NULL DEFAULT '' COMMENT '加密算法描述（如 AES-256, RSA, bcrypt, Argon2 等）',
    algorithm_code      VARCHAR(24) UNIQUE                    NOT NULL COMMENT '加密算法编码',
    version             VARCHAR(32)                           NOT NULL COMMENT '加密算法版本（如 v1, v2）',
    algorithm_key       TEXT                                  NOT NULL COMMENT '加密密钥',
    salt                VARCHAR(255)                                   DEFAULT NULL COMMENT '盐值，用于增强密码或数据加密的安全性（适用于对称加密）',
    expire_at           BIGINT                                         DEFAULT 0 COMMENT '可选，密文的有效期，如果有过期机制时使用',
    encryption_metadata TEXT                                           DEFAULT NULL COMMENT '存储与加密操作相关的附加元数据（如加密时的特定配置或参数）',
    status              ENUM ('ACTIVE', 'EXPIRED', 'REVOKED') NOT NULL DEFAULT 'ACTIVE' COMMENT '密钥状态',
    create_time         TIMESTAMP(3)                          NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time         TIMESTAMP(3)                          NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator             VARCHAR(15)                           NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater             VARCHAR(15)                           NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted             TINYINT                               NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS permissions;
CREATE TABLE permissions
(
    id              INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，记录的唯一标识符',
    permission_id   VARCHAR(24) UNIQUE             NOT NULL COMMENT '权限ID，主键，24字符长度，用于唯一标识权限',
    name            VARCHAR(128)                   NOT NULL                              DEFAULT '' COMMENT '权限名称',
    description     VARCHAR(255)                   NOT NULL                              DEFAULT '' COMMENT '权限描述',
    permission_type ENUM ('PAGE', 'BUTTON', 'API') NOT NULL COMMENT '权限类型，表示是页面权限、按钮权限还是API接口权限',
    resource        VARCHAR(255)                   NOT NULL                              DEFAULT '' COMMENT '资源标识，如URL路径、按钮ID、API路径等，用于标识具体资源',
    http_method     ENUM ('GET', 'POST', 'PUT', 'DELETE','PATCH','OPTIONS','TRACE','NO') DEFAULT 'NO' COMMENT 'HTTP方法，仅对API权限有效，空字符串表示不限制HTTP方法',
    public_access   TINYINT                        NOT NULL                              DEFAULT 0 COMMENT '是否是公开权限 0 需要 1不需要',
    parent_id       VARCHAR(24)                    NOT NULL                              DEFAULT '' COMMENT '父权限ID',
    create_time     TIMESTAMP(3)                   NOT NULL                              DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time     TIMESTAMP(3)                   NOT NULL                              DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator         VARCHAR(15)                    NOT NULL                              DEFAULT '' COMMENT '当前记录的创建人',
    updater         VARCHAR(15)                    NOT NULL                              DEFAULT '' COMMENT '当前记录的更新人',
    deleted         TINYINT                        NOT NULL                              DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);
CREATE UNIQUE INDEX idx_permission_type_resource_http_method_unique ON permissions (permission_type, resource, http_method);


DROP TABLE IF EXISTS groups_info;
CREATE TABLE groups_info
(
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，记录的唯一标识符',
    group_id    VARCHAR(24) UNIQUE  NOT NULL COMMENT '用户组ID，主键，唯一标识用户组',
    group_name  VARCHAR(128) UNIQUE NOT NULL COMMENT '用户组名称等',
    sort_order  INT                 NOT NULL COMMENT '排序字段，用于定义角色的显示顺序',
    description VARCHAR(255)                 DEFAULT '' COMMENT '用户组描述，简要说明该用户组的功能或用途',
    create_time TIMESTAMP(3)        NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time TIMESTAMP(3)        NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator     VARCHAR(15)         NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater     VARCHAR(15)         NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted     TINYINT             NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS user_groups;
CREATE TABLE user_groups
(
    id          INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，记录的唯一标识符',
    user_id     VARCHAR(24) UNIQUE NOT NULL COMMENT '用户ID',
    username    VARCHAR(64) UNIQUE NOT NULL DEFAULT '' COMMENT '用户名',
    groups_id   TEXT               NOT NULL COMMENT '用户组ID，主键，唯一标识用户组',
    create_time TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time TIMESTAMP(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator     VARCHAR(15)        NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater     VARCHAR(15)        NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted     TINYINT            NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
);

DROP TABLE IF EXISTS user_group_permissions;
CREATE TABLE user_group_permissions
(
    id                  INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，记录的唯一标识符',
    group_permission_id VARCHAR(24) UNIQUE    NOT NULL COMMENT '用户组权限表ID，主键，唯一标识',
    group_id            VARCHAR(24)           NOT NULL COMMENT '用户组ID，外键，关联到用户组表中的group_id',
    permission_id       VARCHAR(24)           NOT NULL COMMENT '权限ID，外键，关联到权限表中的permission_id',
    access_level        ENUM ('READ', 'EDIT') NOT NULL COMMENT '权限级别（read 或 edit）',
    create_time         TIMESTAMP(3)          NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time         TIMESTAMP(3)          NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator             VARCHAR(15)           NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater             VARCHAR(15)           NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted             TINYINT               NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
) COMMENT ='用户组权限关联表';
CREATE UNIQUE INDEX idx_group_permission_unique ON user_group_permissions (group_id, permission_id);


DROP TABLE IF EXISTS user_permissions;
CREATE TABLE user_permissions
(
    id                 INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键，记录的唯一标识符',
    user_permission_id VARCHAR(24) UNIQUE    NOT NULL COMMENT '用户权限表ID，主键，唯一标识',
    user_id            VARCHAR(24)           NOT NULL COMMENT '用户ID，外键，关联到用户表中的user_id',
    permission_id      VARCHAR(24)           NOT NULL COMMENT '权限ID，外键，关联到权限表中的permission_id',
    access_level       ENUM ('READ', 'EDIT') NOT NULL COMMENT '权限级别（read 或 edit）',
    create_time        TIMESTAMP(3)          NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '当前记录的创建时间',
    update_time        TIMESTAMP(3)          NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '当前记录的更新时间',
    creator            VARCHAR(15)           NOT NULL DEFAULT '' COMMENT '当前记录的创建人',
    updater            VARCHAR(15)           NOT NULL DEFAULT '' COMMENT '当前记录的更新人',
    deleted            TINYINT               NOT NULL DEFAULT 1 COMMENT '1. 正常 0. 被删除'
) COMMENT ='用户权限关联表';
