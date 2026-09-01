package com.ticket.core.app.admission;

/**
 * 대기열 입장이 보장됐는지 확인하는 port.
 *
 * <p>도메인은 "대기열이 필요한가"를 {@code PerformanceBookingPolicySnapshot.requiresQueueAt}으로 스스로
 * 판단하고, 필요할 때만 이 port를 호출한다. 토큰을 어떻게 검증하는지는 어댑터만 안다.
 * core-domain에는 JWT 의존이 없으므로 검증 구현을 여기 둘 수 없다.
 */
public interface AdmissionGuard {

    /**
     * 입장이 보장되지 않으면 예외를 던진다.
     *
     * @param admissionToken 요청이 전달한 입장 토큰. null 또는 빈 문자열일 수 있다
     */
    void ensureAdmitted(Long performanceId, Long memberId, String admissionToken);
}
