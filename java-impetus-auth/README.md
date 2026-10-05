# Java Impetus Auth 2.0

认证基础设施，提供启动绑定的批量路径规则、Spring 方法拦截及显式检查，不提供用户/RBAC 表、Controller、Servlet Filter 或默认 SecurityFilterChain。2.0 不兼容原来的 auth/auth-impl；auth-impl 和旧的数据库/Web/Token 模板已移除。

当前已提供策略/要求组合、验证方式 SPI、密码认证及可选 PasswordEncoder 适配、RFC 6238 本地 TOTP 第二因素、阶段协调、原子存储 SPI、本地与可选 Redis 实现、短期认证完成结果、显式通用完成处理入口、可选的登录会话/操作凭据生命周期、统一访问检查，以及可选 Spring Security 身份/授权薄适配与显式完成上下文发布。**不自动保存 Security Web 会话，也无内置 LDAP、OAuth2/OIDC、Passkey 等协议实现**。测试中的 FakeMethod 不是生产认证实现。

另提供可选临时验证码、扫码确认和 Passkey 挑战桥接，以及凭据业务 attributes 和应用管理的 key ring。桥接不是短信网关、扫码服务或 WebAuthn 协议实现；下面说明用户需要提供的机制。

## 2.0 完成范围与扩展边界

当前约定的认证基础设施已完成；下列使用方职责不是待补的内置功能，注册扩展也不等于每次认证必须执行它。

| 能力 | 本模块已提供 | 使用方负责 |
| --- | --- | --- |
| 策略与权限 | ALL/ANY 要求、多阶段认证、批量路径规则、Spring 方法保护及显式检查 | 可信身份/入参、当前业务权限，以及是否启用设备/IP/地区等策略 |
| 验证方式 | 密码、本地 TOTP、AuthenticationMethod SPI，以及验证码/扫码/Passkey 挑战桥接 | 凭据来源、选用的协议验证器、发送渠道、扫码确认及注册/绑定流程 |
| 完成与凭据 | 显式完成交接、可选会话/操作 Token、续期/轮换/撤销、attributes 与 key ring 接入 | 选择领取方式、业务快照、续期授权、密钥来源与轮换/退役策略 |
| 原子存储 | 有界本地存储、可选 Redis、版本/消费/幂等回执与独立期限清理 | Redis 客户端、命名空间、共享密钥、部署与应用实例时间同步 |
| Spring Security | 可选身份/授权薄适配、原生方法拦截器接入、显式完成上下文发布 | 原有登录协议、FilterChain、HTTP 响应与 Web 会话持久化 |

不额外实现用户/RBAC 表、供应商协议、业务补偿、可靠通知队列或通用流程引擎。认证之外的业务失败不会自动清理认证记录或撤销 Token；具体生命周期操作见下文。

## 依赖与启用

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-auth</artifactId>
    <version>2.0.0</version>
</dependency>
```

Java 21；Spring 接入使用 Spring Boot 4。JSON 使用 java-impetus-jackson（Jackson 3），操作指纹复用 java-impetus-crypto 的 HMAC。Redis、spring-security-crypto 与 spring-security-core 都是 optional 依赖；下游不引入 Redis/Security/Web 也可以运行核心、本地存储、JCA 密码认证及 TOTP。auth 不依赖 toolkit，不生成二维码。

```java
@Configuration(proxyBeanMethods = false)
@EnableAuth
class ApplicationAuthConfiguration {
    @Bean
    AuthenticationPolicy loginPolicy() {
        return context -> AuthDecision.require(AuthRequirement.all(
                AuthRequirement.method("password"),
                AuthRequirement.any(
                        AuthRequirement.method("totp", EvidenceReuse.operation()),
                        AuthRequirement.method("passkey", EvidenceReuse.operation()))));
    }
    // 密码/TOTP：另外提供相应 CredentialProvider Bean；其他协议提供 AuthenticationMethod<P> Bean。
}
```

```yaml
java-impetus:
  auth:
    default-policy: loginPolicy
    required-policies: []     # 全局附加策略；不能被局部默认选择清除
    store: local
    maximum-transactions: 10000
    transaction-ttl: 5m
    retention-ttl: 2m
    operation-lease: 15s
    maximum-attempts: 5
    maximum-operations: 32
    credentials-enabled: false # 按需启用 AuthCredentialService；已有会话机制不必启用
    maximum-credentials: 10000  # 会话/操作凭据独立容量，包含仍在保留期的终态记录
    maximum-renewal-receipts: 32  # 每个会话在 retention-ttl 内的续期回执上限；满时拒绝新续期
```

@EnableAuth 显式启用装配，包括默认 NATIVE 方法 Advisor；无全局 AutoConfiguration.imports，也无自动 Web 保护。默认 Bean 让位于使用方 Bean；初始化/组件注册有 INFO 日志，不打印证明或业务数据。只使用核心服务、不要自动方法保护时设置 `java-impetus.auth.method-security.mode=DISABLED`。

不用 Spring 时可通过构造器组装 AuthenticationService、PolicyRegistry、MethodRegistry 和存储；访问检查直接构造 AuthAccessService，完成交接直接构造 AuthCompletionService。InMemoryAuthTransactionStore 在应用关闭时需 close()。服务/注册结构可复用，认证状态按事务独立隔离。

## 策略与规则

三个核心契约：AuthenticationPolicy、AuthRequirement、AuthenticationMethod<P>。

- PASS：此策略没有额外要求，不等于认证成功或业务授权通过。未知身份不能仅凭 PASS 完成认证。
- REQUIRE：ALL 全部满足，ANY 任选一种合规方案；保持声明顺序，不展开候选组合的笛卡尔积。
- DENY：拒绝，其他策略不能覆盖。
- 已登记要求与后续要求只增加不削弱；验证后评估 CONTINUE 和 FINAL，消费完成结果前再评估 FINAL。
- 策略读取本次 data 原对象；不会复制或存入认证事务。evaluate 可重复调用，不应用于付款/发送验证码等副作用。

EvidenceReuse.session() 接受应用认可且仍有效的会话证据，不替应用验证会话；within(duration) 使用真实 verifiedAt；operation() 同时匹配用途/操作。均检查身份和未来时间。账户禁用、会话撤销等动态规则由应用策略查询自己的可信数据。

使用本模块会话时，通过 AuthCredentialService.validateSession()/invocation() 校验会话并取得可信证据。校验结果仅是当前调用的快照；用户账户禁用、业务权限，以及在途认证期间需要再次检查的撤销条件仍由应用的 FINAL 策略控制。

@UseAuthPolicy 支持方法/类型的默认选择：方法 > 类 > 全局。PolicyRegistry.select(owner, method) 和 AuthMethodRules 均复用 Spring 的组合/接口/桥接注解解析。认证流程及受保护访问检查附加全局 required-policies，以及 invocation.requiredPolicies() 中的路由等强制策略。启用的方法 Advisor 只匹配本模块注解；没有本模块注解的方法（包括仅使用 Security 注解的方法）不进入 Auth，也不因这些全局策略被扩大保护范围。

## 统一访问检查

`AuthAccessService.check(invocation, requirement)` 是原生接入与可选 Security 适配共用的判断核心。调用方先校验自己的会话/登录协议，再提交可信身份与验证事实；它不读取 HTTP 请求、不验证 Token、不创建认证事务、不准备/发送挑战，也不消费一次性凭据。

角色/权限来自应用自己的 `AuthorityProvider`，不要求 RBAC 表、用户基类或 `UserDetails`：

```java
@Bean
AuthorityProvider authorityProvider(ApplicationAuthorityService application) {
    return context -> {
        // ApplicationAuthorityService 是应用自己的服务；按可信身份读取当前业务权限。
        AuthSubject subject = context.binding().subject();
        return new AuthAuthorities(application.roles(subject), application.permissions(subject));
    };
}

// 声明可复用，不缓存请求/判断结果。
AuthAccessRequirement readOrder = AuthAccessRequirement.authenticated(
        AuthorityRequirement.rolesAny("ADMIN", "BUYER"),
        AuthorityRequirement.permissionsAll("order:read"));

// 本模块会话：先校验有效期/撤销并取得可信证据；外部会话见下节，不必换成我们的 Token。
AuthInvocation trusted = credentials.invocation(sessionToken, operationBinding, "orderPolicy", businessContext);
AuthAccessDecision decision = access.check(trusted, readOrder);
```

| 状态 | 含义 | 调用方动作 |
| --- | --- | --- |
| ALLOWED | 本次身份、业务权限与认证策略满足 | 执行受保护业务 |
| UNAUTHENTICATED | 没有可信身份 | 由应用启动自己的登录流程 |
| DENIED | 显式拒绝、业务权限不足或策略拒绝 | 拒绝业务；不能靠完成 TOTP 获得缺失的权限 |
| AUTHENTICATION_REQUIRED | 已知身份且权限允许，但验证因素/时效/操作绑定不满足 | 显式启动或继续认证，再检查业务访问 |

- `authenticated()` 只声明需要身份；具体密码/MFA 等因素仍统一放在现有 `AuthenticationPolicy` 中，不再建立第二套认证要求配置。
- `rolesAll/rolesAny/permissionsAll/permissionsAny` 分别在组内做 ALL/ANY；多个组之间全部是 AND，保留 `(A OR B) AND (C OR D)`，不能把两组任选条件合并成一个宽松的 ANY。角色与权限独立、大小写敏感、按字面精确匹配，不自动加 `ROLE_`、通配符或继承关系。
- Spring 启用 `@EnableAuth` 后注册默认无授权的 AuthorityProvider 和 AuthAccessService；应用同类型 Bean 可覆盖。未声明角色/权限约束时不查询 provider；声明约束而未提供业务权限时拒绝，不默认授予权限。
- 顺序是可信身份/证据检查 → 当前业务权限 → 现有 FINAL 认证策略。无身份直接 UNAUTHENTICATED；权限不足直接 DENIED，不继续执行挑战策略。provider 和策略共用本次 FINAL context，data 为原对象；库不保存请求或权限结果，也不在存储锁内调用 provider。
- 局部选定策略替换全局默认选择，required-policies 在受保护访问中继续附加；DENY 不能被其他规则覆盖。provider/策略故障、null 返回或未知策略抛异常，不转成 ALLOWED 或伪装为正常的权限不足；上层不可捕获异常后放行。
- AUTHENTICATION_REQUIRED 携带完整组合 `AuthRequirement`；可用 `next(evidence, binding, now)` 查询候选因素。检查核心不承诺当前已注册可执行这些协议；实际启动由 AuthenticationService 的 MethodRegistry 或应用自己的外部协议适配决定。
- `publicAccess()` 明确表示此入口没有认证/业务权限要求，不运行受保护入口的默认/附加策略；不能附带角色/权限约束，与 invocation 中显式 policy 或 requiredPolicies 同时声明会报错。它只适用于本次声明的入口，不能借此豁免后续独立的受保护方法检查。`deny()` 无条件拒绝。

追加认证时将**同一可信 invocation、选定策略和操作绑定**交给 `authentication.begin(trusted, startOperationId, null)`；不要只拿返回的要求树另建一个不同的策略，否则会丢失动态策略的约束。仍缺因素时继续原有多阶段 API。完成后更新可信证据并再次 `access.check(...)`：权限可能在用户完成 MFA 时已撤销。检查结果只是当前调用的快照，不是可跨请求保存/重用的业务授权凭据，不保证业务操作与后续撤销原子串行。

## 批量请求规则与方法接入

### 一组路径共用权限和策略

```yaml
java-impetus:
  auth:
    default-access: authenticated
    default-policy: normalPolicy
    required-policies: [accountPolicy]
    rules:
      - paths: [/public/**, /health]
        methods: [GET]          # 不填表示全部方法；方法名称统一为大写
        access: public
      - paths: [/orders/**, /invoices/**]
        methods: [POST, PUT]
        access: authenticated
        roles:
          any: [ADMIN, OPERATOR]
        permissions:
          all: [document:write]
        policy: sensitivePolicy
      - paths: [/internal/**]
        access: deny
```

规则按配置顺序首个命中，不按路径长度重排、不合并后面的匹配项；具体规则应放在宽泛规则前面。未匹配默认 AUTHENTICATED，可明确改成 DENY 或 PUBLIC。roles/permissions 各自支持 all/any，多组之间 AND。public/deny 与权限或 policy 混用、未知策略、空路径或无效方法在启动时失败。

路径使用 Spring Core 的 [AntPathMatcher](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html)，支持 `*`、`**`、`?`，大小写敏感；不开放正则路径变量。这里不是 MVC/Security 的 PathPattern 规则。适配器必须传入与实际路由一致的、经过应用规范化的绝对路径，不含 context-path、查询串或 fragment；库不解析 URL、解码 `%xx`、移除分号参数或规整 `..`。应用的路径处理/安全过滤策略必须一致，不能以原始 request URI 猜测实际路由。

```java
// rules 为注入的 AuthRequestRules；trusted 来自已经校验的会话/登录身份。
AuthAccessRule route = rules.resolve(normalizedPath, requestMethod);
AuthInvocation prepared = route.apply(trusted);
AuthAccessDecision decision = access.check(prepared, route.requirement());
// 只在 ALLOWED 时执行业务；另外三种决策由应用处理。
// 需要追加认证时把 prepared 原样交给 authentication.begin/后续操作。
```

`policy` 是路由附加要求，不替换默认/全局策略。apply 只组合策略标识，保留 binding、证据与原 data，不修改原 invocation；requiredPolicies 会绑定到认证事务，多阶段操作和最终消费必须携带同一列表。策略内容仍按本次数据动态评估，不能中途去掉路由策略再领取结果。

AuthRequestRules 只保存固定规则、模式和策略标识；AuthMethodRules 只缓存类/方法定义，不保存调用参数、请求、判断或结果。没有配置热更新、全类路径扫描、HTTP 注册或自动挑战；使用方明确把解析结果接到自己的入口。

### Spring 方法自动检查

默认 `NATIVE` 模式使用 Spring AOP 的 MethodInterceptor/Advisor，不依赖 Spring Security 或 AspectJ。应用只需提供一次可信输入适配 Bean，然后正常调用 Spring Bean，无须每次手动反射方法再 verify：

```java
@Configuration(proxyBeanMethods = false)
@EnableAuth
class MethodAuthConfiguration {
    @Bean
    AuthMethodInvocationProvider methodInputs(ApplicationAuthService application) {
        // ApplicationAuthService 是应用自己的可信上下文服务，不是库内类型。
        // 负责 realm、已验证身份/证据、purpose/operation/initiator 和原业务 data。
        return call -> application.trustedInvocation(call.getMethod(), call.getArguments());
    }
}
```

```java
@Service
class OrderService {
    @AuthAccess(permissionsAll = "order:write")
    @UseAuthPolicy(OrderPolicy.class)
    public void update(OrderCommand command) { /* 应用业务 */ }
}

orderService.update(command); // 经 Spring 代理自动检查；非 ALLOWED 不执行业务
```

```yaml
java-impetus:
  auth:
    method-security:
      mode: NATIVE  # NATIVE（默认） / SECURITY / DISABLED；不会因依赖存在自动切换
      order: 250    # 默认在 Security PreFilter/PreAuthorize 之后、常规事务 Advisor 之前
```

PUBLIC/DENY 不调用输入 Provider，也不读取 Security 身份。受保护方法缺少 Provider、Security 模式缺少身份适配器，或应用 Provider 出错时抛出错误，绝不跳过检查或回退放行。输入适配器每次调用按当前参数取得可信 AuthInvocation；库只缓存方法规则，不缓存原调用、业务 data、权限判断或返回值，不建立 ThreadLocal 上下文栈。路由权限仍在应用 HTTP/Security 入口检查；Provider 可以把已解析路由的 requiredPolicies 带入，但 AuthInvocation 不携带路由权限要求，方法 Advisor 不自动合并未知的 HTTP 规则。不能从请求直接接受客户端声明的身份/证据/策略。

这是标准 Spring **代理边界**：只保护经代理调用的 Spring Bean 方法，不保护任意 new 出来的对象、自调用（this.xxx）、private 方法或不能代理的 final 方法。final 类可通过可代理接口使用 JDK 代理；否则不能依赖类代理保护。遵循应用自己的事务/代理设置，共用 Spring 的基础设施 AutoProxyCreator，不强制 CGLIB、不创建另一套代理器。自定义顺序需自行核对 PreFilter、事务及其他 Advisor 的执行边界。[Spring 代理限制](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

使用方可按 Bean 名 `authMethodSecurityAdvisor` 提供自己的 Advisor，替换选定模式的默认入口；AuthMethodPointcut、规则解析器与核心服务同样保留覆盖点。DISABLED 只取消本模块的自动方法 Advisor，不禁用核心服务或应用自己的 Security/事务配置。

### 与 Security 方法注解共存

显式选择 `mode=SECURITY`，提供 `SecurityIdentityMapper` 和上述 `AuthMethodInvocationProvider`，本模块使用 Security 原生 `AuthorizationManagerBeforeMethodInterceptor`，只接入一个薄 AuthorizationManager；不再安装 NATIVE Advisor。缺少 spring-security-core 时启动失败，不偷偷回退。SecurityIdentityMapper 负责将框架提供的当前 Authentication 转为可信身份/实际因素；业务权限仍由应用的 AuthorityProvider 提供。

应用自行通过 `@EnableMethodSecurity` 启用 Security 的 `@PreAuthorize`、`@PostAuthorize` 等原生能力；`@Secured` / JSR-250 需要自行开启相应选项。这需要应用自己的 security-config 或适用的 Starter；本库只在测试范围引入 security-config，不强制消费者引入。

| 方法声明 | 执行入口 |
| --- | --- |
| 仅 `@AuthAccess` / `@UseAuthPolicy` | 本模块选定的方法 Advisor |
| 仅 Security 注解（且类上没有本模块注解） | 仅 Security 原生机制；不读取 Auth 输入/身份/权限或执行 Auth 全局策略 |
| 同时声明两套注解，或类默认与方法声明叠加 | 两套 Advisor 各自执行，不能互相覆盖；所有适用检查都需通过 |
| 未声明两套注解 | 不因本模块的 default-access/default-policy/required-policies 自动拦截 |

NATIVE 模式也可以和应用已启用的 Security 注解共存，仍各管自己的声明；区别是本模块检查用原生异常，而 SECURITY 模式检查由标准 Security 拦截器处理。`@AuthAccess(PUBLIC)` 只免除本层要求，不能覆盖 `@PreAuthorize` 等另一层。`@PostAuthorize` 本来就在业务返回后检查，不能把它理解成业务执行前保护或自动回滚。

SECURITY 拒绝抛出 `AuthorizationDeniedException`，其 authorizationResult 为 `AuthSecurityDecision`，保留 DENIED/UNAUTHENTICATED/AUTHENTICATION_REQUIRED。SecurityContext 中没有 Authentication 时沿用框架的 `AuthenticationCredentialsNotFoundException`；不伪造匿名身份或自动开始挑战。本检查不更新 SecurityContext、不消费凭据、不保存 Web 会话。权限需复用 GrantedAuthority 时，可在应用 AuthorityProvider 中使用 Security 的 `AuthorityUtils.authorityListToSet`；角色字面值/ROLE_ 前缀、RoleHierarchy 等策略仍由应用明确配置，不自动推断因素或层级。[Security 标准方法机制](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)

### 显式方法检查（非代理调用等场景）

```java
class OrderService {
    @AuthAccess(permissionsAll = "order:write")
    @UseAuthPolicy(OrderPolicy.class) // OrderPolicy 是唯一匹配的 AuthenticationPolicy Bean
    public void update(OrderCommand command) { /* 应用业务 */ }
}

// applicationType 是业务实现类型；不要把动态代理类当作定义类型。
Method method = OrderService.class.getMethod("update", OrderCommand.class);
AuthAccessRule route = requestRules.resolve(normalizedPath, requestMethod);
methodAccess.verify(trusted, OrderService.class, method, route);
// verify 成功后再执行非代理业务；正常 Spring 代理调用用上节自动检查，无须再重复 verify。
```

`AuthMethodAccessService.check` 返回原四种决策，`verify` 非 ALLOWED 时抛出携带 decision 的 AuthAccessDeniedException；没有 HTTP 状态码约定、自动登录或挑战。无路由时可省略最后一个参数。调用参数/业务对象通过可信 invocation.data 传入，库不自动构造请求上下文，也不保护任意 POJO 的使用。

`@AuthAccess` 和 `@UseAuthPolicy` 各自按方法 > 类解析，支持接口/父类、泛型桥接与 Spring 组合注解。方法访问声明替换类访问默认；局部策略替换默认选择，但不能移除全局附加或路由策略。路由与方法的角色/权限全部 AND，任一 DENY 拒绝；PUBLIC 只能免除自己的要求，不能冲掉另一层保护。公开/拒绝方法不能同时声明认证策略。

方法声明在首次 resolve 时校验并缓存；没有扫描所有业务方法。应用需要启动时提前发现声明错误时，可在自己的初始化中显式 resolve 已知受保护方法。规则 Bean 可以覆盖，但不提供运行时原地修改的配置集合。

追加认证需要取得**同一组合调用**：

```java
AuthMethodRule methodRule = methodRules.resolve(OrderService.class, method).and(route);
AuthInvocation prepared = methodRule.apply(trusted);
AuthAccessDecision decision = access.check(prepared, methodRule.access().requirement());
// 若 AUTHENTICATION_REQUIRED：authentication.begin(prepared, operationId, null)
// 完成后刷新可信身份/证据，以同样的规则再次检查；不要保存旧的业务授权判断。
```

显式 check/verify 是调用方主动采用的检查入口，未声明的方法仍按 authenticated 检查；这不扩大自动 Advisor 的注解匹配范围。追加认证或 HTTP 错误转换仍由应用明确接入，不在方法拦截器里创建第二套挑战/响应流程。

### 可选 Security 规则接入

```java
// RequestInfo 是应用定义的适配对象，不是库内 Servlet 类型。
AuthorizationManager<RequestInfo> manager = securityAdapter.ruleAuthorizationManager(
        request -> applicationTrustedInvocation(request),
        request -> requestRules.resolve(request.normalizedPath(), request.method()));
```

每次只解析一次规则，复用 Security 身份桥接和核心检查，保留路由策略与四种决策；PUBLIC/DENY 不访问 Authentication supplier。使用方把 manager 连接到自己的 Security 入口，库不注册 FilterChain，也不自动创建请求/方法拦截器。EnableAuth 额外提供可覆盖的 AuthRequestRules、AuthMethodRules 和 AuthMethodAccessService Bean，并记录 INFO 初始化日志。

一次性操作凭据的 `consumeOperation(...)` 仍是应用明确调用的独立原子边界，访问检查不代替消费。库也不替使用方发布认证成功事件、创建业务会话或执行业务。

## 登录协议与自定义接入边界

登录协议、验证因素与业务授权相关，但不是同一层：协议处理凭证交换和可信来源，因素策略决定是否还需要密码/TOTP 等事实，访问检查决定当前身份是否能执行某项业务。不能把 `Authentication.isAuthenticated()` 或外部登录回调成功直接当成已满足我们全部 MFA/业务权限。

| 接入场景 | 外部职责 | 本库交接点 |
| --- | --- | --- |
| 应用自己的账号密码登录 | 账号模型、资格与密码存储 | PasswordCredentialProvider + PasswordVerifier；其他自定义证明使用 AuthenticationMethod<P> |
| LDAP / OAuth2、OIDC 第三方登录已由外部完成 | 成熟库负责协议验证，应用绑定到自己的用户身份 | 服务端将已验证身份/真实事实映射为 AuthInvocation，然后追加认证或直接访问检查 |
| 已有外部会话或第三方 Bearer Token 访问 API | 外部会话机制 / Resource Server 先验证有效性 | 构造可信 AuthInvocation 后调用 AuthAccessService，无须签发第二份 Impetus Token |
| 对第三方提供标准 OAuth2/OIDC 授权服务 | Client、授权交互和标准令牌端点 | 属于 Authorization Server，不把本库 opaque 会话 Token 冒充标准协议 Token |

Spring Security 提供 [LDAP 认证](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/ldap.html)、[OAuth2/OIDC 第三方登录](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html)、验证 JWT/opaque Bearer 的 [Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)，以及独立的 [Authorization Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/index.html)。这些协议实现保留在对应框架中，auth 核心不重复实现它们；Security 薄适配及显式完成结果发布见下节，Web 会话管理保留在应用层。

自定义能力有三个现成入口：

1. **库负责证明阶段**：实现 `AuthenticationMethod<P>`，按需要提供 begin/verify/提交后的 dispatch。密码和 TOTP 已有独立 provider/verifier；不需要把每种协议写进固定 LoginMethodEnum。方式必须实际验证自己的证明，而非接受客户端声称“验证成功”。
2. **外部已完成验证**：应用的可信映射函数直接构造 `AuthSubject`、`AuthEvidence` 和 `AuthInvocation`。无需增加一个什么都不验证的 AuthenticationMethod，也无需对已通过的因素再调用 authenticate。
3. **因素后续决策与业务授权**：AuthenticationPolicy 决定追加因素/动态风控；AuthorityProvider 从当前可信业务数据取权限。设备、IP、区域、特定账户等条件放入本次 data，由应用决定启用，不固定进库。

已验证外部登录的交接示意：

```java
// 仅在外部协议校验通过后，由服务端执行以下映射；不是客户端提交的用户 ID/claims。
// OIDC 用户映射通常以可信 issuer + subject 关联应用账号，不按未验证邮箱自动合并账户。
AuthSubject subject = applicationUserMapping(validatedProtocolResult);
AuthEvidence external = new AuthEvidence("oidc", subject, trustedActualVerifiedAt,
        trustedOriginalPurpose, trustedOriginalOperation);
AuthBinding binding = new AuthBinding(subject.realm(), subject, "login",
        serverLoginOperation, validatedInitiator);
AuthInvocation trusted = new AuthInvocation(binding, "externalLoginPolicy", List.of(external), businessContext);

// 已有因素满足策略时直接完成；否则选择缺失的已注册因素，例如本地 totp。
AuthResult result = authentication.begin(trusted, acceptanceOperationId, null);
// 只有整个策略 COMPLETED 后，应用才决定消费结果或显式兑换本模块会话。
```

验证事实应保持**真实的验证时间与原始绑定**。不能把收到回调/每次业务请求的时间改写成旧密码或旧 MFA 的 verifiedAt，也不能仅凭一个外部 `oidc` 事实声称已验证 password/totp。上游 MFA 声明只有在协议/供应商语义可靠且应用明确认可时才能映射为相应事实。只知道可信身份、拿不到合适的实际验证事实/时间时，可以仅绑定 subject，evidence 保持为空；身份检查可以成立，要求因素或新鲜度的策略仍会要求验证。

标准协议的签名、issuer/audience、重定向和请求关联等校验应由所选协议实现处理。跨请求时先验证外部会话/Token 当前仍有效，再恢复原证据；这里的映射不是免检认证通道。本库只能验证证据内部一致性，不能证明任意输入真来自某个登录协议。

**外部第一因素成功不等于完整登录已完成**：例如策略是 `oidc AND totp`，外部回调后仍是 ACTIVE，没有可兑换的完成结果。应用的登录接入应让它只处于受限的待完成状态，不先开放受保护业务；Auth 的薄适配不修改 Spring Security 默认登录流程，不能只把这段代码挂到 success handler 就认为默认 Security 会话已被安全降权。应用需明确发布最终认证/会话的边界，或保证受保护操作始终经过统一检查；完成后的显式上下文发布见 Security 完成实现小节。

“提供 API 给第三方登录”若指普通自定义登录接口，由应用自己的 Web 层收取证明、调用上述契约并返回业务结果；若指作为 OAuth2/OIDC 身份提供方，则使用授权服务器协议实现，将我们的附加因素能力接入其验证流程，不在 auth infra 中新增 Controller 或授权服务器。

## 可选 Spring Security 薄适配

组件位于 `auth.security`；生产适配只依赖由 BOM 管理的可选 `spring-security-core`，没有 Servlet/Web、security-web、security-config 或协议客户端运行依赖（security-config 仅用于测试）。下游按需引入 Security，自行管理 FilterChain、原生方法注解启用和登录机制；本模块的方法 Advisor 按前文显式模式装配。

`@EnableAuth` 在 Security 核心类存在且应用提供 `SecurityIdentityMapper` Bean 时注册 `AuthSecurityAdapter`。没有默认 principal 名称猜测；只引入 Security 并不会自动选择用户身份或保护入口。应用同类型 adapter Bean 覆盖默认实现；已有 `AuthenticationTrustResolver` Bean 会被复用，未提供时仅在 adapter 内使用默认 resolver，不注册全局 resolver。

### 可信身份映射

```java
@Bean
SecurityIdentityMapper securityIdentityMapper(ApplicationSecurityMapping application) {
    return (authentication, invocation) -> {
        // ApplicationSecurityMapping 是应用的可信映射服务，不是本库规定的用户实体。
        AuthSubject subject = application.subject(authentication, invocation.binding().realm());
        if (Objects.isNull(subject)) return null; // 未识别身份，不回退 getName()/客户端 userId
        // 只知道身份、没有可靠的因素/真实时间时：return new SecurityIdentity(subject);
        return new SecurityIdentity(subject, application.verifiedEvidence(authentication));
    };
}

AuthInvocation trusted = securityAdapter.invocation(currentAuthentication, invocationTemplate);
AuthAccessDecision decision = securityAdapter.check(
        currentAuthentication, invocationTemplate, requiredAccess);
// 需要追加因素时，将 trusted 交给原 authentication.begin(...)，不是在授权检查内自动挑战。
```

- mapper 接收当前 Authentication 和原始 AuthInvocation，可读取可信 realm/操作意图/原 data；只返回身份/实际验证事实，不能替换 policy、purpose、operation、initiator 或 data。
- SecurityIdentity 只是当次映射的紧凑投影，不规定应用的持久 principal 或会话存储类型；adapter 不替换原 principal，也不把完整用户对象保存进认证事务。
- 默认使用 `AuthenticationTrustResolver` 排除 null、未认证和匿名对象；匿名对象即使 `isAuthenticated()==true` 也不参与身份映射。remember-me 可以提供身份，但不因此拥有 password/totp 事实。需要更严格的身份接受条件时，应用可提供自己的 resolver/mapper。
- 未认证、匿名或 mapper 返回 null 时，投影明确去掉模板中的 subject/evidence，受保护检查返回 UNAUTHENTICATED；不能把旧模板身份当成当前 Security 登录。已经取得可信原生完成结果、但没有 Security 身份时，直接使用核心服务，不走这个桥接入口。
- 已映射身份须与模板 realm/已绑定 subject 及证据身份一致；模板事实与映射事实保持原时间/原用途/操作，按需浅合并，不复制业务 data、不推断因素、不缓存 Authentication/映射结果。未来时间与因素时效继续由核心验证。
- Security 的 GrantedAuthority 不自动拆成我们的 roles/permissions，不自动解析 ROLE_ 或把 FACTOR_* 字符串视为验证证据。业务权限仍统一通过 AuthorityProvider 读取当前可信数据；映射标准或供应商因素时间必须由应用明确认可。
- mapper/策略/provider 故障向上抛出，不能捕获后放行。mapper/函数/服务实例可并发复用，应用不得把本次请求存进共享 Bean 字段。

### 委托 Spring 授权入口

```java
// T 可以是应用业务操作对象、Security 请求上下文或方法调用；由使用方选择。
AuthAuthorizationManager<ApplicationOperation> manager = securityAdapter.authorizationManager(
        operation -> applicationInvocation(operation),  // 服务端选择绑定、policy 和原 data
        AuthAccessRequirement.authenticated(AuthorityRequirement.permissionsAll("order:read")));

// 规则需动态选择时，第二个参数也接受 Function<T, AuthAccessRequirement>。
AuthSecurityDecision result = manager.authorize(() -> currentAuthentication, operation);
AuthAccessDecision coreDecision = result.decision();
```

AuthAuthorizationManager 实现 Spring `AuthorizationManager<T>`，只委托现有 AuthAccessService，不安装任何请求/方法拦截器或全局规则。使用方将返回的 manager 接入自己的 Security 配置。PUBLIC/DENY 不求值 Authentication supplier、不调用 mapper；仍检查本库公开入口声明约束。

`AuthSecurityDecision` 保留全部四种核心结果，只有 ALLOWED 的 `isGranted()` 为 true，其他三种均不放行；不返回 null/弃权。Spring 默认 `verify(...)` 的 AuthorizationDeniedException 保留原 AuthorizationResult，应用可从 `getAuthorizationResult()` 识别 AuthSecurityDecision，并读取 AUTHENTICATION_REQUIRED 的完整要求。这只是提供交互信息，不自动转换为 HTTP 响应、创建挑战或跳过异常。应用将多个 manager 组合时需自行保留详细结果，不假设任意外层包装都会保留它。

接口依据：[Spring 授权扩展](https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html)、[AuthorizationDeniedException](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/authorization/AuthorizationDeniedException.html)。

## 通用完成处理入口

没有链式 `end()`，也没有自动全链路 onCompleted 回调。`begin`/`authenticate` 发起后，`verify` 或后续方式提交在全部要求满足、FINAL 策略通过并且存储提交成功时返回 COMPLETED 和 completionId；已有可信事实满足全部要求时，begin 本身也可以直接完成。此时只保存完成结果，尚未消费，也没有自动签发 Token 或发布 Security 认证。

`@EnableAuth` 注册 `AuthCompletionService`，使用方同类型 Bean 可覆盖；不使用 Spring 时直接 `new AuthCompletionService(authentication)`。核心 handler 位于 `auth.completion`，不依赖 Security/Web/Redis：

```java
@Bean
AuthCompletionHandler<ApplicationLoginResult> applicationCompletionHandler(ApplicationLoginService application) {
    // ApplicationLoginService / ApplicationLoginResult 是应用自己提供的服务和返回类型。
    return context -> application.accept(
            context.completion().binding().subject(),
            context.completion().evidence(),
            context.invocation().data());
}

// 服务端明确选择 handler；只在整体 COMPLETED 后调用，不让客户端选择任意处理器。
ApplicationLoginResult result = completions.complete(
        trustedInvocation, verified.transactionId(), verified.completionId(), applicationCompletionHandler);
```

- `AuthCompletionHandler<R>.handle(AuthCompletionContext)` 是一个同步扩展点，可用 Lambda 或应用 Bean；可以返回 null。不会自动发现/执行全部 handler Bean，也不在每次 COMPLETED 状态读取或认证请求重放时调用。
- 顺序为检查所有权/FINAL 策略/有效期 → 原子消费完成结果 → 在当前调用线程执行一个选定 handler。没有后台任务、额外完成状态机或回调结果缓存，处理器不在存储锁/事务内执行。
- context 只在本次调用创建，含 transactionId、原 AuthInvocation 引用和领取到的 AuthCompletion；不复制 data，也不存进认证记录。原 invocation 的 subject 可能仍为空、evidence 可能只有早期因素；**以 completion.binding()/evidence() 为最终可信身份和验证事实**。completion 是消费前取得的快照，其 consumed=false 不代表库内记录尚未消费。
- handler/服务实例可复用，但不能把本次 context、data、完成结果或返回值保存在共享 Bean 字段。库内认证事实/操作记录按 consume 的现有语义清理；用户主动保存结果属于应用职责。
- 本入口、直接 `consume(...)`、`issueSession/issueOperationCredential` 是完成结果的三种互斥领取方式。本入口不签发本库 Token，不能在 handler 内再对同一个完成结果调用凭证签发。需要本库 Token 时直接走原签发 API：完成结果消费与凭证/签发回执原子提交后才返回 Token；已签发后的业务失败不归 auth 管理。
- `AuthenticationMethod.dispatch` 仍只是阶段挑战提交后的通知，不是全链路完成处理入口。AuthResult 只含状态/定位标识，不能替代完成结果中的可信事实。

### 失败边界

- 消费没有提交成功时不会调用 handler。若已确认未提交，可以重试领取；存储超时不等于未提交，应通过 state 查询 consumed，而不是自动重跑。
- 消费提交成功后，handler 抛出的异常原样传给调用方；认证保持完成且已消费，不回滚消费、不撤销凭证、不自动重试、不恢复认证事实，也不记录用户业务的执行状态。
- 消费提交后进程退出或响应丢失，不能从 consumed 推断 handler 是否执行成功；再次调用 complete 会拒绝已消费的结果。本入口只保证消费的单次性，不保证回调可靠投递或外部业务 exactly-once。应用自行决定业务失败处理；库不提供外部数据库回滚、补偿或业务重试协调。
- 已签发 Token 的后续业务失败完全属于应用业务范围，不调整认证链路或 Token 状态。凭证签发自身的响应丢失继续使用原签发幂等回执机制，和这个单次结果交接入口不是同一路径。

### 可选 Security 完成实现

原 `AuthSecurityAdapter` 保持只读；新增 `SecurityCompletionHandler` 是上述通用 handler 的一种可选实现。`@EnableAuth` 在 Security 核心类存在且应用提供 `SecurityCompletionMapper` Bean 时注册它；身份读取的 SecurityIdentityMapper 与完成映射是独立契约。应用同类型 handler Bean 可覆盖；注册 Bean 本身不触发同步。

```java
@Bean
SecurityCompletionMapper securityCompletionMapper(ApplicationSecurityMapping application) {
    return (context, previousAuthentication) -> {
        // 应用决定自己的 principal 模型、关联身份、当前权限与实际验证事实。
        // 不修改 previousAuthentication，不盲目继承旧权限，不把所有因素的时间改为当前时间。
        return application.authentication(
                context.completion().binding().subject(),
                context.completion().evidence(), previousAuthentication);
    };
}

// 显式选择这个 handler；没有前置 Security Authentication 的原生认证也可使用。
SecurityContext context = completions.complete(
        trustedInvocation, verified.transactionId(), verified.completionId(), securityCompletionHandler);
```

mapper 接收原完成 context 和当前 Authentication（可以为 null），必须返回非匿名且已认证的 Authentication。应用负责将这个 Authentication 的 principal/realm 与 completion 的可信身份关联，并按实际验证事实维护后续 SecurityIdentityMapper 的证据来源；库不猜 getName/UserDetails，不自动赋予角色、复制旧权限或生成 FACTOR_* 权限。业务访问仍要复核当前授权。

handler 在当前线程通过 SecurityContextHolderStrategy 创建新的 SecurityContext 并发布，返回同一 context；不修改旧 context/Authentication，也不切换全局 strategy。配置复用应用提供的 strategy/trust resolver Bean，缺省使用 Security 当前 strategy 和内部默认 resolver，不注册全局默认 Bean。应用应在启动时选定 strategy，不在请求处理中切换；mapper 必须只构造结果，不修改旧认证。

这里只发布当前上下文，**不等于已经建立或持久化 Web 登录会话**。需要跨请求保存时，应用使用自己的 SecurityContextRepository 保存返回的 context，并处理自己的 SessionAuthenticationStrategy/会话固定防护等登录策略；auth 不依赖 Servlet/security-web、不接管 FilterChain 或 Reactor 上下文。使用 SecurityContextHolderFilter/显式保存模式时，仅设置 holder 不会自动保存到后续请求。依据：[Spring 上下文架构](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html)、[认证持久化边界](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)。

mapper/发布异常直接抛出，不自动回滚认证消费；映射失败或结果不合格时 handler 不发布新 context。会话保存或应用回调失败的事务/恢复不纳入核心。本入口不声称替代完整 Spring Security 登录协议、Session 策略或成功处理链。

## 调用与多阶段交互

包根为 io.github.jockerCN.auth；子包为 policy、authorization、completion、security、method、transaction、credential、store、annotation、config。

```java
// 以下值由服务器可信适配器构建，不能直接反序列化客户端整份 AuthInvocation。
// initiator 是已验证的发起端绑定，不是任意请求头；subject=null 表示尚未验证。
// operation 绑定这次具体意图/关键参数，不能用所有业务操作共用的固定字符串。
AuthBinding binding = new AuthBinding(
        "main", null, "login", serverOperationId, validatedInitiator);
AuthInvocation invocation = new AuthInvocation(binding, "loginPolicy", businessContext);

// 完整证明直接验证；密码不需要先生成无用挑战。
AuthResult first = service.authenticate(invocation, startOperationId, "password", passwordProof);

// ACTIVE 且无当前挑战时，继续同一个事务的下一因素。
AuthResult next = service.beginNext(
        invocation, first.transactionId(), nextOperationId, null);
// methodId=null：选择第一个已注册的合规方式；也可显式选择允许方式。
AuthResult verified = service.verify(
        invocation, next.transactionId(), next.challenge().id(), verifyOperationId, secondProof);

// 整体 COMPLETED 后才能单次领取完成结果。
AuthCompletion completion = service.consume(
        invocation, verified.transactionId(), verified.completionId());
```

示例省略应用对各状态的分派，不能无条件顺着调用。首次交互型认证用 begin(...)；下一因素已有完整证明用 authenticateNext(...)。state(...) 读取公开状态，cancel(...) 取消流程。

policy、binding、已有 evidence 都是可信服务端输入；客户端只能提交应用选定的证明/定位字段。IP、地区、设备、请求次数等可以在本次 data 中提供，由策略决定是否使用。

AuthResult 只公开状态/挑战/完成标识/原因；不要向客户端返回内部 AuthTransaction 或 MethodContext。COMPLETED 与 AuthCompletion 同次存储提交，但不是 Bearer Token 或持久会话，不代表业务权限通过。

consume() 原子单次领取并清理库内证据/操作记录，返回证据给应用。它不与付款、外部系统或调用方会话写入原子提交，不承诺业务 exactly-once。消费结果不明应查询 consumed，不得假设业务可以安全重复。

## 可选会话与操作凭据

开启 `java-impetus.auth.credentials-enabled=true` 注册 AuthCredentialService、CredentialTokens 和默认 KEEP 的 TokenRotationPolicy；应用同类型 Bean 覆盖默认实现。不用 Spring 时直接构造，原四参数构造器默认 KEEP，五参数构造器接受轮换策略。未开启时不改变原来的认证/consume 流程。不提供 JWT、Cookie/Header 提取、HTTP 登录接口或默认角色/权限。

```java
// result 必须是 COMPLETED。选择兑换凭据时，不要先调用 authentication.consume()。
IssuedCredential issued = credentials.issueSession(invocation,
        result.transactionId(), result.completionId(), issuanceOperationId, Duration.ofHours(8));
String sessionToken = issued.token();

// realm 由可信服务端选择；Token 通过应用自己的受保护传输取得。
AuthCredential session = credentials.validateSession(sessionToken, "main");
credentials.revoke(sessionToken, "main");
```

- `SESSION`：可反复校验，显式 renewSession 可续期；读取不延长期限，撤销后立即拒绝后续校验。没有自动滑动续期或 Refresh Token。
- `OPERATION`：只用于原认证绑定的身份/realm/用途/操作/发起端，原子单次消费，不能作为登录会话使用。
- `AuthCompletion` 仍只是可兑换的认证完成结果。兑换在同一存储记录中**原子消费完成结果并建立凭据/签发回执**，不是先 consume 再单独写会话。只有兑换提交成功才存在会话。
- 首次兑换前重新评估 FINAL；容量满、期限失效或提交明确失败均不消费完成结果，不淘汰其他未过期凭据。
- 一份完成结果只能兑换一份凭据。相同 issuanceOperationId、completionId、类别、TTL 和 maximumLifetime 在事务保留期内确认同一凭据/Token，不延长有效期；改变内容或换签发标识拒绝。已轮换的会话不能通过最初签发回执取得后来生成的 Token，返回 VERSION_CONFLICT。
- 提交成功但响应丢失，使用原签发标识重试确认回执，不重开认证。已提交回执的恢复不重新运行策略，不代表账户状态或业务权限仍允许操作；已撤销/过期凭据不会重新激活。
- 事务/签发回执在原完成结果的保留期限结束后清理，已签发会话继续按自己的期限生效。超过回执期限不能通过旧完成结果重新领取 Token。
- `AuthCredential` 只提供可信身份/验证事实，不授予角色或业务权限。其数据是只读快照，不保证业务调用与后续撤销原子串行。

已有会话追加认证：

```java
AuthBinding operationBinding = new AuthBinding("main", null,
        "payment", serverBoundOrderIntent, validatedInitiator);
AuthInvocation stepUp = credentials.invocation(sessionToken, operationBinding, "paymentPolicy", businessContext);
AuthResult challenge = authentication.begin(stepUp, startOperationId, null);
// 按 ACTIVE/CHALLENGE/PENDING 完成后续验证，取得 completed。
IssuedCredential permit = credentials.issueOperationCredential(stepUp,
        completed.transactionId(), completed.completionId(), issuanceOperationId, Duration.ofMinutes(2));

// 必须提供已验证身份及相同的完整操作绑定。
CredentialUse use = credentials.consumeOperation(permit.token(),
        stepUp.binding(), consumeOperationId);
if (use.replayed()) {
    // 已消费请求的恢复确认：查询应用自己的业务幂等结果，不再次执行付款。
} else {
    // 执行业务；凭据单次消费不等于外部业务 exactly-once。
}
```

invocation() 保留原始 verifiedAt、原验证用途/操作，不把登录时的因素伪装为本次操作的验证。`within(...)` 仍可能要求重新验证，`operation()` 不接受旧操作的事实。stepUp 的 data 原对象只在调用内传递，不进入凭据/存储。

操作消费提交响应丢失时，原 consumeOperationId 在 `retention-ttl` 内返回 `replayed=true` 和已提交事实；新消费标识拒绝。同操作重试不是第二份业务执行许可，也不能恢复调用方尚未执行的外部业务。需与业务自身的幂等结果/事务或 outbox 协作。消费回执在固定保留期后清理，不因读取续期。

### Token 与密钥

CredentialTokens 生成带公开事务定位部分的 opaque reference Token；秘密部分是专用密钥的 HMAC-SHA256，轮换使用独立代次改变秘密。服务端仅保存完整 Token 的 SHA-256 摘要，校验使用恒定时间摘要比较，不保存明文 Token。UUID 定位字段、completionId、credentialId 均不是可直接使用的凭据。稳定密钥用于恢复已提交代次的 Token，不把 Token 变成可脱离存储校验的 JWT。

默认随机专用密钥与本地存储同生命周期。共享/可重启存储须提供各实例一致的 CredentialTokens/AuthKeyRing Bean，或配置固定的 `redis.credential-key`；它与 ProofFingerprint 密钥是两个独立用途。无匹配历史密钥的恢复报 CREDENTIAL_KEY_MISMATCH，不返回无法使用的新 Token。

```java
@Bean
CredentialTokens credentialTokens() {
    // 从应用的秘密管理设施读取至少 32 字节密钥，勿写死在源码/日志中。
    return new CredentialTokens(applicationSecret);
}
```

固定密钥继续使用上面的构造器或 `AuthKeyRing.fixed(secret)`；动态管理时提供一个线程安全的 `AuthKeyRing` Bean，`current()` 返回活动 `AuthKey`，`resolve(keyId)` 返回对应历史密钥或 null。`AuthKey(id, secret)` 要求至少 32 字节，复制密钥内容且不提供秘密 getter；密钥来源、缓存、更新、定时轮换、KMS 接入和退役时间由用户决定，库不保存另一份 key ring。所有实例必须使用一致的 keyId/密钥，ID 不可复用于不同秘密。Spring 装配优先使用应用的 `CredentialTokens` Bean；没有该 Bean 时，优先采用 `AuthKeyRing` Bean，再使用固定密钥配置或 local 模式的随机密钥。

签发一次选择一个 key，`keyId` 与摘要、凭据一起原子提交；恢复根据已保存 keyId 重新生成相同 Token，不采用当时最新的 key。KEEP 保留原 keyId，ROTATE 才用活动 key 和新代次，且续期回执恢复仍用已提交的选择。活动 key 切换不会自动轮换所有存量会话。普通 opaque Token 校验走权威存储摘要，不遍历密钥：删除旧 key 会阻止需要它的签发/续期恢复，但**不是撤销会话**，登出仍走 revoke。

历史 key 至少保留到引用它的有效凭据不再需要续期、相关回执不再需要恢复；过早删除会使续期或恢复失败。已消费签发的精确确认不再选择活动 key 或准备候选凭据，只按原回执恢复。

ProofFingerprint 继续使用独立稳定的密钥，认证事务/幂等窗口内不能直接替换；本 key ring 管理凭据派生密钥，不替换操作指纹算法，也不处理应用的 TLS、WebAuthn 或 TOTP 密钥。

Token 的保密传输、客户端存储、防窃取、请求限流由应用/所选协议负责；本库不强制设备、IP、地区检查。不可把整份 AuthInvocation 或内部 StoredCredential 返回客户端。IssuedCredential.toString() 隐藏 Token，但应用显式记录 token() 或序列化完整返回值仍会暴露秘密。

会话安全背景：[OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)。

### 业务信息边界

客户端 Token 是 opaque reference，不携带用户 JSON/角色/任意 claims。服务端 AuthCredential 记录 id、类别、身份与操作绑定、验证事实及时间、有效期和状态；AuthCompletion/completionId 只是认证完成结果/定位标识，不是可直接使用的登录 Token。

`AuthCredential.attributes()` 是可选业务快照，默认 null。Spring 使用方可提供 `CredentialAttributesProvider` Bean；构造器组装时传入 `AuthCredentialService` 的六参数构造器。Provider 接收可信 invocation、最终 completion 与凭据类别，在存储锁外取值，和首次签发一起原子持久化。已消费完成结果的恢复不再调用 Provider；并发首次签发可能各自取值，首个成功提交的快照胜出，重试不能覆盖它。

```java
record SessionAttributes(String tenantId, long accountVersion) { }

@Bean
CredentialAttributesProvider credentialAttributes(ApplicationAccounts accounts) {
    return (invocation, completion, kind) -> accounts.sessionAttributes(completion.binding().subject());
}

@Bean
RedisAuthStateCodec authRedisStateCodec() {
    return new RedisAuthStateCodec(Map.of("session-attributes-v1", SessionAttributes.class), 65536);
}
```

ApplicationAccounts 是用户自己的服务；返回紧凑不可变 POJO/Map，不固定租户、设备等字段。库不深复制业务对象，用户须保证本地快照不被后续修改。Redis 自定义 POJO 复用稳定类型 ID 白名单与聚合大小限制；未登记或超限时签发回滚，不消费完成结果。Provider 可因并发/未提交失败重复调用，不能用于不可重试的外部写入。

续期/轮换保留初次 attributes；撤销、过期清除它，操作凭据消费保留它用于固定期限内的结果确认，记录清理时一起释放。不自动更新 attributes 或把 invocation.data 放进去。动态权限/账户禁用仍查询当前可信数据，不依赖长期凭据中的旧快照；无需持久快照时继续按 subject/session.id 关联应用数据。`credentials.invocation(..., businessContext)` 的 data 仍只在本次调用内传递。

### 显式会话续期与 Token 轮换

续期时长由应用服务端决定，是否允许账户/设备续期也由应用在调用前校验。本库只保证凭据资格、绑定、期限及原子一致性；不能直接把客户端提交的 Duration 当授权。

```java
// 可选最大生命周期从首次签发时间计算；未传时不设置固定的累计上限。
IssuedCredential initial = credentials.issueSession(input, completed.transactionId(),
        completed.completionId(), issuanceOperationId, Duration.ofHours(1), Duration.ofDays(7));

IssuedCredential renewed = credentials.renewSession(initial.token(), "main",
        renewalOperationId, Duration.ofHours(2), businessContext);
// 不需要业务上下文时使用四参数重载。
// 应用始终使用返回的 token()；是否发生轮换由策略决定。
```

- 仅有效 ACTIVE SESSION 可续期；过期/撤销不能复活，OPERATION 不续期。没有过期 Token 换新 Token 的通道；需要重新认证并签发。
- 新到期时间为 `max(原 expiresAt, 提交时间 + ttl)`，有 maximumLifetime 时再限制到首次签发时确定的绝对上限。不是在旧到期时间上累加；纯续期不会缩短原有效期。初始 maximumLifetime 必须不小于初始 TTL，不能在续期时重置。
- 会话 id、createdAt、身份/用途/操作绑定、原始 verifiedAt 全部保留。续期不刷新密码/MFA 的验证年龄，不授予新的权限，也不替代追加验证。
- 续期与轮换分开：KEEP 只续期，ROTATE 同时改变 Token 代次，稳定的会话 id 不变。成功轮换后旧 Token 立即不能正常校验、消费、撤销或发起新的续期；不默认提供双 Token 宽限期。

```java
@Bean
TokenRotationPolicy tokenRotationPolicy() {
    return context -> Duration.between(context.tokenIssuedAt(), context.now())
            .compareTo(Duration.ofMinutes(30)) >= 0
            ? TokenRotationDecision.ROTATE : TokenRotationDecision.KEEP;
}
```

策略可以读取当前凭据、Token 签发时间、绝对上限、本次 TTL、当前时间及原始 data；不接收明文 Token。data 只在当次调用传递，不复制、不序列化、不缓存。策略在存储锁/事务外运行，并发同操作可能各自评估，首个成功提交决定结果，因此不应在策略中提交不可重复的业务副作用。策略抛异常/null 时不提交续期。普通 validateSession 不调用策略，不自动轮换；活动派生密钥选择使用前文的 AuthKeyRing，与 TokenRotationPolicy 是独立配置。

续期先读快照/回执，评估策略，再原子复核当前版本和 ACTIVE/期限/绑定，最后一次提交期限、摘要、代次与回执。提交时已经撤销/过期则拒绝；不同操作竞争同一快照时不会覆盖新状态。KEEP 的旧版本竞争返回 VERSION_CONFLICT；ROTATE 后旧摘要发起新操作返回 INVALID_CREDENTIAL。应用决定是否重新获取当前有效凭据再发起新的业务请求，不自动重跑策略或提交异常。

提交成功但响应丢失时，在 `retention-ttl` 内用**原 Token、原 renewalOperationId 和原 TTL**确认同一已提交操作，返回相同的新 Token/到期时间，不重新评估策略或再次延长期限。旧摘要只用于这条精确回执的确认，不是第二个有效 Bearer；无需开启通用宽限期。回执仍对应当前版本时也可用返回的新 Token 确认；改变 TTL 返回 OPERATION_CONFLICT，后续续期/轮换已改变版本则返回 VERSION_CONFLICT，不恢复过时结果或借旧回执取得最新 Token。进入撤销/过期终态时清除续期回执。

恢复窗口仍是受保护的凭据交互：持有原 Token 且知道原操作标识/TTL 的一方可能取回新 Token，operationId 不是第二因素，不能当作防窃取保障。应用应选择合适的短回执期限、保护传输与操作绑定；本库不默认加入 IP/设备条件，也不宣称消除被窃取 Bearer 的风险。

每个会话只保留最近 `retention-ttl` 内、最多 `maximum-renewal-receipts` 条紧凑回执；包含操作标识/TTL、原摘要、提交版本和回执到期时间，不包含完整业务 data 或历史明文 Token。到期条目在后续成功续期中清除，整个凭据清理时一起释放；普通校验不扫描回执或为它续期。限额满时拒绝新操作，不淘汰尚在去重窗口内的回执。去重不是永久历史记录：每笔新续期必须使用新的操作标识，超过回执期限不要拿旧标识当新请求重新提交。多次并发普通校验得到的是当时快照，不保证后续业务与撤销/轮换原子串行。

## 可选临时验证码、扫码确认与 Passkey

三个桥接位于 `method.code`、`method.scan`、`method.passkey`，共享 `method.challenge.ChallengeProvider<P>`：

| 桥接 | 默认 methodId / 初始交互 | 用户实现 |
| --- | --- | --- |
| OneTimeCodeAuthenticationMethod<P> | code / CHALLENGE | 随机验证码生成、摘要/引用验证、短信/邮件发送与账户级限流 |
| ScanAuthenticationMethod<P> | scan / PENDING | 已认证确认方、显式批准/拒绝、原始请求绑定和防重放 |
| PasskeyAuthenticationMethod<P> | passkey / CHALLENGE | 委派成熟 WebAuthn 验证器，管理 RP/origin、公钥凭据与注册 |

`ChallengeProvider.prepare(context)` 只准备 `PreparedChallenge(publicPayload, privateState, ttl)`；`verify(context, proof)` 返回现有 MethodResult；`dispatch(committedContext)` 默认无操作，库只在挑战提交后调用。准备、验证、通知可重复/并发调用，仍遵守 AuthenticationMethod 的幂等契约，库不持有外部 SDK/请求。跨请求只保存明确准备的紧凑协议状态，终态清除；不得保存完整用户对象、明文验证码或 WebAuthn 私钥。

```java
@Bean
AuthenticationMethod<MyAssertion> passkey(MyWebAuthnProvider provider) {
    // MyAssertion / MyWebAuthnProvider 是应用自己的类型，后者实现 ChallengeProvider<MyAssertion>。
    return new PasskeyAuthenticationMethod<>(MyAssertion.class, provider);
}

@Bean
AuthenticationMethod<String> emailCode(MyCodeProvider provider) {
    return new OneTimeCodeAuthenticationMethod<>("email-code", String.class, provider);
}
```

不用 Spring 时把这些实例放入 MethodRegistry。它们没有默认 Bean、隐式 Enable 或必走节点；只有应用显式注册且策略选中时才执行。默认方式名可通过三参数构造器自定义。策略继续用原有 ALL/ANY、请求 data 动态选择；例如 `any(method("email-code"), method("passkey"))` 不要求两种都运行。

三种桥接都是 challenge-first：先 begin/beginNext，再 verify；不能直接提交 proof 绕过挑战准备。库复用原有归属、期限、总尝试次数、替换、幂等回执和原子提交；同事务完成后不能再次领取，重新发码不会重置尝试次数。跨事务/第三方服务的一次性消费、防重放和请求/账户限流仍由 provider 的权威实现负责，不能仅凭库的同事务去重视为已解决。

扫码不生成二维码、不安装 polling/WebSocket/移动端接口；看到扫码或客户端 success 标志不是通过验证。Passkey 不手写密码学或解析器、不强制依赖 Security：已使用 Security 时复用其 [WebAuthn 能力](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)，其他应用选择自己的成熟验证器。Provider 必须检查 challenge、origin、RP ID、凭据所属身份、签名、UP/UV 等 [WebAuthn 验证要求](https://www.w3.org/TR/webauthn-3/#sctn-verifying-assertion)，再返回真实 AuthEvidence；注册、账户恢复和个人凭据存储仍由用户处理。

## 密码认证

组件位于 `method.password`，不要求框架用户实体或 UserDetailsService：

- `PasswordCredentialProvider.find(context, account)`：按可信 `context.binding().realm()` 查询账号，返回 `PasswordCredential(AuthSubject, encodedPassword)`。账号不存在、禁用或不允许密码登录时返回 null；账号归一化、数据模型和账户资格由应用决定。不接收密码，可以读取本次原始 `context.data()`。
- `PasswordVerifier.encode()/matches()`：编码与比对，方便应用注册/改密时使用同一格式；不负责写用户数据库。应用可以提供自己的 Bean，优先于所有默认实现。
- `PasswordAuthenticationMethod`：默认 id 为 `password`，只返回因素验证事实。不会自动生成会话、授予角色/权限或跳过后续挑战。已知身份时只允许验证同一 subject；错误 realm/身份与密码错误均拒绝。

使用 `@EnableAuth`，应用只需提供 `PasswordCredentialProvider` Bean，库便注册默认校验器和密码方式；没有 provider 时不注册密码能力，避免假装已经具备用户查询实现。默认 Bean 与可选适配均让位于应用实现，并记录不含密码/账号的初始化日志。

```java
@Bean
PasswordCredentialProvider passwordCredentials(UserAccountService accounts) {
    return (context, account) -> {
        // UserAccountService 是应用自己的服务；不要求特定用户表/基础类。
        var user = accounts.find(context.binding().realm(), account);
        if (Objects.isNull(user) || !user.passwordLoginAllowed()) return null;
        return new PasswordCredential(
                new AuthSubject(context.binding().realm(), user.id()), user.passwordHash());
    };
}

@Bean
AuthenticationPolicy passwordLogin() {
    return context -> AuthDecision.require(AuthRequirement.method("password"));
}
```

```yaml
java-impetus:
  auth:
    default-policy: passwordLogin
    password:
      method-id: password
      challenge-ttl: 1m
      pbkdf2-iterations: 600000
      pbkdf2-maximum-iterations: 2000000
```

```java
// 注册/改密：应用保存返回的带盐编码，不保存明文；不经过认证事务。
String passwordHash = passwordVerifier.encode(newPassword);

// 登录：binding/initiator/业务意图由可信服务端构造，proof 只在本次调用存在。
AuthResult result = authentication.authenticate(invocation, operationId,
        "password", new PasswordProof(account, submittedPassword));
// 仅整体 COMPLETED 后，应用显式 consume 或 issueSession；ACTIVE 则按策略继续下一因素。
```

默认 `Pbkdf2PasswordVerifier` 直接调用 JCA 的 `PBKDF2WithHmacSHA256`，随机 16 字节盐、32 字节派生值，60 万次迭代。编码为 `{impetus-pbkdf2-sha256}$迭代数$URL-Base64盐$URL-Base64派生值`；不是 Spring PBKDF2 的编码格式，也不自动尝试其他格式或旧明文/MD5。比对采用编码中的迭代数，超过配置的上限、编码损坏或类型不支持均不匹配。参数变化不会自动重写数据库；密码更新由应用负责。构造器可直接用于不依赖 Spring 的核心。

默认参数对应 [OWASP 的 PBKDF2-HMAC-SHA256 建议](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html#pbkdf2)，不代表默认 JCA 提供者具备 FIPS 认证。OWASP 优先推荐 Argon2id；需要它或应用既有编码时提供校验器/下面的 PasswordEncoder。生产中按硬件评估工作因子和吞吐；测试使用的低迭代参数不能作为生产建议。不会 trim、截断、归一化或添加固定公共密钥；密码格式/注册规则由应用决定。

### 可选 Spring Security 密码适配

应用自行引入 `spring-security-crypto`（或已有 Spring Security 依赖）并提供 `PasswordEncoder` Bean：在没有自定义 PasswordVerifier 时，库自动使用 `PasswordEncoderVerifier`，不再启用原生 PBKDF2 默认实现。仅有类依赖、没有 PasswordEncoder Bean 时仍使用 JCA 默认实现。这个密码适配器只负责密码比对，不装配 FilterChain、AuthenticationManager、UserDetailsService 或授权规则；已提供的身份/授权、方法保护与完成发布接入见相应 Security 小节，Web 登录协议和会话持久化仍由应用负责。

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

编码原样交给应用的 Encoder，算法/参数/`{id}` 由它决定；库不转换格式。未知编码 id 或编码器异常遵循 Encoder 行为，作为 `METHOD_FAILED` 返回服务端，不伪装成验证通过或盲目回退。多个 Encoder/Provider Bean 时应用需指定 Spring 的 `@Primary`/单一候选。参考 [Spring Security 密码存储与 PasswordEncoder](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)。

### 失败、重试与数据边界

- 缺失/禁用账号、错误密码、身份绑定不匹配统一返回 `invalid-credentials`，沿用全事务尝试限制。provider/编码器抛异常属于基础设施失败，原操作可以按现有恢复规则重试，不变成错误密码。应用公开响应还应避免泄露内部异常。
- 方法构造时生成一次随机密码的 dummy hash；缺失/不允许的账号也做一次密码比对，减少明显的“未找到账号直接返回”耗时差。不是恒定时延承诺：查询、哈希工作因子和实现差异仍可能影响时间。方法实例只保存 provider、校验器、配置和该固定 dummy hash，不保存账号/证明。
- 通常直接 `authenticate`，不需要额外请求。显式 `begin` 仅产生 `{prompt: password}`，没有密码、账号或密码哈希的 privateState；提交时重新查询当前凭据，支持现有挑战到期/替换语义。
- `PasswordProof` 的密码在公开 Jackson JSON 中是 WRITE_ONLY，`PasswordCredential` 的密码哈希不公开序列化，两者 `toString` 脱敏。内部 HMAC 指纹仍包括真实密码，修改密码不能冒充同一已提交操作。库不打印证明或存储原密码/用户哈希，只保存必要事实与带密钥指纹。
- PBKDF2 临时字符/派生缓冲区用完清除；输入 String 由调用方持有，Java String 不可擦除，不承诺移除调用方/日志系统自行持有的密码。业务 data 不应放密码/整份请求，provider 不得把它们写进自己的缓存。
- 不默认引入账号/IP/设备锁定规则，也不宣称事务级 maximum-attempts 等价于全局抗爆破。应用应在入口/策略配置跨事务限流、账户资格、传输保护及需要的多重挑战；内置 TOTP 之外的 MFA 协议由应用接入。

## TOTP 第二因素

组件位于 `method.totp`。`LocalTotpVerifier` 用 JCA HMAC 实现 [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238)，支持 SHA1/SHA256/SHA512、6/8 位及每份凭据的周期，起点为 Unix epoch。默认 SHA1、6 位、30 秒，接受当前及前一个时间步，不接受未来时间步；窗口可配置为 0～10 步，扩大窗口会增加可猜测范围。默认生成 20/32/64 字节随机密钥，导入密钥至少 128 位。提供者要与客户端使用一致参数，并验证客户端兼容性。

### 应用提供凭据，而非供应商 SDK

```java
@Bean
TotpCredentialProvider totpCredentials(AccountTotpRepository repository) {
    return (context, credentialId) -> {
        // subject 已由密码、可信会话或应用适配器确认；不能从客户端自报 userId 构建。
        AuthSubject subject = context.binding().subject();
        AccountTotp active = repository.findActive(subject.realm(), subject.id(), credentialId);
        if (Objects.isNull(active)) return null; // 示例业务类型由应用定义
        return new TotpCredential(
                new TotpCredentialKey(subject, active.id(), active.version()),
                applicationDecrypt(active.encryptedSecret()),
                new TotpParameters(TotpAlgorithm.SHA1, 6, 30));
    };
}
```

`TotpCredentialProvider.find(context, credentialId)` 只查询本次可信身份的当前可用凭据，null 表示不存在/禁用；可读取原始 `context.data()`。credentialId 只是可选的客户端选择器，null 由应用选择默认凭据。更换密钥/算法/周期时递增服务端 version；库不缓存 provider 的返回对象。多个 provider 时由应用指定单一候选或 `@Primary`。

兼容标准 TOTP 的认证器使用同一算法，不需要逐个接 SDK。远程推送、厂商 API、扫码或专有协议应实现现有 `AuthenticationMethod<P>`；不要求供应商暴露 secret，也不强制走本地 `TotpVerifier`。TOTP 不是抗钓鱼协议，不能替代 Passkey/WebAuthn 等机制；应用需结合真实风险选择。参考 [OWASP MFA](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)。

`@EnableAuth` 且有 provider 才注册默认 verifier、usage store 和认证方式；用户相应类型的 Bean 覆盖默认实现。无需 Spring 时直接构造这些组件；本地 usage store 生命周期结束需 `close()`。method id 默认 `totp`，策略通过 `AuthRequirement.method("totp")` 接入，不默认强制所有用户启用。

```yaml
java-impetus:
  auth:
    totp:
      method-id: totp
      challenge-ttl: 1m
      past-steps: 1
      future-steps: 0
      maximum-credentials: 10000 # 有活跃使用回执的凭据/修订数量，不是用户表大小
      maximum-receipts: 32       # 每份凭据/修订的有界时间步回执
```

算法、位数、周期固定在服务端 `TotpParameters`，不是随证明传入的可变参数。`TotpProof(String code, String credentialId)` 只带验证码和可选凭据选择器；单参数构造器选择默认凭据。保留前导零，不 trim、不接受 Unicode 数字，也不把验证码当数字解析。

```java
// first 是密码已通过但还需要 TOTP 的 ACTIVE 事务。
AuthResult result = authentication.authenticateNext(invocation,
        first.transactionId(), verifyOperationId, "totp", new TotpProof(submittedCode));
// 也可 beginNext 后 verify。仅整体 COMPLETED 才 consume/issueSession。
```

无可信 subject 时不会查询凭据或通过 TOTP，不将它作为匿名账号查找/第一因素。显式 begin 仅公开 `{prompt: totp}`，privateState 只有 credentialId/version；verify 重新查询当前凭据，并校验已绑定身份和原凭据修订，换绑后的旧挑战不能通过。挑战/事务期限、尝试次数仍遵守核心规则，不因 TOTP 恢复而延长。

### 绑定辅助：不生成二维码

```java
String secret = TotpSupport.generateSecret();
String uri = TotpSupport.provisioningUri("My App", "alice@example.com", secret);
// URI 含密钥，只在受保护的绑定交互交给本人，不记录日志或当公开认证挑战返回。

TotpMatch confirmed = new LocalTotpVerifier().verify(secret,
        TotpParameters.defaults(), firstSubmittedCode, serverClock.instant());
if (Objects.nonNull(confirmed)) {
    // 应用原子确认自己的 pending enrollment，存储加密密钥并启用新修订。
}
```

`otpauth://...` URI 按通行的 [Key URI Format](https://github.com/google/google-authenticator/wiki/Key-Uri-Format) 编码。auth 只生成密钥与 URI，不返回二维码图片、不引入 ZXing/toolkit；应用自行决定展示形式及是否引入二维码工具。不同客户端不一定支持全部可选参数，不能仅凭 URI 生成成功认定兼容。

`verify` 只计算/检查当前验证码，**不代表已原子消费验证码或完成凭据启用**。pending enrollment 的期限、首次确认幂等与单次生效、密钥加密存储、换绑/撤销/备份恢复码、账户资格由应用负责；不能直接把未确认密钥作为默认活动凭据。`generate` 只用于协议/绑定测试，不作为向不可信方提供 OTP 的接口。

### 原子防重放与恢复

- `TotpUsageStore.find/consume` 按可信 subject/credentialId/version 隔离，原子占用时间步并保留紧凑回执，阻止不同认证事务/操作重复使用同一步。只做普通 get/set 或依赖认证事务版本，无法跨事务防重放。
- 原 transactionId/stageId/operationId 和原带密钥证明指纹精确匹配，才能确认同一已提交使用；变更内容拒绝。恢复先查回执，再检查新验证码窗口，所以 OTP 已提交但认证推进/响应失败时，可在核心仍允许该操作的期限内重试。保留原 verifiedAt，不伪装为新的因素认证，也不延长回执/挑战/事务。
- 默认回执保留至少 `transaction-ttl + retention-ttl`（默认 7 分钟），并至少覆盖该时间步完整接受窗口；过期后不恢复。不能拿过期操作标识重新提交新证明。已过期/禁用/换绑凭据、已失效的核心挑战/事务仍拒绝，不因有回执而重新激活。
- OTP 使用与认证事务推进是两个提交边界，不宣称跨记录/用户数据库事务 exactly-once。已提交的 OTP 不会在认证提交异常时“释放”；未知提交结果只以原操作恢复确认，不盲目换操作再消费。
- `InMemoryTotpUsageStore` 适用于单实例同生命周期；读取逻辑检查期限，过期元数据在后续写入/容量准入或显式 `purgeExpired()` 时清理，总量有界，无额外后台线程。`close()` 释放全部记录。容量/单凭据回执满时拒绝，不淘汰未过期回执。
- 选择 `auth.store=redis` 时默认使用 `RedisTotpUsageStore`，复用应用 RedissonClient 和 namespace，原生事务/CAS 原子提交回执、TTL 和容量登记，不自写 Lua。共享模式缺客户端/共享 store 明确失败，不自动回退 local。自定义 shared store 需自己满足原子性/期限/容量契约。
- Redis 模式复用稳定的 `ProofFingerprint`/`redis.proof-key`，各实例密钥/编码一致；直接构造多实例方式时必须显式传入同一稳定指纹器，不能使用三参数构造器的随机本地指纹密钥。Redis 的主库读取、同槽、时钟同步、不可淘汰权威存储及复制故障转移限制同下节。
- `TotpCredential.secret` 不公开 JSON，`TotpProof.code` 为 WRITE_ONLY，toString 脱敏，内部记录不保存原密钥或验证码，只保存必要标识/时间/指纹。临时字节清除不意味着能擦除调用方 String；应用仍要保护 URI、secret 及自己的日志。
- 事务尝试限制不等于全局防爆破；跨事务限流、资格、设备/IP 等规则由应用选择和配置，不在库里强制。

## 验证方式 SPI

```java
public interface AuthenticationMethod<P> {
    String id();
    Class<P> proofType();
    MethodResult begin(MethodContext context);
    MethodResult verify(MethodContext context, P proof);
    default void dispatch(MethodContext committedContext) { }
}
```

id 在注册期唯一，proofType 校验入参类型，只执行被本次选择的方式。

- begin 仅准备 CHALLENGE/PENDING 的 PreparedChallenge，提交后才 dispatch。
- verify 返回 VERIFIED(evidence)、CHALLENGE、PENDING 或 REJECTED(reason, terminal)。VERIFIED 只是因素通过。
- reason 使用稳定业务码，不放证明、密码或内部异常信息。
- evidence 保留真实验证时间、同一身份及本次用途/操作，不能取客户端的成功声明。
- dispatch 在提交后执行，异常不回滚已有挑战，也不代表认证验证失败。
- publicPayload/privateState 必须是紧凑不可变协议数据，不得保存整份请求/证明/可变业务对象；verify 不得原地修改保存状态。
- 实际凭证、协议必要校验、抗重放由方式负责；名为 totp/passkey 的 Bean 不会自动实现对应协议。
- begin/verify 的外部副作用需支持同一 operationId 的恢复重试；核心原子推进不等于外部系统 exactly-once。
- 方法/策略/指纹实例会并发复用，不能在 Bean 字段缓存当次请求。租约过期不会中断旧方式调用，恢复时可能重叠执行，由方式保证副作用幂等。

## 提交、重试与标识

| 标识 | 用途 |
| --- | --- |
| transactionId | 整个跨请求认证流程 |
| stageId | 内部阶段；同阶段替换挑战时保持 |
| challengeId | 当前具体挑战版本；替换后旧版本失效 |
| operationId | 一次准备、证明提交或重发动作的幂等标识 |

这些不是凭据；继续/查询/取消/消费均检查 realm、用途、操作、发起端和已知身份绑定。它们不能替代传输保护、协议证明或可信发起端验证。

同操作相同内容已提交时返回当前状态，不重复方式/发送；不同内容拒绝。执行中 IN_PROGRESS，竞争更新 VERSION_CONFLICT，使用原操作标识确认/重试。服务端在验证前原子登记尝试，成功/失败验证动作均计入整个事务限制，新操作累计，网络重试不重复计数；无客户端 retryCount。

仅保存带密钥的 HMAC 内容指纹，不保存证明或普通密码摘要。指纹使用独立的 Jackson 配置读取证明字段，忽略公开 JSON 的隐藏注解/有损格式；证明应为稳定 JSON 可表示的值，特殊类型使用自定义 ProofFingerprint。默认密钥只适用于本地服务/存储同一生命周期；默认序列化上限 16 KiB。共享存储必须使用实例间一致稳定的密钥/编码，不能使用各实例随机默认密钥。

通知失败后，同操作重试取得已提交状态，但不自动再次发送：

- resend(..., newOperationId)：显式重发原挑战，不重新 begin，保持 challengeId。
- replaceChallenge(..., newOperationId)：不能重发/挑战过期时，在原阶段创建新版本。旧挑战失效，不重置事务期限/尝试数。
- 明确提交失败不通知；结果不明先确认同一操作，不盲目重开。
- 进程在提交后、通知前退出可能未发送；本批无持久 outbox 或自动可靠投递。

终态不重新激活；终止清理挑战/证据，保留有限期限的元数据，重开使用新的发起操作标识。保留期后删除去重记录，不是无限期防重放表。

## 存储与生命周期

AuthTransactionStore 的 create/load/advance/purge 必须原子实现；advance 与 purge 以 expectedVersion 条件推进。持久实现返回前需完成独立提交，不能把待提交状态返回通知器。

### 主动弃用与记录清理

```java
authentication.discard(invocation, transactionId); // ACTIVE 或 COMPLETED 且尚未消费
authentication.purge(invocation, transactionId);   // 终态或已消费的 COMPLETED
// 登出/撤销是另一个动作：credentials.revoke(token, realm)
```

- `cancel` 保持原有 ACTIVE 限制；`discard` 明确表示不再使用本链路，返回 DISCARDED，清除挑战、公私 payload、证据、完成结果和操作回执。同一 DISCARDED 记录内重复调用幂等，不延长期限。已消费的结果拒绝弃用，不重新打开完成结果，不撤销已签发凭据。
- `purge` 是不可恢复的单条链路记录清理，不是全用户登出、清空 Redis 或删除全部历史。ACTIVE 或未消费 COMPLETED 必须先弃用。服务检查当前调用的归属/操作/策略绑定，后端原子检查版本和终态；并发消费、签发、验证或清理不会互相覆盖。
- 清理后 state/consume/再次 purge 返回 NOT_FOUND；不伪造一份“曾清理成功”的结果。提交异常可能已完成，先查状态；调用方不能把任意 NOT_FOUND 都当作本次清理的成功证明。
- 已签发会话/操作凭据、续期/消费回执及其原有期限独立保留，仍可校验、续期、消费和撤销；不触碰 TOTP 的独立防重放存储。清理会删除原签发领取回执，**签发响应尚未确认时不要 purge**，否则不能再用原完成结果恢复 Token。
- 本地与 Redis 均保留原发起标识的轻量 tombstone 到已有保留期限。窗口内重放同一发起标识不会创建新链路；需要新链路使用新的 operationId。它不保存证明、身份或业务 data，不承诺永久去重。tombstone 同样受 maximum-transactions 容量限制，主动 purge 不会释放这个去重配额；期限到达后回收。
- 没有业务回滚/补偿或“清理后重新使用已消费因素”的能力。用户数据库事务失败、签发 Token 后的业务失败不自动调用 discard/purge/revoke。记录清理是调用方明确选择，不是认证失败后的默认动作。

本地实现按事务更新；方式、策略与通知不在 Map 锁内调用。不会跨请求持有线程/Future/数据库事务或全局 ThreadLocal。整体期限、操作数/尝试数及总容量均有限；一条共享守护线程清理两份索引，读取也处理过期。终态/消费释放不再需要的数据，保留期后移除记录，close 停止清理并释放缓存。

本地存储同时实现可选 AuthCredentialStore；每条记录原子维护认证事务与其凭据，二者各有独立期限/容量。正常会话校验只读取不可变快照，只有状态变更/过期才更新。不会额外维护 Token 索引、缓存本次请求或为每个会话创建定时任务。事务清理后只保留必要凭据元数据/验证事实；撤销/过期立即清理事实，操作消费仅在有限回执期限内保留恢复所需事实。

自定义后端启用凭据能力时，必须由同一个 AuthCredentialStore Bean 同时承担事务与凭据原子操作；不支持两个独立存储拼接，缺少能力启动失败。AuthCredentialService 构造器也检查与 AuthenticationService 使用同一实例。

后端还需实现只读 `renewal(...)` 和原子 `renew(...)`，保留 StoredCredential 的版本、代次、Token 签发时间与可选绝对上限，并提供有界续期回执。不能用普通 get/set 拼接，也不能在锁中执行轮换策略；认证事务清理后仍需保留有效凭据及其续期回执。

本地仅用于单实例，不支持重启恢复/跨实例共享。自定义实现须保持版本、绑定、期限、终态与单次消费语义。

### 可选 Redis 原子适配

`RedisAuthTransactionStore` 同时实现 AuthTransactionStore 与 AuthCredentialStore，直接使用 **Redisson 原生 RTransaction、RBucket 条件更新、RSetCache**，不维护自写 Lua。认证事务与凭据保持同一聚合；版本、绑定、终态及资格判断复用本地实现的 AuthStoreRules。完成结果消费、凭据/回执写入、期限与容量登记在同一次 Redisson 事务中提交成功后才返回。

下游自行引入 Redisson，或引入 optional 的 java-impetus-redis 并提供该模块所需的 Redis 运行时依赖。复用使用方现有 RedissonClient；Auth 不创建/关闭客户端，不强制应用连接配置，不引入 Web/Security。开启示例：

```yaml
java-impetus:
  auth:
    store: redis
    credentials-enabled: true
    redis:
      namespace: application-prod  # 环境/应用隔离；同一集群的所有实例使用相同命名空间
      maximum-state-bytes: 65536
      proof-key: ${AUTH_PROOF_KEY_BASE64}
      credential-key: ${AUTH_CREDENTIAL_KEY_BASE64}
```

两个密钥应分别由秘密管理设施生成、提供，均为至少 32 字节的 Base64 编码原始密钥。ProofFingerprint/CredentialTokens 应用 Bean 可替代配置；应用 AuthKeyRing Bean 也可代替固定 credential-key。启用共享存储时没有相应稳定密钥明确启动失败，不能默认各实例随机生成。未启用凭据时不要求 credential-key。Redis 依赖或 RedissonClient 缺失、选择未知后端时明确失败，不回退本地；自定义 AuthTransactionStore Bean 优先。

默认支持 String、Boolean、Integer、Long、BigDecimal、byte[] 与 JSON 语义的 Map/List 数据。自定义 publicPayload/privateState/attributes POJO 应显式登记稳定类型 ID：

```java
@Bean
RedisAuthStateCodec authRedisStateCodec() {
    return new RedisAuthStateCodec(Map.of(
            "otp-state-v1", OtpPrivateState.class,
            "otp-prompt-v1", OtpPrompt.class), 65536);
}
```

各实例使用相同登记及协议版本。存储编码与公开 HTTP Mapper 隔离，保留 privateState、操作回执、要求树、原始时间与大整数精度；不会按数据中的类名加载任意 JVM 类。默认未使用多态 default typing。Map/List 为普通 JSON 容器，嵌套业务对象的确切 Java 类型需要使用显式登记的 POJO。Redis 是可信后端，内部私有状态不能作为公开 JSON 返回或写入日志；内容需紧凑不可变，默认单聚合上限 64 KiB。

当前内部存储 schema 为 4，包含凭据 keyId、显式类型 attributes、代次、续期回执和事务附加策略标识；只处理当前格式，不提供旧开发格式兼容或迁移。部署时所有实例使用一致的代码和存储格式；不同开发格式使用不同 namespace，不会自动删除应用 Redis 数据。

- **原子性边界**：Redisson 事务隔离级别为 READ_COMMITTED，写操作由其内置锁保护；本库先条件确认当前聚合，再检查当前版本与资格，绝不直接覆盖先前读取的快照。只有已知未提交的条件竞争做有限重读；Redis/网络/提交异常不会自动重跑验证方式或开启新流程。失败回滚不等于已提交事务的业务补偿，结果不明仍用原 operationId 查询/重试确认。
- **内存与锁**：不缓存当次请求或认证状态，不全量读取所有认证聚合。通常会话读取没有事务/锁。仅新事务/新凭据的容量准入使用两个独立短事务 gate；容量集合只记录 ID 和保留期限，满时拒绝而非淘汰已有凭据。RSetCache 的清理由 Redisson 管理，Auth 不再创建清理线程。
- **期限**：资格使用应用 Clock 与记录内绝对期限检查；Redis TTL 按写入时剩余时长设置，只负责毫秒级物理清理，不依赖 Redis 与应用绝对时钟相等，也不代替资格判断。网络/提交延时可能稍晚清理 key，但不能延长认证期限。应用实例之间的时间同步仍由环境保障，库不修改服务器时间或推测偏差；跨请求 TTL 应覆盖正常交互/网络耗时。事务、会话、消费回执分别清理；清理旧事务不会删除后来创建的同发起标识索引，读取/重试不延长会话。
- **主库读取**：单机默认满足；Cluster/Sentinel/主从/Replicated 模式必须配置 `ReadMode.MASTER`，否则启动拒绝，以免从滞后副本接受已撤销会话。不会修改共享客户端的全局配置。
- **部署边界**：同 namespace 的 key 带相同 hash tag，以保证 Cluster 同槽事务，因此一个 namespace 集中于一个 slot，不宣称自动跨槽分片。Redis 应作为不可任意淘汰的权威存储，配置合适的内存、ACL、TLS、持久化和备份；Redisson 事务不等于外部业务事务，也不能消除 Redis 异步复制故障转移中的数据丢失窗口。

参考：[Redisson 原生事务与隔离级别](https://redisson.pro/docs/transactions/)、[Redis Cluster 同槽与复制限制](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/)。

## Spring 生态复用与领域边界审计

本次方法接入同时检查了配置、规则、密码/TOTP、完成处理、凭据、本地与 Redis 存储的生态边界：

| 部分 | 复用与保留理由 |
| --- | --- |
| 方法执行/注解解析 | 使用 Spring AOP / Security 原生拦截器、AnnotationMatchingPointcut、组合/桥接解析及共享代理器；不维护独立 Method 上下文或方法执行引擎。PolicyRegistry 基础选择统一为 Spring 解析，类型选择识别 JDK/CGLIB 代理的目标类型但执行仍经原 Bean 代理 |
| 配置与 Bean 生命周期 | 现有 ConfigurationProperties、条件装配、ObjectProvider 和 Spring 销毁机制即可；不另建容器、配置中心或 Bean 注册表 |
| 密码/外部登录 | 已有 PasswordEncoderVerifier 委派给应用 PasswordEncoder；LDAP/OIDC 等优先交给应用已有 Security 协议设施，映射实际验证事实。无 Security 时的 JCA 默认实现保留；其 PBKDF2 存储格式不是 Security Encoder 格式 |
| TOTP | 保留本地 RFC 6238 与 provider SPI；不是 Spring 一次性链接/验证码登录的替代名字，不为每个认证 App 重写客户端 |
| Redis 与本地权威状态 | Redis 复用 Redisson 原生事务、条件写与 TTL。本地容量/期限、领域版本/重放回执/单次领取是认证协议所需，不能以普通 Cache 或 Spring 数据库事务等价替换；本地唯一 sweeper 生命周期由 Spring 管理 |
| 完成交接/通知 | 显式选定 handler 和提交后 dispatch 保持既定协议；普通 ApplicationEventPublisher 可供应用发布旁路通知，但不能替代单次消费与明确领取，也不保证可靠投递或外部业务 exactly-once |
| 路由与会话 | 规则已复用 AntPathMatcher 且不拦截 HTTP；已有 Security 请求匹配、上下文保存和 Session 生命周期由应用沿用，Auth 不再建第二套 Web Filter/Session 机制。只有确有需求才启用本模块独立凭据 |

方法接入审计没有发现必须整体替换的另一套 Spring 容器/代理/事务实现；较多状态代码主要来自跨请求挑战、原子版本、消费和恢复边界，不能只为缩短代码删除。Security 方法适配器通过 ObjectProvider 和 Spring SingletonSupplier 懒取得并保留单例 Bean 引用，不对 final 类创建懒代理，也不每次重新查询 Bean；不缓存本次输入、Authentication 或判断。没有引入事件总线、通用流程引擎、热更新或新的运行时数据缓存；当前存储格式见 Redis 小节。

## 测试

自动方法接入另覆盖 NATIVE 无 Security/Web/Redis/AspectJ、显式模式/缺依赖/自定义顺序/消费者 Advisor 覆盖、真实 Spring 代理与自调用边界、组合/类/接口泛型注解，以及标准 PreAuthorize/PostAuthorize/PreFilter/Secured/JSR-250 的独立和叠加行为。Security-only 方法不进入 Auth，即使配置全局必需策略或缺少 Auth 输入/身份 mapper。默认顺序与真实 Spring 事务 Advisor 共用一个代理器，拒绝不进入业务事务。NATIVE 和 SECURITY 各 300 个独立虚拟线程请求、每次 5 次调用，验证原 data、当前框架上下文与动态检查不串用。

覆盖规则/证据范围、归属隔离、提交失败/响应丢失、通知重试、挑战替换、过期/取消、尝试/容量/索引清理、并发消费和 200 个独立并发事务，以及无 Redis/Security/Web 类的 Spring 启动和消费者 Bean 覆盖。

凭据覆盖原子签发/消费、响应丢失恢复、冲突/容量/期限、撤销、篡改/归属/类别隔离、真实验证时间与追加认证；50 请求同凭据竞争、100 轮消费/撤销与兑换竞争，以及 5 批各 200 个并发独立会话和双生命周期清理。

续期覆盖 KEEP/ROTATE、可选绝对寿命、原验证事实/调用 data、策略及提交失败、响应丢失、旧 Token 精确回执与过时结果拒绝、撤销/过期、有限回执容量/期限、稳定密钥与 Spring Bean 覆盖。并发覆盖 50 同操作确认、KEEP/ROTATE 各 50 不同操作的旧版本竞争、100 次续期/撤销竞争，以及 5 批各 200 个独立会话、每个会话 3 次轮换和最终清理。

密码覆盖真实 JCA/应用 Encoder 比对、盐与工作因子、Unicode/长密码/空字符不截断、损坏/不支持/超限编码、统一失败、realm/身份绑定、原业务 data、幂等与密码变更冲突、provider 故障恢复、动态追加 MFA/会话身份追加认证、50 个共享方法的独立并发请求，以及公开 JSON/内部存储不泄露密码。Spring 装配覆盖 provider 缺失、无可选类、应用 Encoder/校验器/方式覆盖与无效配置；Redis 编码恢复用例无需真实 Redis，不能算真实后端验证。

TOTP 覆盖 RFC 三种 HMAC 的全部 18 个标准向量（含 2038 年之后）、时间步边界/漂移、前导零/错误输入、Base32/绑定 URI、真实密码后 TOTP、凭据归属/换绑/私有挑战、跨事务防重放、OTP 与认证提交响应丢失后恢复，以及原 verifiedAt/期限不变。100 不同操作竞争、100 同操作确认和 10 批容量/清理验证覆盖本地有界原子存储；Redis 原生操作/编码测试不能替代真实后端。

访问检查覆盖四种决策、公开/拒绝入口、ALL/ANY 权限组与全局策略、可信身份/原 data/证据时间、权限不足不可由 MFA 修复、provider/策略故障、消费者 Bean 覆盖，以及共享服务下 500 个独立并发请求、各重复 5 次检查。外部登录交接用例覆盖无需注册外部方式的已验证因素、外部登录后真实 TOTP、MFA 期间权限撤销、应用自定义证明 SPI、已有外部会话无需二次签发，以及只读检查不消费操作凭据。这些是交接边界测试，不等于已实现或联调 LDAP/OIDC 协议客户端。

Security 薄适配覆盖匿名/未认证/remember-me、不推断因素/时间/角色、未知身份拒绝、realm/subject/事实一致性、原 data/实际时间/操作范围、四种结果及默认拒绝异常保留完整信息、公开入口的 supplier 惰性、无全局 SecurityContext 修改，以及外部身份接真实 TOTP 后仅由应用显式提供新事实才放行。500 个独立并发请求各重复 5 次，验证共享桥接/manager 不缓存调用数据。Spring 覆盖无 Security/Redis/Web 核心启动、无 mapper 不装配、应用 adapter/resolver 覆盖及无默认 FilterChain；不是 Servlet 过滤链或 LDAP/OIDC 协议联调。

完成处理覆盖原 invocation/data、当前线程调用、可信完成事实、重复/过期/错误归属与动态 FINAL 检查、消费故障/丢失确认、handler 失败不回滚、不缓存返回值，以及与凭证签发互斥。100 同结果竞争只交付一次，100 独立并发调用随后清理存储事实。Security 完成覆盖新 context 不修改旧对象、应用权限选择、实际 TOTP 时间/原事实、映射/发布故障、自定义 strategy/trust resolver 和 100 个默认 ThreadLocal 隔离调用；Spring 覆盖可选依赖缺席、消费者覆盖、独立 mapper 条件及 handler Bean 不自动执行。这些不包含 HTTP 会话保存或外部业务事务恢复。

弃用/清理覆盖 ACTIVE/COMPLETED/已消费/各终态、归属/版本、提交失败与响应丢失、同发起标识去重及有界 tombstone、凭据/续期/消费回执独立存活、真实 TOTP 防重放不重置、100 次弃用/消费竞争和在途 provider 的迟到提交。规则覆盖多路径/HTTP 方法、首个命中、默认访问、ALL/ANY 组合、启动校验、实现/接口/泛型桥接/组合注解、本地/路由/全局策略不会丢失，以及 500 个独立并发调用各检查 5 次不串 data。Security 规则入口仅解析一次规则，保留追加认证决策且 PUBLIC 不读取身份。

```shell
mvn -pl java-impetus-auth -am "-Dtest=Auth*Test,RedisAuthStringCodecTest" -Dsurefire.failIfNoSpecifiedTests=false clean test
```

默认用例无需数据库或 Redis，覆盖本地回归、存储编码精度/类型、原生事务提交与回滚、条件竞争、容量失败、回执不刷新 TTL、可选装配/稳定密钥/主库读取。

真实 Redis 组合与竞争验证在 `AuthRedisIntegrationTest`：双客户端跨实例挑战恢复、并发唯一发起/签发/消费、容量与独立期限、清理隔离、版本/绑定及损坏后端拒绝，以及跨实例续期/轮换、原子竞争、实际 TTL 延长和已提交响应丢失恢复；另覆盖 TOTP 双实例原子占用/恢复不刷新 TTL、密码后 TOTP 挑战往返与跨事务防重放，显式弃用/清理后的去重、独立 Token 续期/撤销，以及 attributes/keyId 跨实例提交/恢复、未登记类型回滚、应用/Redis 时间偏差下的物理与逻辑期限。设置 `IMPETUS_AUTH_REDIS_URL=redis://host:port` 后运行上述命令；认证可通过 `IMPETUS_AUTH_REDIS_USERNAME`/`IMPETUS_AUTH_REDIS_PASSWORD` 提供。未设置地址时这 19 项明确跳过，不算实际 Redis 验证通过。每项只写入并清理新 UUID 命名空间，从不 FLUSHDB；请使用专用测试 Redis。桥接测试使用 provider stub，只验证接入契约，不等于已联调短信、扫码或 WebAuthn；具体协议与 Security Web 会话/过滤链接入由用户决定，外部业务恢复不属于 auth 范围。

扩展回归覆盖三种方式的可选注册、challenge-first、跨调用归属、原提交回执恢复、过期不调用 provider、通知失败后显式重发、替换不重置次数；凭据覆盖固定/活动 key、历史 key 恢复与退役、KEEP/ROTATE、初始 attributes、终态清理、类型白名单/大小和 50 请求并发签发/轮换中的快照一致性。

源码、测试源码与 JAR/source/Javadoc 产物验证使用以下命令，不触发 verify 阶段的签名或发布；`-DskipTests` 不代表测试已运行：

```shell
mvn -pl java-impetus-auth -am -DskipTests package
```

使用方 skill 可独立复制 [java-impetus-auth](../.agents/skills/java-impetus-auth/SKILL.md) 的整个目录，包含基础接入、规则、清理、凭据及 Security 边界说明。
