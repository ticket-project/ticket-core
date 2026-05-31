package com.ticket.support.security.internalauth;

import java.time.Instant;

public record InternalAuthClaims(
        Long memberId,
        String role,
        String audience,
        Instant issuedAt,
        Instant expiresAt,
        String tokenId
) {
}
