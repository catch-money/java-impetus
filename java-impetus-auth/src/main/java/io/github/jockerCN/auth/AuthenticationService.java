package io.github.jockerCN.auth;

import io.github.jockerCN.auth.method.*;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.*;
import io.github.jockerCN.crypto.MessageAuthentication;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-neutral coordinator. All inputs/bindings/evidence must come from trusted application
 * adapters, not automatic deserialization of the complete AuthInvocation from a client.
 * No business data or proof is retained. Public results are not bearer sessions.
 */
public final class AuthenticationService {
    private final io.github.jockerCN.auth.store.AuthTransactionStore store;
    private final PolicyRegistry policies;
    private final MethodRegistry methods;
    private final ProofFingerprint fingerprint;
    private final Clock clock;
    private final AuthOptions options;

    public AuthenticationService(io.github.jockerCN.auth.store.AuthTransactionStore store,
                                 PolicyRegistry policies, MethodRegistry methods,
                                 ProofFingerprint fingerprint, Clock clock, AuthOptions options) {
        this.store = Objects.requireNonNull(store, "store");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.methods = Objects.requireNonNull(methods, "methods");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.options = Objects.requireNonNull(options, "options");
    }

    public static ProofFingerprint localFingerprint() {
        return new JacksonProofFingerprint(MessageAuthentication.generateKey(), 16 * 1024);
    }

    public AuthResult begin(AuthInvocation input, String operationId, String methodId) {
        return initiate(input, operationId, methodId, null, AuthOperation.Kind.BEGIN);
    }

    /**
     * Full proof directly: no begin/dispatch challenge or extra round-trip for passwords.
     */
    public AuthResult authenticate(AuthInvocation input, String operationId, String methodId, Object proof) {
        methods.checkProof(methodId, proof);
        return initiate(input, operationId, methodId, proof, AuthOperation.Kind.DIRECT);
    }

    private AuthResult initiate(AuthInvocation input, String operationId, String requestedMethod,
                                Object proof, AuthOperation.Kind kind) {
        checkOperationId(operationId);
        String policy = Objects.nonNull(input.policy()) ? input.policy() : policies.defaultPolicy();
        Instant now = clock.instant();
        AuthBinding binding = AuthIdentitySupport.bind(input.binding(), input.evidence(), now);
        AuthRequirement requirement = evaluate(policy, input.requiredPolicies(), binding, input.evidence(),
                AuthEvaluationContext.Phase.INITIAL, input.data(), now);
        String startKey = fingerprint.fingerprint(Arrays.asList(binding.realm(), binding.purpose(),
                binding.operation(), binding.initiator(), operationId));
        AuthTransaction initial = new AuthTransaction(id("tx"), startKey, policy, binding, requirement,
                input.evidence(), AuthStatus.ACTIVE, 0, null, java.util.Map.of(), 0, null, null,
                now.plus(options.transactionTtl()), now.plus(options.transactionTtl()).plus(options.retentionTtl()),
                input.requiredPolicies());
        AuthTransaction current = store.create(initial);
        checkInvocation(current, input);
        String content = fingerprint.fingerprint(Arrays.asList(kind, policy, input.requiredPolicies(), requestedMethod, binding.subject(), proof));
        AuthResult duplicate = duplicate(current, operationId, kind, content);
        if (Objects.nonNull(duplicate)) return duplicate;
        active(current);
        if (current.requirement().satisfied(current.evidence(), current.binding(), now)
                && Objects.nonNull(current.binding().subject()))
            return completeWithoutMethod(current, operationId, kind, content, input);
        String methodId = selectMethod(current, requestedMethod, now);
        if (kind == AuthOperation.Kind.DIRECT) methods.checkProof(methodId, proof);
        AuthTransaction claimed = claim(current, operationId, kind, methodId, id("stage"), null, content);
        return execute(claimed, operationId, input, proof);
    }

    /**
     * Continue the same transaction with its next required factor; never resets its deadline.
     */
    public AuthResult beginNext(AuthInvocation input, String transactionId, String operationId, String methodId) {
        return continueMethod(input, transactionId, operationId, methodId, null, AuthOperation.Kind.BEGIN);
    }

    public AuthResult authenticateNext(AuthInvocation input, String transactionId, String operationId,
                                       String methodId, Object proof) {
        methods.checkProof(methodId, proof);
        return continueMethod(input, transactionId, operationId, methodId, proof, AuthOperation.Kind.DIRECT);
    }

    private AuthResult continueMethod(AuthInvocation input, String transactionId, String operationId,
                                      String methodId, Object proof, AuthOperation.Kind kind) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        String content = fingerprint.fingerprint(Arrays.asList(kind, current.policy(), methodId,
                input.binding().subject(), proof));
        AuthResult duplicate = duplicate(current, operationId, kind, content);
        if (Objects.nonNull(duplicate)) return duplicate;
        active(current);
        if (Objects.nonNull(current.challenge())) throw new AuthException(AuthException.Code.INVALID_CHALLENGE);
        String selected = selectMethod(current, methodId, clock.instant());
        return execute(claim(current, operationId, kind, selected, id("stage"), null, content),
                operationId, input, proof);
    }

    public AuthResult verify(AuthInvocation input, String transactionId, String challengeId,
                             String operationId, Object proof) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        String content = fingerprint.fingerprint(Arrays.asList(AuthOperation.Kind.VERIFY, challengeId, proof));
        AuthResult duplicate = duplicate(current, operationId, AuthOperation.Kind.VERIFY, content);
        if (Objects.nonNull(duplicate)) return duplicate;
        active(current);
        AuthChallenge challenge = challenge(current, challengeId, true);
        methods.checkProof(challenge.methodId(), proof);
        AuthTransaction claimed = claim(current, operationId, AuthOperation.Kind.VERIFY, challenge.methodId(),
                challenge.stageId(), challengeId, content);
        return execute(claimed, operationId, input, proof);
    }

    /**
     * Explicit delivery retry. The provider must be able to rebuild delivery from privateState.
     */
    public AuthResult resend(AuthInvocation input, String transactionId, String challengeId, String operationId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        String content = fingerprint.fingerprint(Arrays.asList(AuthOperation.Kind.RESEND, challengeId));
        AuthResult duplicate = duplicate(current, operationId, AuthOperation.Kind.RESEND, content);
        if (Objects.nonNull(duplicate)) return duplicate;
        active(current);
        AuthChallenge challenge = challenge(current, challengeId, true);
        AuthTransaction claimed = claim(current, operationId, AuthOperation.Kind.RESEND, challenge.methodId(),
                challenge.stageId(), challengeId, content);
        AuthOperation operation = claimed.operations().get(operationId);
        // Receipt commits before external notification; failed dispatch is not rolled back.
        AuthTransaction committed = store.advance(claimed.version(), claimed.settle(operation, claimed.binding(),
                claimed.requirement(), claimed.evidence(), AuthStatus.ACTIVE, claimed.challenge(),
                null, claimed.reason(), claimed.purgeAt()));
        dispatch(committed, operation, input.data());
        return committed.view();
    }

    /**
     * Same stage, new challenge ID; old proofs stop being eligible after commit.
     */
    public AuthResult replaceChallenge(AuthInvocation input, String transactionId, String challengeId,
                                       String operationId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        String content = fingerprint.fingerprint(Arrays.asList(AuthOperation.Kind.REPLACE, challengeId));
        AuthResult duplicate = duplicate(current, operationId, AuthOperation.Kind.REPLACE, content);
        if (Objects.nonNull(duplicate)) return duplicate;
        active(current);
        AuthChallenge challenge = challenge(current, challengeId, false);
        AuthTransaction claimed = claim(current, operationId, AuthOperation.Kind.REPLACE, challenge.methodId(),
                challenge.stageId(), challengeId, content);
        return execute(claimed, operationId, input, null);
    }

    public AuthResult state(AuthInvocation input, String transactionId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        return current.view();
    }

    public AuthResult cancel(AuthInvocation input, String transactionId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        if (current.status() == AuthStatus.CANCELLED) return current.view();
        active(current);
        return store.advance(current.version(), current.terminal(AuthStatus.CANCELLED,
                "cancelled", clock.instant().plus(options.retentionTtl()))).view();
    }

    /**
     * Abandon an ACTIVE or unconsumed COMPLETED chain; never undo consumption or issued tokens.
     */
    public AuthResult discard(AuthInvocation input, String transactionId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        if (current.status() == AuthStatus.DISCARDED) return current.view();
        if (current.status() != AuthStatus.ACTIVE && current.status() != AuthStatus.COMPLETED)
            throw new AuthException(AuthException.Code.TERMINAL);
        if (Objects.nonNull(current.completion()) && current.completion().consumed())
            throw new AuthException(AuthException.Code.ALREADY_CONSUMED);
        Instant purgeAt = clock.instant().plus(options.retentionTtl());
        if (purgeAt.isAfter(current.purgeAt())) purgeAt = current.purgeAt();
        return store.advance(current.version(), current.terminal(AuthStatus.DISCARDED, "discarded", purgeAt)).view();
    }

    /**
     * Explicit irreversible transaction cleanup, not logout, business rollback or OTP reset.
     */
    public void purge(AuthInvocation input, String transactionId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        store.purge(transactionId, current.version());
    }

    /**
     * Exactly one successful consumption, NOT exactly-once execution of the caller's business work.
     */
    public AuthCompletion consume(AuthInvocation input, String transactionId, String completionId) {
        AuthTransaction current = completionForIssue(input, transactionId, completionId);
        if (current.completion().consumed()) throw new AuthException(AuthException.Code.ALREADY_CONSUMED);
        AuthCompletion completion = current.completion();
        store.advance(current.version(), current.consumed());
        return completion;
    }

    boolean usesStore(io.github.jockerCN.auth.store.AuthTransactionStore candidate) {
        return store == candidate;
    }

    /**
     * A consumed result is returned only to let the store confirm an existing issuance receipt.
     */
    AuthTransaction completionForIssue(AuthInvocation input, String transactionId, String completionId) {
        AuthTransaction current = store.load(transactionId);
        checkInvocation(current, input);
        AuthCompletion completion = current.completion();
        if (current.status() != AuthStatus.COMPLETED || Objects.isNull(completion))
            throw new AuthException(AuthException.Code.TERMINAL);
        if (!completion.id().equals(completionId))
            throw new AuthException(AuthException.Code.BINDING_MISMATCH);
        if (completion.consumed()) return current;
        if (!completion.expiresAt().isAfter(clock.instant())) throw new AuthException(AuthException.Code.EXPIRED);
        AuthRequirement latest = evaluate(current.policy(), current.requiredPolicies(), current.binding(), current.evidence(),
                AuthEvaluationContext.Phase.FINAL, input.data(), clock.instant());
        if (!AuthRequirement.combine(current.requirement(), latest)
                .satisfied(current.evidence(), current.binding(), clock.instant()))
            throw new AuthException(AuthException.Code.REQUIREMENTS_CHANGED);
        return current;
    }

    private AuthResult execute(AuthTransaction claimed, String operationId, AuthInvocation input, Object proof) {
        AuthOperation operation = claimed.operations().get(operationId);
        MethodContext context = context(claimed, operation, input.data());
        MethodResult result;
        try {
            result = Objects.requireNonNull(switch (operation.kind()) {
                case VERIFY, DIRECT -> methods.verify(operation.methodId(), context, proof);
                case BEGIN, REPLACE -> methods.get(operation.methodId()).begin(context);
                default -> throw new IllegalStateException("unexpected operation");
            }, "method result");
        } catch (RuntimeException failure) {
            release(claimed, operation);
            throw new AuthException(AuthException.Code.METHOD_FAILED, failure);
        }
        try {
            return commitResult(claimed, operation, input, result);
        } catch (RuntimeException failure) {
            release(claimed, operation);
            throw failure;
        }
    }

    private AuthResult commitResult(AuthTransaction claimed, AuthOperation operation,
                                    AuthInvocation input, MethodResult result) {
        Instant now = clock.instant();
        if (operation.kind() == AuthOperation.Kind.VERIFY
                && !claimed.challenge().expiresAt().isAfter(now))
            throw new AuthException(AuthException.Code.EXPIRED);
        AuthTransaction next;
        if (result instanceof MethodResult.Verified(AuthEvidence evidence)) {
            if (!operation.methodId().equals(evidence.methodId())
                    || evidence.verifiedAt().isAfter(now)
                    || !Objects.equals(evidence.purpose(), claimed.binding().purpose())
                    || !Objects.equals(evidence.operation(), claimed.binding().operation()))
                throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
            AuthBinding binding = claimed.binding().bind(evidence.subject());
            List<AuthEvidence> accepted = new ArrayList<>(claimed.evidence());
            accepted.add(evidence);
            AuthRequirement requirement;
            try {
                requirement = AuthRequirement.combine(claimed.requirement(),
                        evaluate(claimed.policy(), claimed.requiredPolicies(), binding, accepted, AuthEvaluationContext.Phase.CONTINUE, input.data(), now));
                if (requirement.satisfied(accepted, binding, now)) {
                    requirement = AuthRequirement.combine(requirement,
                            evaluate(claimed.policy(), claimed.requiredPolicies(), binding, accepted, AuthEvaluationContext.Phase.FINAL, input.data(), now));
                }
            } catch (AuthException denied) {
                if (denied.code() != AuthException.Code.POLICY_DENIED) throw denied;
                next = claimed.terminal(AuthStatus.REJECTED, "policy-denied", now.plus(options.retentionTtl()));
                return store.advance(claimed.version(), next).view();
            }
            if (requirement.satisfied(accepted, binding, now)) {
                AuthCompletion completion = new AuthCompletion(id("completion"), binding, accepted,
                        now.plus(options.retentionTtl()), false);
                next = claimed.settle(operation, binding, requirement, accepted, AuthStatus.COMPLETED,
                        null, completion, null, completion.expiresAt());
            } else {
                next = claimed.settle(operation, binding, requirement, accepted, AuthStatus.ACTIVE,
                        null, null, null, claimed.purgeAt());
            }
        } else if (result instanceof MethodResult.Rejected(String reason, boolean terminal)) {
            if (terminal || claimed.attempts() >= options.maximumAttempts()) {
                next = claimed.terminal(AuthStatus.REJECTED, reason, now.plus(options.retentionTtl()));
            } else {
                next = claimed.settle(operation, claimed.binding(), claimed.requirement(), claimed.evidence(),
                        AuthStatus.ACTIVE, claimed.challenge(), null, reason, claimed.purgeAt());
            }
        } else {
            PreparedChallenge prepared = result instanceof MethodResult.Challenge(PreparedChallenge value)
                    ? value : ((MethodResult.Pending) result).challenge();
            Instant expires = now.plus(prepared.ttl());
            if (expires.isAfter(claimed.expiresAt())) expires = claimed.expiresAt();
            AuthChallenge challenge = new AuthChallenge(operation.stageId(), id("challenge"), operation.methodId(),
                    result instanceof MethodResult.Pending ? AuthChallenge.Interaction.PENDING : AuthChallenge.Interaction.CHALLENGE,
                    prepared.publicPayload(), prepared.privateState(), expires);
            next = claimed.settle(operation, claimed.binding(), claimed.requirement(), claimed.evidence(),
                    AuthStatus.ACTIVE, challenge, null, null, claimed.purgeAt());
        }
        AuthTransaction committed = store.advance(claimed.version(), next);
        if (Objects.nonNull(committed.challenge()) && committed.challenge() != claimed.challenge())
            dispatch(committed, operation, input.data());
        return committed.view();
    }

    private AuthResult completeWithoutMethod(AuthTransaction current, String operationId, AuthOperation.Kind kind,
                                             String content, AuthInvocation input) {
        AuthRequirement finalRequirement = AuthRequirement.combine(current.requirement(),
                evaluate(current.policy(), current.requiredPolicies(), current.binding(), current.evidence(),
                        AuthEvaluationContext.Phase.FINAL, input.data(), clock.instant()));
        if (!finalRequirement.satisfied(current.evidence(), current.binding(), clock.instant()))
            throw new AuthException(AuthException.Code.REQUIREMENTS_CHANGED);
        AuthTransaction claimed = claim(current, operationId, kind, null, null, null, content);
        AuthCompletion completion = new AuthCompletion(id("completion"), claimed.binding(), claimed.evidence(),
                clock.instant().plus(options.retentionTtl()), false);
        return store.advance(claimed.version(), claimed.settle(claimed.operations().get(operationId), claimed.binding(),
                finalRequirement, claimed.evidence(), AuthStatus.COMPLETED, null, completion, null,
                completion.expiresAt())).view();
    }

    private AuthTransaction claim(AuthTransaction current, String operationId, AuthOperation.Kind kind,
                                  String methodId, String stageId, String challengeId, String content) {
        checkOperationId(operationId);
        Instant now = clock.instant();
        if (current.operations().values().stream().anyMatch(o -> o.running(now)))
            throw new AuthException(AuthException.Code.IN_PROGRESS);
        if (!current.operations().containsKey(operationId)
                && current.operations().size() >= options.maximumOperations())
            throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
        AuthOperation previous = current.operations().get(operationId);
        boolean verification = Objects.isNull(previous)
                && (kind == AuthOperation.Kind.VERIFY || kind == AuthOperation.Kind.DIRECT);
        if (verification && current.attempts() >= options.maximumAttempts())
            throw new AuthException(AuthException.Code.LIMIT_EXCEEDED);
        AuthOperation operation = new AuthOperation(operationId, kind, methodId,
                Objects.isNull(previous) ? stageId : previous.stageId(), challengeId,
                content, id("claim"), now.plus(options.operationLease()), false);
        return store.advance(current.version(), current.claim(operation, current.attempts() + (verification ? 1 : 0)));
    }

    private AuthResult duplicate(AuthTransaction current, String operationId, AuthOperation.Kind kind, String content) {
        checkOperationId(operationId);
        AuthOperation existing = current.operations().get(operationId);
        if (Objects.isNull(existing)) return null;
        if (existing.kind() != kind || !existing.fingerprint().equals(content))
            throw new AuthException(AuthException.Code.OPERATION_CONFLICT);
        if (existing.committed()) return current.view();
        if (existing.running(clock.instant())) throw new AuthException(AuthException.Code.IN_PROGRESS);
        return null;
    }

    private void release(AuthTransaction claimed, AuthOperation operation) {
        try {
            store.advance(claimed.version(), claimed.claim(operation.release(), claimed.attempts()));
        } catch (RuntimeException ignored) {
            // State may have changed/commit may be uncertain. Never clear someone else's claim.
            // The bounded lease permits the same operation to recover without retaining the proof.
        }
    }

    private void dispatch(AuthTransaction committed, AuthOperation operation, Object data) {
        try {
            methods.get(committed.challenge().methodId()).dispatch(context(committed,
                    new AuthOperation(operation.id(), operation.kind(), committed.challenge().methodId(),
                            committed.challenge().stageId(), committed.challenge().id(), operation.fingerprint(),
                            null, null, true), data));
        } catch (RuntimeException failure) {
            throw new AuthException(AuthException.Code.DELIVERY_FAILED, failure);
        }
    }

    private MethodContext context(AuthTransaction current, AuthOperation operation, Object data) {
        Object privateState = Objects.isNull(current.challenge()) ? null : current.challenge().privateState();
        return new MethodContext(current.binding(), current.id(), operation.stageId(), operation.challengeId(),
                operation.id(), clock.instant(), privateState, data);
    }

    private String selectMethod(AuthTransaction current, String requested, Instant now) {
        List<AuthRequirement.Factor> candidates = current.requirement().next(current.evidence(), current.binding(), now);
        if (candidates.isEmpty() && Objects.isNull(current.binding().subject()) && Objects.nonNull(requested)) {
            methods.get(requested);
            return requested;
        }
        if (Objects.nonNull(requested)) {
            if (candidates.stream().noneMatch(f -> f.methodId().equals(requested)))
                throw new AuthException(AuthException.Code.METHOD_UNAVAILABLE);
            methods.get(requested);
            return requested;
        }
        return candidates.stream().map(AuthRequirement.Factor::methodId).filter(methods::contains).findFirst()
                .orElseThrow(() -> new AuthException(candidates.isEmpty()
                        ? AuthException.Code.IDENTITY_REQUIRED : AuthException.Code.METHOD_UNAVAILABLE));
    }

    private AuthRequirement evaluate(String policy, List<String> required, AuthBinding binding, List<AuthEvidence> evidence,
                                     AuthEvaluationContext.Phase phase, Object data, Instant now) {
        AuthDecision decision = policies.evaluate(policy, required,
                new AuthEvaluationContext(binding, evidence, phase, now, data));
        if (decision.kind() == AuthDecision.Kind.DENY) throw new AuthException(AuthException.Code.POLICY_DENIED);
        return decision.requirement();
    }

    private void checkInvocation(AuthTransaction current, AuthInvocation input) {
        current.binding().check(AuthIdentitySupport.bind(input.binding(), input.evidence(), clock.instant()));
        if (Objects.nonNull(input.policy()) && !Objects.equals(current.policy(), input.policy()))
            throw new AuthException(AuthException.Code.BINDING_MISMATCH);
        if (!current.requiredPolicies().equals(input.requiredPolicies()))
            throw new AuthException(AuthException.Code.BINDING_MISMATCH);
    }

    private AuthChallenge challenge(AuthTransaction current, String id, boolean requireValid) {
        AuthChallenge challenge = current.challenge();
        if (Objects.isNull(challenge) || !challenge.id().equals(id))
            throw new AuthException(AuthException.Code.INVALID_CHALLENGE);
        if (requireValid && !challenge.expiresAt().isAfter(clock.instant()))
            throw new AuthException(AuthException.Code.EXPIRED);
        return challenge;
    }

    private void active(AuthTransaction current) {
        if (current.status() == AuthStatus.EXPIRED) throw new AuthException(AuthException.Code.EXPIRED);
        if (current.status() != AuthStatus.ACTIVE) throw new AuthException(AuthException.Code.TERMINAL);
    }

    private void checkOperationId(String value) {
        if (Objects.isNull(value) || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException("operationId must be 1..128 characters");
    }

    private static String id(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }
}
