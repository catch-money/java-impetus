package io.github.jockerCN.auth.store;

import io.github.jockerCN.auth.credential.CredentialIssue;
import io.github.jockerCN.auth.credential.AuthCredential;
import io.github.jockerCN.auth.policy.*;
import io.github.jockerCN.auth.transaction.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** Dedicated, bounded storage encoding. Public JSON configuration is deliberately not used.
 * Payload types are explicitly registered by stable ID; stored data cannot name arbitrary JVM classes.
 */
public final class RedisAuthStateCodec {
    private final JsonMapper mapper = JsonMapper.builder().disable(MapperFeature.USE_ANNOTATIONS).build();
    private final Map<String, Class<?>> types;
    private final Map<Class<?>, String> names;
    private final int maximumBytes;

    public RedisAuthStateCodec() { this(Map.of(), 64 * 1024); }

    public RedisAuthStateCodec(Map<String, Class<?>> payloadTypes, int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
        Map<String, Class<?>> registered = new HashMap<>(Map.of(
                "string", String.class, "boolean", Boolean.class, "integer", Integer.class,
                "long", Long.class, "decimal", BigDecimal.class, "bytes", byte[].class,
                "map", Map.class, "list", List.class));
        payloadTypes.forEach((name, type) -> {
            if (!name.matches("[A-Za-z0-9._-]{1,64}") || Objects.isNull(type)
                    || Objects.nonNull(registered.putIfAbsent(name, type)))
                throw new IllegalArgumentException("invalid or duplicate auth payload type ID");
        });
        Map<Class<?>, String> reverse = new HashMap<>();
        registered.forEach((name, type) -> {
            if (Objects.nonNull(reverse.putIfAbsent(type, name)))
                throw new IllegalArgumentException("duplicate auth payload class");
        });
        this.types = Map.copyOf(registered);
        this.names = Map.copyOf(reverse);
    }

    String encode(StoredAuthState state) {
        byte[] bytes = mapper.writeValueAsBytes(new State(4, transaction(state.transaction()), credential(state.credential()),
                state.issue(), state.consumptionId(), state.credentialPurgeAt(), state.renewals()));
        checkSize(bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    StoredAuthState decode(String encoded) {
        checkSize(encoded.getBytes(StandardCharsets.UTF_8).length);
        State state = mapper.readValue(encoded, State.class);
        if (state.schema() != 4) throw new IllegalArgumentException("unsupported auth storage schema");
        return new StoredAuthState(transaction(state.transaction()), credential(state.credential()), state.issue(),
                state.consumptionId(), state.credentialPurgeAt(), state.renewals());
    }

    private void checkSize(int size) {
        if (size > maximumBytes) throw new IllegalArgumentException("auth storage state exceeds maximumBytes");
    }

    private Payload payload(Object value) {
        if (Objects.isNull(value)) return null;
        Class<?> type = value instanceof Map<?, ?> ? Map.class : value instanceof List<?> ? List.class : value.getClass();
        String name = names.get(type);
        if (Objects.isNull(name)) throw new IllegalArgumentException("unregistered auth challenge payload type");
        return new Payload(name, mapper.valueToTree(value));
    }

    private Object payload(Payload value) {
        if (Objects.isNull(value)) return null;
        Class<?> type = types.get(value.type());
        if (Objects.isNull(type)) throw new IllegalArgumentException("unknown auth challenge payload type ID");
        return mapper.treeToValue(value.value(), type);
    }

    private Requirement requirement(AuthRequirement value) {
        if (Objects.isNull(value)) return null;
        return switch (value) {
            case AuthRequirement.Factor factor -> new Requirement("factor", factor.methodId(), factor.reuse(), List.of());
            case AuthRequirement.All all -> new Requirement("all", null, null, all.children().stream().map(this::requirement).toList());
            case AuthRequirement.Any any -> new Requirement("any", null, null, any.children().stream().map(this::requirement).toList());
        };
    }

    private AuthRequirement requirement(Requirement value) {
        if (Objects.isNull(value)) return null;
        return switch (value.kind()) {
            case "factor" -> new AuthRequirement.Factor(value.method(), value.reuse());
            case "all" -> new AuthRequirement.All(value.children().stream().map(this::requirement).toList());
            case "any" -> new AuthRequirement.Any(value.children().stream().map(this::requirement).toList());
            default -> throw new IllegalArgumentException("unknown auth requirement kind");
        };
    }

    private Transaction transaction(AuthTransaction tx) {
        if (Objects.isNull(tx)) return null;
        AuthChallenge c = tx.challenge();
        Challenge challenge = Objects.isNull(c) ? null : new Challenge(c.stageId(), c.id(), c.methodId(),
                c.interaction(), payload(c.publicPayload()), payload(c.privateState()), c.expiresAt());
        return new Transaction(tx.id(), tx.initiationKey(), tx.policy(), tx.binding(), requirement(tx.requirement()),
                tx.evidence(), tx.status(), tx.version(), challenge, tx.operations(), tx.attempts(), tx.completion(),
                tx.reason(), tx.expiresAt(), tx.purgeAt(), tx.requiredPolicies());
    }

    private AuthTransaction transaction(Transaction tx) {
        if (Objects.isNull(tx)) return null;
        Challenge c = tx.challenge();
        AuthChallenge challenge = Objects.isNull(c) ? null : new AuthChallenge(c.stageId(), c.id(), c.methodId(),
                c.interaction(), payload(c.publicPayload()), payload(c.privateState()), c.expiresAt());
        return new AuthTransaction(tx.id(), tx.initiationKey(), tx.policy(), tx.binding(), requirement(tx.requirement()),
                tx.evidence(), tx.status(), tx.version(), challenge, tx.operations(), tx.attempts(), tx.completion(),
                tx.reason(), tx.expiresAt(), tx.purgeAt(), tx.requiredPolicies());
    }

    private Credential credential(StoredCredential stored) {
        if (Objects.isNull(stored)) return null;
        AuthCredential value = stored.credential();
        return new Credential(value.id(), value.kind(), value.binding(), value.evidence(), value.createdAt(),
                value.expiresAt(), value.status(), payload(value.attributes()), stored.tokenDigest(), stored.version(),
                stored.tokenGeneration(), stored.tokenIssuedAt(), stored.absoluteExpiresAt(), stored.keyId());
    }

    private StoredCredential credential(Credential value) {
        if (Objects.isNull(value)) return null;
        return new StoredCredential(new AuthCredential(value.id(), value.kind(), value.binding(), value.evidence(),
                value.createdAt(), value.expiresAt(), value.status(), payload(value.attributes())), value.tokenDigest(),
                value.version(), value.tokenGeneration(), value.tokenIssuedAt(), value.absoluteExpiresAt(), value.keyId());
    }

    private record Credential(String id, AuthCredential.Kind kind, AuthBinding binding, List<AuthEvidence> evidence,
                              Instant createdAt, Instant expiresAt, AuthCredential.Status status, Payload attributes,
                              String tokenDigest, long version, long tokenGeneration, Instant tokenIssuedAt,
                              Instant absoluteExpiresAt, String keyId) { }
    private record State(int schema, Transaction transaction, Credential credential, CredentialIssue issue,
                         String consumptionId, Instant credentialPurgeAt,
                         Map<String, CredentialRenewalReceipt> renewals) { }
    private record Payload(String type, JsonNode value) { }
    private record Requirement(String kind, String method, EvidenceReuse reuse, List<Requirement> children) { }
    private record Challenge(String stageId, String id, String methodId, AuthChallenge.Interaction interaction,
                             Payload publicPayload, Payload privateState, Instant expiresAt) { }
    private record Transaction(String id, String initiationKey, String policy, AuthBinding binding,
                               Requirement requirement, List<AuthEvidence> evidence, AuthStatus status,
                               long version, Challenge challenge, Map<String, AuthOperation> operations,
                               int attempts, AuthCompletion completion, String reason, Instant expiresAt, Instant purgeAt,
                               List<String> requiredPolicies) { }
}
