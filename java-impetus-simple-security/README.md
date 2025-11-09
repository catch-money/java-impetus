# java-impetus-simple-security
1. 认证信息提取
2. 多位置Token提取策略
   Header: Authorization: Bearer <token>
   Cookie: auth-token=<token>
   Query Parameter: ?token=<token>
   Request Body: JSON中的token字段
3. 扩展信息收集
   设备信息: User-Agent、设备指纹
   地理信息: IP地址、地理位置
   请求元数据: 请求时间、请求ID、来源
   自定义Header: 用户可配置的业务字段


1. 认证模块
   Token生成/验证（JWT默认实现）
   多种认证策略（用户名密码、API Key、第三方OAuth）
   会话管理（可选）
2. 授权模块
   基于注解的权限控制（@RequireAuth、@RequireRole、@RequirePermission）
   资源访问控制（URL模式匹配）
   动态权限验证
3. 安全防护模块
   XSS过滤（请求参数自动转义）
   CSRF Token验证
   频率限制（防暴力破解）
   敏感信息脱敏
4. 请求 → SecurityFilter → AuthenticationFilter → AuthorizationFilter → XssFilter → CsrfFilter → 业务Controller