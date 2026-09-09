package com.ticket.payment.domain;

/**
 * Payment(결제 시도)의 상태다. PG별 세부 상태를 그대로 노출하지 않는다 — provider payload 변환은
 * infrastructure가 맡는다(ADR 0005의 "미래 모듈별 공개 계약 초안" 참고, 이번 범위는 아니다).
 *
 * <p>허용 전이:
 *
 * <pre>
 * READY -> PROCESSING -> SUCCEEDED
 * READY / PROCESSING -> FAILED
 * READY / PROCESSING -> CANCELED
 * </pre>
 */
public enum PaymentStatus {
    READY,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
