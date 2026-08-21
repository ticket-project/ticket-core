package com.ticket.core.domain.payment.gateway;

public record PaymentApprovalResult(
        boolean approved,
        String pgTransactionId,
        String failureCode,
        String failureMessage
) {

    public PaymentApprovalResult {
        if (approved && (pgTransactionId == null || failureCode != null)) {
            throw new IllegalArgumentException("승인 결과는 pgTransactionId만 가져야 합니다.");
        }
        if (!approved && (failureCode == null || pgTransactionId != null)) {
            throw new IllegalArgumentException("거절 결과는 failureCode만 가져야 합니다.");
        }
    }

    public static PaymentApprovalResult approved(final String pgTransactionId) {
        return new PaymentApprovalResult(true, pgTransactionId, null, null);
    }

    public static PaymentApprovalResult declined(final String failureCode, final String failureMessage) {
        return new PaymentApprovalResult(false, null, failureCode, failureMessage);
    }
}
