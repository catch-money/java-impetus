# java-impetus-auth

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Java 21](https://img.shields.io/badge/Java-21-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

Policy-driven authentication infrastructure with optional Spring integration. It provides authentication stages, atomic state storage, password/TOTP factors, credential lifecycles and access checks.

It does **not** provide a user/RBAC schema, controllers, Servlet filters, a default SecurityFilterChain or an identity-provider protocol server. The old auth/auth-impl design is not compatible with 2.0; auth-impl has been removed.

## Capabilities and application responsibilities

| Capability | Provided here | Application-owned |
| --- | --- | --- |
| Policies and access | ALL/ANY factors, dynamic policies, route rules, method checks | Trusted identity/data, permissions, optional device/IP/region rules |
| Verification | Password, local RFC 6238 TOTP, method SPI and optional challenge bridges | Credential sources, providers, registration, delivery and protocol verification |
| Completion and credentials | Explicit completion handoff, optional opaque session/operation tokens, renew/rotate/revoke | Which handoff to use, business data, renewal authorization and key management |
| Storage | Bounded local store and optional atomic Redis adapter | Deployment, namespace, shared keys, time synchronization and durability |
| Spring Security | Optional identity/authorization bridges, method interceptor and explicit completion publication | Existing login protocols, filter chains, HTTP responses and Web session persistence |

Device/IP/location checks are mechanisms applications may add through policies, not mandatory library fields or steps. Registering a verification method does not make every request execute it.

## Dependency and activation

```xml
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-auth</artifactId>
    <version>2.0.0</version>
</dependency>
```

Java 21 and Spring Boot 4 for Spring integration. JSON uses java-impetus-jackson/Jackson 3; keyed proof fingerprints use crypto. Redis and Security dependencies are optional; the core, local storage, JCA password verifier and TOTP work without Redis, Security or Web. No toolkit/QR dependency is required.

```java
@Configuration(proxyBeanMethods = false)
@EnableAuth
class ApplicationAuthConfiguration {
    @Bean
    AuthenticationPolicy loginPolicy() {
        return context -> AuthDecision.require(AuthRequirement.all(
                AuthRequirement.method("password"),
                AuthRequirement.method("totp", EvidenceReuse.operation())));
    }
    // Supply PasswordCredentialProvider and TotpCredentialProvider beans separately.
}
```

```yaml
java-impetus:
  auth:
    default-policy: loginPolicy
    required-policies: []
    store: local
    maximum-transactions: 10000
    transaction-ttl: 5m
    retention-ttl: 2m
    operation-lease: 15s
    maximum-attempts: 5
    maximum-operations: 32
    credentials-enabled: false
    maximum-credentials: 10000
    maximum-renewal-receipts: 32
```

@EnableAuth explicitly imports configuration; there is no global auth AutoConfiguration.imports entry or implicit Web protection. Consumer beans override defaults. Registration logs do not include proofs or business data.

Default method protection is NATIVE. Set java-impetus.auth.method-security.mode=DISABLED if you only want explicit/core entry points.

Without Spring, assemble services, registries and stores through constructors. Close InMemoryAuthTransactionStore when its lifecycle ends. Reusable services/structures do not hold per-call request data.

## Core contracts and policy composition

The central interfaces are AuthenticationPolicy, AuthRequirement and AuthenticationMethod<P>.

- PASS adds no requirement; it is not authentication success or business authorization. An unknown identity cannot complete authentication solely because a policy passes.
- REQUIRE combines ALL and ANY in declaration order without expanding all combinations.
- DENY cannot be overridden by another policy.
- Registered/current requirements can grow but are not weakened later.
- Policies are reevaluated for CONTINUE/FINAL and before consuming a completion.
- evaluate reads the current original data object and can run repeatedly; do not perform non-idempotent sends/payments there.

EvidenceReuse.session accepts trusted, still-valid session evidence; within(duration) uses the actual verifiedAt; operation additionally matches purpose/operation. Identity and future-time checks still apply.

The application must validate external sessions and dynamic account status. AuthCredentialService.validateSession/invocation can validate this module's sessions, but the returned facts are a current snapshot, not authorization permanently valid until expiry.

@UseAuthPolicy selects method > class > global default, using Spring interface/composed/bridge annotation resolution. Global required-policies and invocation.requiredPolicies append mandatory constraints; a local selection cannot remove them.

## Trusted input and staged interaction

The package root is io.github.jockerCN.auth, with policy, authorization, completion, security, method, transaction, credential, store, annotation and config subpackages.

```java
AuthBinding binding = new AuthBinding(
        "main", null, "login", serverOperationId, validatedInitiator);
AuthInvocation invocation = new AuthInvocation(binding, "loginPolicy", businessContext);

AuthResult first = authentication.authenticate(
        invocation, startOperationId, "password", passwordProof);

// Only when ACTIVE and no current challenge:
AuthResult next = authentication.beginNext(
        invocation, first.transactionId(), nextOperationId, null);

AuthResult verified = authentication.verify(
        invocation, next.transactionId(), next.challenge().id(),
        verifyOperationId, secondProof);

// Only after the whole policy returns COMPLETED:
AuthCompletion completion = authentication.consume(
        invocation, verified.transactionId(), verified.completionId());
```

This is a sequence sketch, not unconditional code: dispatch based on each returned state. Use begin for challenge-first interaction; authenticateNext when the next factor already has a complete proof. state reads public state; cancel applies to active transactions.

realm, subject, binding, policy and evidence are trusted server inputs. Do not deserialize an entire client-supplied AuthInvocation. Bind operation to the actual intent/critical parameters; validatedInitiator is not an arbitrary unverified header.

AuthResult exposes status and locating fields, not the internal transaction/private MethodContext. COMPLETED is not a bearer token, a persistent session or proof of business permission.

## Access checks and business authorities

AuthAccessService.check(invocation, requirement) is the common decision engine for native and Security integration. It does not parse HTTP, validate a token, create/send a challenge, consume an operation credential or execute business work.

Supply current authorities through an application AuthorityProvider:

```java
@Bean
AuthorityProvider authorities(ApplicationAuthorityService application) {
    return context -> {
        AuthSubject subject = context.binding().subject();
        return new AuthAuthorities(application.roles(subject), application.permissions(subject));
    };
}

AuthAccessRequirement requirement = AuthAccessRequirement.authenticated(
        AuthorityRequirement.rolesAny("ADMIN", "BUYER"),
        AuthorityRequirement.permissionsAll("order:read"));

AuthInvocation trusted = credentials.invocation(
        sessionToken, operationBinding, "orderPolicy", businessContext);
AuthAccessDecision decision = access.check(trusted, requirement);
```

ApplicationAuthorityService is an application type, not a required model.

| Decision | Meaning |
| --- | --- |
| ALLOWED | Current identity, authorities and factor policies satisfy this check |
| UNAUTHENTICATED | No trusted identity |
| DENIED | Explicit denial, insufficient business authority or policy denial |
| AUTHENTICATION_REQUIRED | Identity/authority allowed, but factors/freshness/binding need more verification |

MFA cannot grant missing business permissions. rolesAll/rolesAny/permissionsAll/permissionsAny compose each group appropriately; distinct groups are ANDed. Roles and permissions are separate case-sensitive literal values, with no automatic ROLE_ prefix, wildcard or hierarchy.

The default AuthorityProvider grants nothing. It is not called when no authorities are required. The order is identity/evidence → current business authority → FINAL policies. Provider/policy failures or null/unknown configuration throw; never catch them and grant access.

publicAccess bypasses this declaration's requirements only; it cannot remove protection from a later independent method check. It cannot be combined with authority/policy constraints. deny always rejects.

For step-up, begin using the **same trusted invocation, selected policies and operation binding**, not a newly built weaker requirement tree. After completion, refresh trusted facts and recheck current business permission. A past ALLOWED result is not a reusable permission token.

## Batch route rules

```yaml
java-impetus:
  auth:
    default-access: authenticated
    default-policy: normalPolicy
    required-policies: [accountPolicy]
    rules:
      - paths: [/public/**, /health]
        methods: [GET]
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

Rules use Spring AntPathMatcher, are case-sensitive and choose the **first configured match**, not the most specific or a merged union. Empty methods means any method. Unmatched access defaults to authenticated; configure another default explicitly.

Invalid combinations/paths/methods or unknown policies fail at startup. No regex path variables are exposed. Supply an application-normalized absolute path matching the actual routing rules, without context path/query/fragment. The library does not decode URLs or normalize semicolons/dot segments for you.

```java
AuthAccessRule route = requestRules.resolve(normalizedPath, requestMethod);
AuthInvocation prepared = route.apply(trusted);
AuthAccessDecision decision = access.check(prepared, route.requirement());
```

Route policy is an appended requirement, not a replacement for global/default policy. Carry the same requiredPolicies through later stages and consumption. Registries retain declarations, not request instances, results or permission caches. There is no HTTP registration or automatic challenge response.

## Spring method protection

### NATIVE mode

Spring AOP MethodInterceptor/Advisor handles library annotations without Security or AspectJ. Provide one trusted-input adapter:

```java
@Bean
AuthMethodInvocationProvider methodInputs(ApplicationAuthService application) {
    return call -> application.trustedInvocation(call.getMethod(), call.getArguments());
}

@Service
class OrderService {
    @AuthAccess(permissionsAll = "order:write")
    @UseAuthPolicy(OrderPolicy.class)
    public void update(OrderCommand command) {
        // Application business work.
    }
}
```

OrderPolicy is an AuthenticationPolicy bean. ApplicationAuthService owns trusted identity, realm, factors, operation and original data.

```yaml
java-impetus:
  auth:
    method-security:
      mode: NATIVE   # NATIVE / SECURITY / DISABLED; never inferred from the classpath
      order: 250
```

Only library-annotated methods/types match this advisor. Unannotated or Security-only methods are not widened into Auth protection by default/required global policies.

PUBLIC/DENY does not request identity inputs. Missing provider/mapper or provider failure is an error, not a bypass. No per-invocation cache or custom ThreadLocal method-context stack is maintained.

Standard proxy limits apply: no self-invocation, private/unproxyable methods or arbitrary new objects. Final classes can use a suitable JDK interface proxy; otherwise do not rely on class-based interception. It shares Spring's proxy infrastructure rather than installing another proxy engine.

A consumer advisor named authMethodSecurityAdvisor overrides the selected default. Configurable ordering must account for Security prefilters, transactions and other advisors.

### SECURITY mode and native Security annotations

Select mode=SECURITY and supply SecurityIdentityMapper plus AuthMethodInvocationProvider. The library uses AuthorizationManagerBeforeMethodInterceptor with a thin manager, not a second NATIVE advisor. Missing Security core fails startup; no silent fallback.

Enable Security's own @PreAuthorize/@PostAuthorize and other annotations in application configuration with @EnableMethodSecurity and its corresponding options. The library does not impose security-config/Web.

| Method declaration | Checks |
| --- | --- |
| Only library annotations | Selected Auth advisor |
| Only Security annotations, no library class default | Security only |
| Both applicable annotation sets | Both advisors; every applicable check must pass |
| Neither | Not implicitly intercepted by this library |

PUBLIC cannot override another Security check. PostAuthorize is post-invocation protection, not automatic pre-business checking or rollback.

Security denial retains AuthSecurityDecision in AuthorizationDeniedException, including AUTHENTICATION_REQUIRED. Missing Security Authentication follows Spring's native error. No context update, completion consumption or challenge creation happens here.

### Explicit method checks

For non-proxy integration, AuthMethodAccessService.check/verify remains available:

```java
Method method = OrderService.class.getMethod("update", OrderCommand.class);
AuthAccessRule route = requestRules.resolve(normalizedPath, requestMethod);
methodAccess.verify(trusted, OrderService.class, method, route);
```

Use the business implementation type, not the generated proxy class. Method > class access/policy declarations resolve through interfaces, inheritance and bridge/composed annotations. Route and method authorities are ANDed; PUBLIC only exempts its own layer.

verify throws AuthAccessDeniedException unless ALLOWED. Explicit unannotated-method checks remain authenticated by default; this does not broaden the automatic advisor's matching. POJO use outside the protected method boundary is not automatically guarded.

## External login protocols

Password factors, login protocols and authorization are related but different. isAuthenticated or an external login callback is not proof that every local factor/permission rule is satisfied.

| Scenario | Integration |
| --- | --- |
| Own password login | PasswordCredentialProvider + PasswordVerifier |
| LDAP/OAuth2/OIDC already verified elsewhere | Map trustworthy identity and actual evidence into AuthInvocation |
| Existing session/resource-server bearer | Validate externally first, then check access; no second token is required |
| OAuth2/OIDC identity provider | Use an authorization-server implementation, not this module's opaque token |

Mature protocol stacks own signature/issuer/audience/redirect/request-correlation checks. Map actual verifiedAt and original purpose/operation; never label every request as newly verified password/TOTP. If only identity is known, carry no invented factor evidence.

An external first factor plus pending TOTP is still an incomplete login. The application must not publish unrestricted Security/Web login merely because the external callback succeeded. The thin bridge does not change the framework's default login-success/session behavior.

## Optional Spring Security bridges

The security package depends optionally on spring-security-core, not Servlet/security-web/FilterChain infrastructure.

### Identity and authorization

@EnableAuth registers AuthSecurityAdapter only when Security exists and an application SecurityIdentityMapper bean is present. Mapping is explicit; there is no guess based on principal.getName or UserDetails.

```java
@Bean
SecurityIdentityMapper identityMapper(ApplicationSecurityMapping application) {
    return (authentication, invocation) -> {
        AuthSubject subject = application.subject(authentication, invocation.binding().realm());
        if (Objects.isNull(subject)) return null;
        return new SecurityIdentity(subject, application.verifiedEvidence(authentication));
    };
}
```

The mapper returns identity/actual facts, not replacement policies or business data. Null, unauthenticated and anonymous objects do not reuse an old template identity. Remember-me can identify a subject but does not imply password/TOTP evidence.

Mapped realm/subject/evidence must agree. Existing and mapped facts retain their original times/bindings. GrantedAuthority/ROLE_/FACTOR_* strings are not automatically inferred as factor evidence or split into roles/permissions. AuthorityProvider remains the current business-authority source.

```java
AuthAuthorizationManager<ApplicationOperation> manager =
        securityAdapter.authorizationManager(
                operation -> applicationInvocation(operation),
                AuthAccessRequirement.authenticated(
                        AuthorityRequirement.permissionsAll("order:read")));
```

Dynamic requirement functions and ruleAuthorizationManager are also available. PUBLIC/DENY avoids calling the Authentication supplier. The manager returns a non-null AuthSecurityDecision and only ALLOWED grants access. Applications wire it into their own Security entry points; no FilterChain is installed.

Adapters are reusable/read-only and do not cache Authentication, data or decisions. They do not consume credentials or change SecurityContext.

## Explicit completion handoff

There is no automatic end/onCompleted callback. begin/verify can return COMPLETED with completionId only after all requirements, FINAL policy and storage commit succeed. That creates a completion record, **not a signed-in session or issued token**.

Choose one handoff:

1. consume directly for the trusted completion.
2. AuthCompletionService.complete with one explicitly selected handler.
3. AuthCredentialService issuance for this module's token.

They are mutually exclusive uses of the same completion.

```java
@Bean
AuthCompletionHandler<ApplicationLoginResult> loginCompletion(ApplicationLoginService application) {
    return context -> application.accept(
            context.completion().binding().subject(),
            context.completion().evidence(),
            context.invocation().data());
}

ApplicationLoginResult result = completions.complete(
        trusted, completed.transactionId(), completed.completionId(), loginCompletion);
```

The order is ownership/FINAL/expiry checks → atomic consume → synchronous selected handler. Handler code is outside storage locks/transactions. No discovery of all handlers, callback-result cache or background completion engine is added.

Use completion.binding/evidence as the final trusted facts; the original invocation may still lack the newly verified subject/factors. The context retains the original data reference only for this call. Handlers must not save it in shared fields.

### Failure boundary

No successful consume means no handler call. An unknown storage result requires state confirmation, not blind replay.

After consume commits, a handler exception propagates without undoing consumption, retrying business work or revoking credentials. A lost response/process exit cannot prove whether external business work ran. complete is a one-time handoff, not reliable delivery or external exactly-once. Already-issued-token business failures are entirely outside this authentication lifecycle.

### Security completion implementation

A SecurityCompletionMapper bean conditionally enables SecurityCompletionHandler. It is an implementation of the same explicit handoff, independent of the identity-reading mapper.

```java
@Bean
SecurityCompletionMapper completionMapper(ApplicationSecurityMapping application) {
    return (context, previous) -> application.authentication(
            context.completion().binding().subject(),
            context.completion().evidence(), previous);
}

SecurityContext published = completions.complete(
        trusted, completed.transactionId(), completed.completionId(), securityCompletionHandler);
```

The mapper returns a valid nonanonymous authenticated object based on final trusted identity/current authorities. It must not mutate the old authentication or invent factor times.

The handler creates and publishes a new SecurityContext on the current thread using the selected holder strategy. This is **not Web session persistence**. Applications use SecurityContextRepository and their own login/session fixation strategy for cross-request persistence. The library does not manage Servlet/Reactor contexts or save sessions automatically.

Mapper/publication failures do not undo consume. A bad mapper result does not publish a replacement context.

## Optional session and operation credentials

Enable java-impetus.auth.credentials-enabled=true to register AuthCredentialService, CredentialTokens and a default KEEP rotation policy. Without this option, existing consume/handoff behavior remains unchanged.

```java
IssuedCredential issued = credentials.issueSession(invocation,
        completed.transactionId(), completed.completionId(),
        issuanceOperationId, Duration.ofHours(8));
AuthCredential session = credentials.validateSession(issued.token(), "main");
credentials.revoke(issued.token(), "main");
```

Do not consume the completion first. Issuance atomically consumes it and creates the credential/receipt before returning the token.

- SESSION supports repeated validation and explicit renewal. Reading does not extend expiry; no implicit sliding renewal or refresh token.
- OPERATION is bound to the original realm/subject/purpose/operation/initiator and consumed once.
- FINAL policy runs before first issuance; failed capacity/expiry/commit does not consume completion.
- Exact issuance retries use the original operation/completion/type/TTL/lifetime and confirm the same token without extending it.
- Receipt recovery does not rerun current authorization; revoked/expired credentials are not revived.
- Transaction/issuance receipts and credential expiry are independent.
- AuthCredential is a trustworthy fact snapshot, not a grant of business roles/permissions.

For step-up, credentials.invocation validates a session while preserving true factor times/original binding. Complete missing factors, then issueOperationCredential if needed.

consumeOperation(token, binding, operationId) returns CredentialUse. replayed=true confirms the original committed consume, **not a second permission to run a payment**. The external operation requires its own idempotency/transaction coordination.

### Token format, key ring and attributes

Tokens are opaque references with public locating data and a secret derived through HMAC-SHA256. The store retains a SHA-256 digest, not a plaintext token; validation uses constant-time digest comparison. They are not JWTs and cannot be validated independently of authoritative storage.

Default random credential keys are only for the matching local lifecycle. Shared/restartable stores need consistent CredentialTokens/AuthKeyRing or a configured credential-key. ProofFingerprint uses a separate key/purpose.

```java
@Bean
CredentialTokens tokens() {
    return new CredentialTokens(applicationSecret); // At least 32 bytes, from secret management.
}
```

AuthKeyRing.fixed supports a fixed key; an application ring supplies current() and resolve(keyId). AuthKey copies the secret and exposes no secret getter. Do not reuse an ID for another secret. All instances need consistent historical key mappings.

Issuance records the selected key ID. Recovery uses that key, KEEP retains it, ROTATE selects the active key. Switching the active key does not automatically rotate every session. Retiring a key can block recovery/renewal but **does not revoke an existing opaque token**; use revoke.

CredentialAttributesProvider optionally supplies a compact immutable business snapshot on first issuance. It receives trusted invocation/final completion/kind outside locks. Concurrent candidates can be evaluated repeatedly; the first committed snapshot wins. Do not perform irreversible writes there.

```java
record SessionAttributes(String tenantId, long accountVersion) {}

@Bean
CredentialAttributesProvider attributes(ApplicationAccounts accounts) {
    return (invocation, completion, kind) ->
            accounts.sessionAttributes(completion.binding().subject());
}

@Bean
RedisAuthStateCodec codec() {
    return new RedisAuthStateCodec(
            Map.of("session-attributes-v1", SessionAttributes.class), 65536);
}
```

The store does not deep-copy arbitrary application objects. Renewal preserves attributes; expiry/revocation clears them; operation-consume recovery keeps them only for its bounded receipt period. Dynamic authorization must read current data, not old attributes. invocation.data is never persisted implicitly.

Protect tokens and transport in the application. IssuedCredential.toString hides the token, but logging token() or serializing its full value still exposes it.

### Explicit renewal and rotation

```java
IssuedCredential initial = credentials.issueSession(input,
        completed.transactionId(), completed.completionId(),
        issuanceOperationId, Duration.ofHours(1), Duration.ofDays(7));

IssuedCredential renewed = credentials.renewSession(
        initial.token(), "main", renewalOperationId,
        Duration.ofHours(2), businessContext);
```

The optional maximum lifetime is anchored to first issuance; it cannot be reset during renewal. The server chooses/authorizes TTL, not an untrusted client Duration.

New expiry is max(old expiry, commit time + requested TTL), capped by the absolute lifetime. It is not added to old expiry. Renewal keeps identity, session ID, createdAt and actual factor times. Expired/revoked sessions cannot revive; operation credentials cannot renew.

```java
@Bean
TokenRotationPolicy rotationPolicy() {
    return context -> Duration.between(context.tokenIssuedAt(), context.now())
            .compareTo(Duration.ofMinutes(30)) >= 0
            ? TokenRotationDecision.ROTATE : TokenRotationDecision.KEEP;
}
```

The policy runs outside storage locks and may be evaluated by competing requests. It reads the current credential and original call data, not plaintext token; keep it repeatable. validateSession does not call it.

KEEP extends expiry without changing the token. ROTATE changes generation/digest and immediately invalidates the previous bearer; no default grace window exists. Renewal rechecks version, binding, eligibility and deadline atomically. Stale operations cannot overwrite newer state.

For a lost response, retry with the **original token, renewalOperationId and TTL** within retention-ttl. The precise receipt returns the same committed token/expiry without rerunning policy or extending expiry. An old digest is accepted only for that receipt, not as a second bearer. Changed content/conflicting versions are rejected.

Receipts are bounded by TTL and maximum-renewal-receipts; full capacity rejects new renewal rather than evicting an active replay window. A receipt is not a second factor or protection against a stolen bearer. Applications control transport, account eligibility and any device/IP rules.

## Verification method SPI and optional bridges

```java
public interface AuthenticationMethod<P> {
    String id();
    Class<P> proofType();
    MethodResult begin(MethodContext context);
    MethodResult verify(MethodContext context, P proof);
    default void dispatch(MethodContext committedContext) {}
}
```

Method IDs are unique; proofType validates the selected proof. begin prepares CHALLENGE/PENDING; only after commit is dispatch called. verify returns VERIFIED evidence, a new challenge/pending state or rejection. VERIFIED is one factor, not full-policy completion.

Public/private protocol state must be compact immutable data. Do not persist full requests, plaintext proofs or mutable user objects. Preserve actual verification time/binding; do not accept a client's “success” flag. Side effects must be recoverable/idempotent by operation ID; a lease expiring does not interrupt an old provider invocation.

Optional challenge-first bridges share ChallengeProvider<P>:

| Bridge | Default ID / initial state | Application supplies |
| --- | --- | --- |
| OneTimeCodeAuthenticationMethod | code / CHALLENGE | Code generation, digest/reference verification, delivery and global limits |
| ScanAuthenticationMethod | scan / PENDING | Trusted approving user/device, approve/deny and request binding |
| PasskeyAuthenticationMethod | passkey / CHALLENGE | Mature WebAuthn verifier, RP/origin/credential/registration handling |

No default beans are forced on applications. Register a bridge with its proof type/provider, then choose it in a policy. Custom method IDs use the corresponding constructor. prepare returns PreparedChallenge(publicPayload, privateState, ttl); verify returns MethodResult; dispatch is post-commit notification.

The module does not generate QR images, host SMS/email gateways, create polling/WebSocket endpoints or implement WebAuthn cryptography. Provider implementations own authoritative cross-transaction replay controls. The library's same-transaction receipts do not substitute for protocol one-time-use checks.

## Password authentication

PasswordCredentialProvider.find(context, account) returns PasswordCredential(subject, encodedPassword), or null for missing/disabled/ineligible accounts. The application owns its account schema/normalization. It receives no submitted password and may read the original call data.

@EnableAuth registers the password method/verifier only when a credential provider exists. A consumer verifier overrides defaults; the method returns factor evidence, not a session or permissions.

Default Pbkdf2PasswordVerifier uses JCA PBKDF2WithHmacSHA256, a random 16-byte salt, 32-byte output and 600,000 iterations. Default maximum accepted iterations is 2,000,000. The stored format is:

```text
{impetus-pbkdf2-sha256}$iterations$URL-Base64-salt$URL-Base64-derived-value
```

It is not Spring's PBKDF2 format and does not implicitly accept old MD5/plaintext or other encodings. matches uses the encoded work factor up to the configured cap. Password updates/rehashing belong to the application.

```java
String encoded = passwordVerifier.encode(newPassword);
AuthResult result = authentication.authenticate(invocation, operationId,
        "password", new PasswordProof(account, submittedPassword));
```

Application PasswordEncoder beans, with optional spring-security-crypto, use PasswordEncoderVerifier unless a custom PasswordVerifier already exists. Merely having Security classes does not select an encoder. Delegation does not add a FilterChain/AuthenticationManager/UserDetailsService.

Incorrect account/password/binding uses invalid-credentials. Provider/encoder failures are infrastructure failures, not successful checks or automatic algorithm fallback. A reusable dummy hash comparison reduces obvious missing-account timing differences without promising constant request time.

No trim/truncation/normalization or fixed public key is added. PasswordProof is WRITE_ONLY in public JSON; credentials hide hashes and toString values are redacted. Internal keyed fingerprints still include the real proof. Temporary buffers are cleared where possible; caller-owned immutable Strings cannot be erased.

Transaction attempt limits are not cross-account/IP anti-brute-force controls. Applications choose account eligibility, transport protection, rate limits and additional factors.

## Local TOTP

LocalTotpVerifier implements RFC 6238 through JCA HMAC-SHA1/SHA256/SHA512, 6/8 digits and credential-specific periods. Defaults: SHA1, six digits, 30 seconds, current and previous step accepted, no future step. Past/future windows allow 0–10 steps; larger windows widen guessing tolerance.

TotpCredentialProvider uses the already trusted subject and optional credentialId to fetch a current active credential. It owns enrollment, encrypted secret storage and eligibility. Increment server credential version when rebinding/changing parameters.

Defaults are only registered when a provider exists. TOTP is not anonymous account lookup/first-factor verification. Keep leading zeroes; proof codes are not trimmed or parsed as numbers, and only ASCII digits are accepted.

```java
AuthResult second = authentication.authenticateNext(
        invocation, first.transactionId(), verifyOperationId,
        "totp", new TotpProof(submittedCode));
```

Typical defaults:

```yaml
java-impetus:
  auth:
    totp:
      method-id: totp
      challenge-ttl: 1m
      past-steps: 1
      future-steps: 0
      maximum-credentials: 10000
      maximum-receipts: 32
```

### Enrollment helpers

```java
String secret = TotpSupport.generateSecret();
String uri = TotpSupport.provisioningUri("My App", "alice@example.com", secret);
TotpMatch match = new LocalTotpVerifier().verify(
        secret, TotpParameters.defaults(), submittedCode, clock.instant());
if (Objects.nonNull(match)) {
    // Application atomically confirms its own pending enrollment.
}
```

The URI contains the secret. Deliver it only in protected enrollment, never as public challenge/log data. The module returns a URI, not a QR image; applications can choose ZXing/toolkit separately.

Generated default secrets are 20/32/64 bytes by algorithm; imported secrets require at least 128 bits. Keep client/server parameters compatible. Local verify alone does not consume an OTP or activate an enrollment.

Standard TOTP clients do not require one SDK adapter per vendor. Proprietary push/remote protocols use AuthenticationMethod. TOTP is not phishing-resistant.

### Replay protection

TotpUsageStore atomically consumes a time step by trusted subject/credentialId/version, across authentication transactions. Exact original transaction/stage/operation/fingerprint can confirm a committed usage; changed content cannot.

Receipt recovery runs before new-code window checks, preserving true verifiedAt and existing deadlines. An OTP that was consumed but whose auth advancement failed is not “released”; recover with the original operation. Usage and auth-state advancement are separate atomic boundaries, not exactly-once across arbitrary databases.

InMemoryTotpUsageStore is bounded/single-instance and needs close; expiry cleanup occurs through admission/writes/purgeExpired, with no extra background thread. RedisTotpUsageStore uses the shared Redisson client/namespace and native atomic operations; shared mode does not silently fall back to local.

Shared fingerprint keys must be stable across instances. No raw TOTP secret/code is stored in usage receipts. Credential disable/revision changes and core expiry still reject even if a historical receipt exists.

## IDs, retry and notification

| ID | Scope |
| --- | --- |
| transactionId | Entire multi-request authentication |
| stageId | Logical factor stage |
| challengeId | One challenge version; replacement invalidates the old one |
| operationId | One idempotent prepare/verify/resend action |

IDs are locators, **not authentication credentials**. Every operation verifies ownership/binding and relevant policy IDs.

Exact committed retries return state without rerunning verification/sending; changed content conflicts. IN_PROGRESS/version competition uses the original operation for confirmation. Attempts are atomically registered before verification and counted across the whole transaction; retries do not consume another attempt. No client retryCount is trusted.

ProofFingerprint stores a keyed HMAC, not raw proof or an unkeyed password digest. The default isolated Jackson representation preserves proof fields despite public hiding/lossy JSON rules; use a custom fingerprint for special proof types. Default input limit is 16 KiB. Shared storage needs a stable secret/encoding across instances.

Notification failure does not undo commit. An exact retry retrieves committed state but does not automatically resend. Use resend with a new operation ID for the same challenge or replaceChallenge for a new version within the same stage. Neither resets overall expiry/attempts. There is no persistent outbox/reliable delivery guarantee.

## Discard, purge and storage lifecycle

```java
authentication.discard(invocation, transactionId);
authentication.purge(invocation, transactionId);
credentials.revoke(token, realm);
```

cancel remains ACTIVE-only. discard abandons ACTIVE or unconsumed COMPLETED transactions, clears challenge/evidence/completion/operation data and returns DISCARDED; repeat discard is idempotent within retention. It cannot reopen consumed results or revoke issued credentials.

purge irreversibly removes one terminal/consumed transaction. First discard active or unconsumed completed records. It is not a global logout or Redis flush. After deletion, state/consume/purge returns NOT_FOUND, not a synthetic success confirmation.

Issued credentials and their receipts/deadlines survive transaction cleanup. Purge removes issuance recovery, so do not purge before confirming a lost issuance response. TOTP replay storage is separate and is not reset.

A bounded initiating-operation tombstone remains until the original retention expires, preventing recreation with the same operation during that window. It consumes capacity until expiry, without retaining proof/request data. New flows use a new start operation ID.

InMemoryAuthTransactionStore uses per-record atomic updates, one shared daemon sweeper and bounded capacity. Policies/methods/notifications run outside map locks; no cross-request threads/Futures/database transactions/ThreadLocals are retained. Close releases records and stops cleanup.

Custom stores must implement atomic create/load/advance/purge, expected-version checks, expiry, ownership and one-time semantics. With credentials enabled, **the same store instance** must implement both AuthTransactionStore and AuthCredentialStore. Separate get/set backends cannot emulate atomic completion-to-credential issuance.

## Optional Redis adapter

RedisAuthTransactionStore implements transaction and credential storage using Redisson RTransaction, conditional bucket updates and RSetCache, not custom Lua. It returns only after native commit; version/eligibility checks share the same domain rules as local storage.

Add Redisson directly or java-impetus-redis with its required runtime starter. Auth reuses but does not create/close the client.

```yaml
java-impetus:
  auth:
    store: redis
    credentials-enabled: true
    redis:
      namespace: application-prod
      maximum-state-bytes: 65536
      proof-key: ${AUTH_PROOF_KEY_BASE64}
      credential-key: ${AUTH_CREDENTIAL_KEY_BASE64}
```

Use separate stable secrets of at least 32 raw bytes encoded as Base64. Application ProofFingerprint/CredentialTokens/AuthKeyRing beans can replace configured keys. Missing shared keys/client/capabilities fail; no random-key/local-store fallback.

The codec supports basic JSON-compatible values and explicitly registered POJO type IDs. It does not load arbitrary class names/default-typed payloads. Register matching type IDs on every instance; private protocol state is not public response JSON. The default aggregate limit is 64 KiB.

Current internal format is **schema 4** with keyId, typed attributes, generations, receipts and required policy IDs. Only that format is supported; different development formats use a different namespace. The library does not delete old application data automatically.

Deployment boundaries:

- Native Redisson READ_COMMITTED transactions protect writes; conditional/version checks prevent overwriting stale snapshots.
- Only known uncommitted condition competition gets bounded rereads. Network/commit exceptions do not automatically rerun factors.
- Application Clock/absolute record deadlines decide eligibility. Redis TTL handles relative physical cleanup; network latency can delay deletion but not extend logical validity.
- All application instances need synchronized clocks. The library does not adjust server time.
- Cluster/Sentinel/master-replica/replicated clients require ReadMode.MASTER for authoritative reads; the library does not mutate a shared client.
- Namespace keys share a hash tag for same-slot transactions; one namespace is not automatic cross-slot sharding.
- Redis is authoritative state, not an evictable convenience cache. Applications configure memory, ACL/TLS, durability and backups. Native transactions do not erase replication/failover loss windows or include external business transactions.

## Framework boundaries

Spring owns configuration binding, bean lifecycle, proxy interception and composed annotation resolution. Security owns its protocols and session/filter facilities. Redisson owns distributed transaction/conditional-write infrastructure.

Auth retains only authentication-domain requirements: stages, versions, leases, bounded receipts, single consumption and trusted factor evidence. It does not add a general workflow engine, config center, event bus, Web routing stack or business-compensation coordinator.

## Testing and safe integration

Local tests cover policy/rules, ownership, replay/recovery, capacity/expiry, factors, optional registration, actual Spring proxies, Security-only coexistence and concurrent calls. Protocol-provider stubs are contract tests, not SMS/WebAuthn/LDAP/OIDC interoperability certification.

```bash
mvn -pl java-impetus-auth -am "-Dtest=Auth*Test,RedisAuthStringCodecTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

For dedicated real Redis tests, set IMPETUS_AUTH_REDIS_URL and optionally IMPETUS_AUTH_REDIS_USERNAME/PASSWORD. Without a test URL the real-backend cases are skipped, not counted as validated. Tests use isolated UUID namespaces and do not FLUSHDB; use a dedicated test service.

Applications still validate their own identity mapping, HTTP integration, account rules, transport, secrets and external business lifecycle.

## Skills

Use the [java-impetus-auth skill](../.agents/skills/java-impetus-auth/SKILL.md) for application integration. Copy its **entire directory**, including references, to your project's `.agents/skills/`. See the [skills guide](../.agents/skills/README_EN.md) for downloading and personal installation.

```text
$java-impetus-auth Configure password and TOTP policies with local storage,
without enabling Spring Security or forcing device/IP checks.
```

Select the skill in Codex or explicitly mention its name. It does not install dependencies or enable authentication beans by itself, and is not an instruction to implement an identity provider or RBAC model.

## License

[MIT License](../LICENSE).
