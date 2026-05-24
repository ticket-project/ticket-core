package com.ticket.support.security.admission;

import java.time.Instant;

public record AdmissionClaims(
        Long memberId,
        Long performanceId,
        Instant issuedAt,
        Instant expiresAt,
        String tokenId,
        String scope
) {
}
