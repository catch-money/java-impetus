package io.github.jockerCN.auth.transaction;

import java.time.Instant;

public record ChallengeView(String id, String methodId, AuthChallenge.Interaction interaction,
                            Object payload, Instant expiresAt) { }
