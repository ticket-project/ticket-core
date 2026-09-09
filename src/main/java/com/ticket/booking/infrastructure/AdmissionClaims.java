package com.ticket.booking.infrastructure;

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
