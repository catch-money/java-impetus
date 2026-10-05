---
name: java-impetus-auth
description: Use java-impetus-auth in a consuming Java or Spring Boot application for policy-driven authentication, password/TOTP challenges, credentials, access rules, Spring method protection, or optional Spring Security bridges. Apply to integration and usage, not to changing library internals, implementing an identity provider or building RBAC tables.
---

# Use java-impetus-auth

Use the actual API in the consumer's resolved version; [authentication integration](references/auth-api.md) describes the 2.0.0 implementation. Read the relevant section before selecting lifecycle, rule or Security APIs.

Add `io.github.jocker-cn:java-impetus-auth` at the application's managed version. Use Java 21 and Spring Boot 4-compatible dependencies. Spring integration is explicitly enabled by `@EnableAuth`; Redis and Spring Security are optional. Native assembly through constructors is also supported. Do not create a second identity model, token system or filter chain when the application already has one.

Select only the required capabilities. Password/TOTP defaults require their credential-provider beans; code/scan/Passkey bridges require explicit method registration. Policies choose the factors per call. Keep credentials disabled when the application already owns sessions; merely registering an adapter does not make a factor mandatory.

Build `AuthInvocation` only from trusted server-side bindings and verified identity/evidence. Do not deserialize a full invocation, subject, verifiedAt or policy list directly from client claims. Pass per-call business context through `data`; definitions/services are reusable but must not retain request objects.

Keep authentication and authorization distinct. `AuthenticationPolicy` supplies PASS/REQUIRE/DENY; `AuthenticationMethod<P>` verifies factors. `AuthorityProvider` reads current business grants. Only an ALLOWED access decision permits business execution; completing MFA cannot supply a missing permission. Application IP/device/region requirements remain optional policy code, not hard-coded fields.

Temporary-code, scan-approval and Passkey adapters are optional challenge-first bridges, not protocol implementations. Register only chosen methods and a trusted application `ChallengeProvider<P>`; use existing policy ALL/ANY selection. Delegate WebAuthn to a mature verifier, never accept a client's success/subject assertion. Do not create another challenge store or HTTP layer merely to use these bridges. See the factor section in the reference for ownership boundaries.

With `@EnableAuth`, `@AuthAccess` and `@UseAuthPolicy` are enforced on Spring-proxied methods by the selected advisor. Default NATIVE uses Spring AOP without Security; explicit SECURITY uses its standard before-method interceptor; DISABLED leaves only explicit APIs. Supply one trusted `AuthMethodInvocationProvider`; in SECURITY supply a `SecurityIdentityMapper` as well. Never switch modes simply because Security is on the classpath. Only our annotation declarations match: Security-only and unannotated methods never enter Auth, including its global policies. With both annotation families, both apply independently. Respect Spring proxy/self-invocation/final-method limits; use `AuthMethodAccessService.verify/check` as an explicit alternative for non-proxied calls, not another mandatory check for already-proxied methods.

Batch request rules remain startup-bound metadata, not automatic HTTP protection; connect them to the application's existing request/Security entry. Preserve mandatory route/global policies when preparing trusted method inputs and starting additional authentication. Do not create another proxy engine, Method ThreadLocal or Servlet filter chain.

Only a committed COMPLETED result may be consumed or exchanged. `consume`, `AuthCompletionService.complete(..., handler)`, and credential issuance are mutually exclusive ways to claim the same result. Do not consume before issuing a token or issue again inside a completion handler. The handler runs synchronously after consumption, outside store locks; failure does not reopen authentication or trigger business compensation.

Use `discard` for an active or completed-unconsumed chain, `purge` for eligible terminal transaction records, and `revoke` for credentials. Purge is explicit irreversible record cleanup: it preserves live credentials, renewal receipts, initiation tombstones and independent TOTP anti-replay records, but removes issuance recovery metadata. Do not purge an issuance whose response remains unconfirmed.

For Redis, use a dedicated application namespace, stable shared keys and the application's RedissonClient. Never silently fall back to local storage after a shared-store failure. For Security, application mappers own principal binding, actual factor facts and current grants; context publication does not persist an HTTP login session. Do not fabricate factor evidence from authenticated flags or authority strings.

Credential attributes are opt-in immutable application snapshots from `CredentialAttributesProvider`, not automatic request/user copying or cached live permissions. Register custom Redis payload types explicitly. For fixed or rotating credential keys use a thread-safe application `AuthKeyRing`; retain historical IDs needed for renewal and exact receipt recovery. Key retirement is not token revocation, and this ring does not rotate the separate operation-fingerprint key.

Verify the consuming application's changed path with its available environment. Do not start services, clean application namespaces or assume production Redis is disposable merely to test an integration.
