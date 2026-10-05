package io.github.jockerCN.auth;

import io.github.jockerCN.auth.transaction.AuthBinding;
import io.github.jockerCN.auth.transaction.AuthEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Same trusted identity/evidence checks at authentication and access boundaries. No retained state. */
final class AuthIdentitySupport {
    private AuthIdentitySupport() { }
    static AuthBinding bind(AuthBinding binding, List<AuthEvidence> evidence, Instant now) {
        for (AuthEvidence value : evidence) {
            if (value.verifiedAt().isAfter(now)) throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
            if (Objects.isNull(binding.subject())) binding = binding.bind(value.subject());
            else if (!binding.subject().equals(value.subject())) throw new AuthException(AuthException.Code.IDENTITY_MISMATCH);
        }
        return binding;
    }
}
