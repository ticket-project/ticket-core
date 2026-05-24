package com.ticket.support.security.jwt;

import java.time.Instant;

public record JwtMemberClaims(
        Long memberId,
        String role,
        Instant issuedAt,
        Instant expiresAt
) {
}
