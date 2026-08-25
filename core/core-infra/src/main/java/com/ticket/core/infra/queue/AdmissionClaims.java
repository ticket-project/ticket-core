package com.ticket.core.infra.queue;

import java.time.Instant;

public record AdmissionClaims(
        String subject,
        Long memberId,
        Long performanceId,
        Instant issuedAt,
        Instant expiresAt,
        String tokenId,
        String scope
) {
}
