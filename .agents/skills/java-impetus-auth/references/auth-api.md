# Auth 2.0 integration

Package root: `io.github.jockerCN.auth`. This module is infra, not an OAuth/OIDC authorization server, HTTP controller suite or user/RBAC schema. It installs the explicitly enabled method advisor but no default FilterChain or Web session mechanism.

## Spring and native assembly

`@EnableAuth` opts into configuration. Default beans back off to application beans and log INFO initialization without secrets. It does not select every factor: password/TOTP methods require their credential-provider beans; code/scan/Passkey methods are explicitly registered and selected by policy. Credentials stay disabled by default; the automatic method-advisor default is NATIVE. Policy bean names are referenced by Spring properties:

```yaml
java-impetus:
  auth:
    default-policy: loginPolicy
    required-policies: [accountPolicy]
    store: local
    transaction-ttl: 5m
    retention-ttl: 2m
    operation-lease: 15s
    maximum-attempts: 5
    maximum-transactions: 10000
    credentials-enabled: false
```

`AuthenticationPolicy.evaluate(AuthEvaluationContext)` returns `AuthDecision.pass()`, `require(AuthRequirement...)` or `deny(reason)`. Use `AuthRequirement.all`, `any` and `method(id, EvidenceReuse)`; selected requirements do not weaken later. PASS does not prove identity or authorize business. FINAL policies run before completion consumption/issuance; do not use evaluation for side effects.

Default selection is method > class > configured default. Global `required-policies` and invocation `requiredPolicies` remain mandatory. Dynamic device, IP, account and object decisions use the original `context.data()`. Store only necessary authentication facts, not application requests.

Without Spring construct `PolicyRegistry`, `MethodRegistry`, `AuthenticationService`, `AuthAccessService` and the selected store. Close local stores at lifecycle end. `InMemoryAuthTransactionStore` is a bounded single-instance authority, not an evicting cache; local restart loses sessions/state.

## Identity, factors and challenges

`AuthInvocation(binding, policy, evidence, data)` preserves compatibility; the five-argument constructor additionally accepts `List<String> requiredPolicies`. `AuthBinding` carries realm, optional subject, purpose, operation and trusted initiator. All come from a trusted application adapter. Method data is passed by reference and not persisted.

`authenticate(invocation, operationId, methodId, proof)` supports a direct proof such as password. `begin` prepares a challenge when needed; `verify` uses transactionId/challengeId/operationId. A missing next factor remains ACTIVE; use `beginNext` or `authenticateNext` on the same transaction. Only complete all required factors before claiming the completionId.

Reuse the same operationId/content after uncertain commit; changed content conflicts. `resend` explicitly retries delivery of a committed challenge using a new operationId. `replaceChallenge` replaces its challengeId without restarting the stage/deadline. No reliable outbox is included; a commit may succeed before dispatch or before a response is delivered.

- Password: application `PasswordCredentialProvider` selects current credentials; optional `PasswordVerifier` overrides JCA PBKDF2. When provided, Spring Security `PasswordEncoder` can be adapted. PasswordProof is not retained; production account/rate-limit rules remain with the application.
- TOTP: application `TotpCredentialProvider` supplies a subject-bound credential/secret/version. Local RFC 6238 verifier supports SHA1/SHA256/SHA512, 6/8 digits, configured period/window. Revisions isolate replacement credentials. `TotpUsageStore` enforces atomic bounded replay prevention; it is separate from transaction cleanup.
- `TotpSupport.generateSecret` and `provisioningUri` help enrollment; URI contains a secret, not a public challenge. QR generation and pending enrollment persistence are application choices.
- Other protocols use `AuthenticationMethod<P>`; external already-verified LDAP/OIDC/etc identities can be mapped into actual evidence without a fictitious local verification step. Use original verification times, not the current request time.

### Optional challenge-first bridges

`OneTimeCodeAuthenticationMethod<P>` (`method.code`, default ID `code`), `ScanAuthenticationMethod<P>` (`method.scan`, `scan`) and `PasskeyAuthenticationMethod<P>` (`method.passkey`, `passkey`) accept `(Class<P>, ChallengeProvider<P>)` or `(id, Class<P>, provider)`. The provider lives in `method.challenge`: prepare returns a compact `PreparedChallenge`, verify returns existing `MethodResult`, dispatch defaults to no-op and runs only after commit. Register the selected method as an AuthenticationMethod bean or in MethodRegistry; no default beans or mandatory factors are added. Policies choose them with existing ALL/ANY and trusted request data.

Call begin/beginNext then verify; a direct proof cannot bypass preparation. Auth enforces challenge ownership/deadlines, transaction-wide attempt limits, receipt recovery and terminal cleanup. Code generation/delivery/secure digest checking and account limits are provider-owned; scanning requires authenticated, explicit approval bound to the initiating challenge, not merely seeing a QR code. Delegate Passkey assertion validation to a mature WebAuthn implementation, with challenge/origin/RP ID, credential ownership, signature and UP/UV checks. Registration, Web endpoints, rendering and external replay authorities stay application-owned. Provider verification must support exact-operation recovery without granting another transaction/operation a consumed proof; do not persist plaintext codes or private keys in Auth payloads. These adapters do not implement the underlying protocols.

## Access rules and Spring method protection

`AuthAccessService.check(invocation, requirement)` returns ALLOWED, UNAUTHENTICATED, DENIED or AUTHENTICATION_REQUIRED. Only ALLOWED executes business. Authority groups distinguish roles/permissions, ALL/ANY; all groups are AND. Provider errors fail closed; MFA cannot repair insufficient business grants. Recheck current grants after additional authentication.

```yaml
java-impetus:
  auth:
    default-access: authenticated
    rules:
      - paths: [/public/**, /health]
        methods: [GET]
        access: public
      - paths: [/orders/**, /invoices/**]
        permissions:
          all: [document:write]
        policy: sensitivePolicy
```

`AuthRequestRules.resolve(path, method)` returns an `AuthAccessRule`. Ordered first match, not most-specific match; omitted methods matches all; unmatched defaults authenticated. Supported patterns are absolute Ant `*`, `**`, `?`, case-sensitive, not MVC PathPattern or regex variables. Pass the application's normalized routing path without context-path/query/fragment. The core does not decode or canonicalize raw URLs; normalization must agree with the application router/security entry.

```java
AuthAccessRule route = requestRules.resolve(normalizedPath, requestMethod);
AuthInvocation prepared = route.apply(trusted);
AuthAccessDecision decision = access.check(prepared, route.requirement());
```

Route policy is mandatory and does not replace default/global policy. `prepared` must also be used to start/continue additional authentication. Lists of mandatory names bind to the transaction and cannot be dropped on subsequent operations/claiming. Public access must not be mixed with explicit policy/authorities.

`@AuthAccess` declares value plus rolesAll/rolesAny/permissionsAll/permissionsAny. `@UseAuthPolicy(PolicyType.class)` selects exactly one policy bean. `AuthMethodRules.resolve(applicationType, method)` handles implementation/interface, bridge, inherited and composed annotations and caches definitions only. An access method annotation replaces its class default; request and method constraints compose with AND/DENY dominance. PUBLIC does not erase another layer.

```java
methodAccess.verify(trusted, applicationType, method, route);
// Execute the application method only after verify succeeds.
```

`check` returns a decision; `verify` throws `AuthAccessDeniedException` retaining that decision. These are explicit alternatives for non-proxied calls. Unannotated explicit checks default authenticated; automatic matching does not. The actual argument context belongs in trusted data. For MFA use `methodRules.resolve(type, method).and(route).apply(trusted)`, then check/start authentication with this same combined context; do not discard its mandatory policy list.

### Automatic method advisor

`@EnableAuth` defaults `java-impetus.auth.method-security.mode` to NATIVE. Select SECURITY explicitly to use Security's `AuthorizationManagerBeforeMethodInterceptor` with the Auth decision adapter; DISABLED disables only our automatic advisor. Default order is 250 (after standard PreFilter/PreAuthorize, before the default business transaction advisor). Application override bean name: `authMethodSecurityAdvisor`. Missing Security classes in SECURITY mode fails startup, never falls back to NATIVE. Modes do not disable the application's own method security.

```java
@Bean
AuthMethodInvocationProvider methodInputs(ApplicationAuthService application) {
    // ApplicationAuthService is application-owned; it builds trusted bindings/evidence
    // and supplies the actual argument data without retaining the invocation.
    return call -> application.trustedInvocation(call.getMethod(), call.getArguments());
}
```

Supply this adapter once, then call the Spring service normally; manual method reflection/verify is not required. PUBLIC/DENY does not resolve method inputs or Security identity. Protected calls with missing inputs/mappers or provider failures fail closed. NATIVE denial uses AuthAccessDeniedException. SECURITY denial uses AuthorizationDeniedException whose authorizationResult is AuthSecurityDecision; no Authentication in the holder follows the framework's AuthenticationCredentialsNotFoundException. No automatic challenge, credentials consumption or HTTP response mapping occurs.

Only `@AuthAccess` / `@UseAuthPolicy` (method/class/interface/composed declarations) match our advisor. A method with only Security annotations and no our class default does not enter Auth, including global required policies; an undeclared method also does not enter. When both apply, both checks must pass; Auth PUBLIC cannot override a Security requirement. PostAuthorize still occurs after business execution.

Respect Spring's shared proxy infrastructure: arbitrary objects, self-invocation, private methods and unproxyable final methods are not protected. Interface/JDK and eligible class proxies follow the application's settings; no forced CGLIB or AspectJ dependency. Definitions are cached, not arguments, MethodInvocation, decisions or results; no request ThreadLocal stack. Batch HTTP rules are not auto-merged from an unknown HTTP context: the trusted provider must carry needed route requirements.

## Completion, credentials and cleanup

CompletionId is a short-lived one-use handoff, not a bearer session. Choose one path:

1. `AuthenticationService.consume` obtains trusted AuthCompletion facts.
2. `AuthCompletionService.complete(input, transactionId, completionId, handler)` consumes then runs a selected synchronous handler on the calling thread. Context preserves original input/data; trust completion.binding/evidence rather than an early input snapshot. Handler failure is propagated; no reopen, auto retry, exactly-once business work or compensation.
3. `AuthCredentialService.issueSession` / `issueOperationCredential` atomically consume and issue opaque credentials. Do not first call consume/complete. Existing exact issuance receipts recover response loss within retention.

Enable credential beans via `credentials-enabled=true` when this token mechanism is actually needed. Existing external sessions need not be replaced. Tokens are opaque references, not JWT/OAuth tokens. Authentication facts contain no roles/passwords; optional credential attributes are separate explicit business snapshots, never live permissions.

`validateSession` checks token/realm/status/expiry; `invocation` maps to the target operation. `consumeOperation` is one-use with an exact operationId recovery receipt. `revoke` terminates credentials. `renewSession(token, realm, operationId, ttl[, data])` allows an application TokenRotationPolicy KEEP/ROTATE and optional issuance maximumLifetime. Renewal preserves original factor times; original token/operationId/TTL recover a committed renewal without extending again. It is not signing-key rotation.

`CredentialAttributesProvider.attributes(invocation, completion, kind)` is optional: supply a bean or the six-argument AuthCredentialService constructor. Return a small immutable value; the first committed issuance snapshot wins. The provider runs outside locks and may be invoked concurrently/on uncommitted retries, so avoid non-idempotent writes. `AuthCredential.attributes()` defaults null, survives renewal and operation-consumption receipts, and clears on revoke/expiry. Redis POJOs require stable payload type IDs and share the aggregate size limit; never copy the whole request/user object. Current grants/account status still come from application services.

`CredentialTokens(byte[])` / `AuthKeyRing.fixed(secret)` keeps a fixed key. Supply a thread-safe application AuthKeyRing bean or `new CredentialTokens(ring)` for dynamic keys: `current()` returns AuthKey(id, secret), `resolve(id)` returns historical key/null. Spring first uses an application CredentialTokens bean; otherwise AuthKeyRing takes precedence over fixed credential-key configuration or a local random key. At least 32 bytes, unique immutable IDs; application owns key source/cache/rotation/retirement. StoredCredential keyId commits with its digest/generation; KEEP preserves it, ROTATE selects current once, and recovery derives from committed keyId rather than latest selection. Do not retire keys needed by active credential renewals or retained receipts. Opaque validation uses the stored digest, so key removal is not session revocation; use revoke. The separate ProofFingerprint key remains stable through live transactions/idempotency windows. A custom AuthCredentialStore must implement the renewal overload with nextKeyId atomically; KEEP cannot change keyId. Issue candidates may be null only when confirming an already-consumed completion; still check the original committed request exactly, never perform fresh issuance from null.

`discard(input, transactionId)` permits ACTIVE or unconsumed COMPLETED, returns DISCARDED and clears challenge/evidence/completion/operations; same retained discarded record is idempotent. `cancel` retains its ACTIVE-only contract. Consumed completion cannot be discarded.

`purge(input, transactionId)` atomically removes an eligible terminal transaction after binding/version checks. ACTIVE or unconsumed COMPLETED needs discard first. NOT_FOUND follows cleanup; repeat purge does not manufacture a success receipt. Credentials/renewal/operation receipts continue independently, and TOTP replay records remain intact. Purge deletes original issuance recovery metadata: do not purge before confirming a token issuance response. Existing initiation tombstone stays until its current deadline, preventing the same initiation from restarting; both stores retain its bounded admission slot. To log out revoke the token, not purge a transaction.

No broad delete-all/clear-user API or external business rollback is provided. Never invoke purge/discard automatically because business work after authentication failed.

## Redis and Security

Redis is optional. Provide the application's RedissonClient, stable shared Base64 proof-key and (when credentials enabled) credential-key or an application AuthKeyRing, and choose `store=redis`/`redis.namespace`. Keys require at least 32 bytes. Replicated deployment reads from MASTER; the namespace shares one Cluster slot. Custom codecs register stable payload IDs for challenge data/attributes; no arbitrary class-name loading. Current storage schema is 4, no legacy development-format migration; use matching code/format and a separate namespace for other formats. Relative physical TTL handles Redis clock offset and network delays; record timestamps and the application Clock still enforce logical eligibility. Synchronize application-instance clocks; do not interpret a surviving Redis key as a live credential or retry unknown commits as new operations.

Custom AuthTransactionStore implements atomic create/load/advance/purge and preserves initiation tombstones. Credentials require the same store instance implementing AuthCredentialStore; never splice independent completion and credential stores. No custom Lua, owned RedissonClient or local request/state cache.

Security requires optional spring-security-core plus application `SecurityIdentityMapper`; authenticated flags/FACTOR_* authorities are not trusted factor evidence. `AuthSecurityAdapter.check` and authorizationManager delegate to the same core. `ruleAuthorizationManager(invocationFactory, ruleFactory)` resolves a rule once, preserves mandatory policies and doesn't read authentication on PUBLIC/DENY. Connect it to the application's existing Security entry; it does not create filters.

For native Security method annotations the application enables `@EnableMethodSecurity` with its own security-config/Starter; enable secured/jsr250 explicitly when desired. Our SECURITY advisor does not replace native annotations or enable them on the application's behalf. Keep `AuthorityProvider` application-owned; if sharing current GrantedAuthority values, reuse `AuthorityUtils.authorityListToSet` and explicitly choose role prefixes/hierarchy. PasswordEncoderVerifier already delegates to the existing Security encoder; LDAP/OIDC/session handling should reuse the application's ecosystem instead of a parallel Auth protocol/HTTP layer. Domain challenge CAS, one-use completion and replay receipts remain with Auth; Spring database transactions or ordinary events cannot replace those contracts.

`SecurityCompletionMapper` constructs the final authenticated, nonanonymous Authentication from real completion facts/current grants. `SecurityCompletionHandler` publishes a new context through the selected holder strategy on the calling thread. This does not sign tokens, mutate the old context, choose principal semantics or persist an HTTP session. Session fixation handling and SecurityContextRepository remain with the application's Web layer.
