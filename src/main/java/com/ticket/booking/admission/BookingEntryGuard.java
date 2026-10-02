package com.ticket.booking.admission;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.usecase.PerformanceSaleFinder;

import lombok.RequiredArgsConstructor;

/**
 * 예매 진입 검사다 — 회차 판매 정책을 찾고, 예매 기간인지 보고, 대기열 입장을 요구하는 회차면 ticket-queue가 발급한 입장 토큰을 검증한다.
 *
 * <p><b>왜 별도 class인가.</b> 주문 생성·좌석 선택·좌석 상태 조회 세 곳이 같은 순서를 따라야 한다. 세 곳에 같은 분기를 두면 한 곳만 고쳐도 아무도 모른다 -- 더 나쁜 것은 <b>틀리는
 * 방향이 조용하다</b>는 점이다. 분기를 잘못 지우면 대기열을 우회한 요청이 그대로 통과한다.
 *
 * <p>판정 자체는 여기 없다. 예매 기간과 대기열이 필요한지는 {@link PerformanceSalesPolicy}가, 토큰이 유효한지는 {@link AdmissionVerifier} 구현이 정한다. 이
 * class는 그 셋을 잇는 순서만 소유한다.
 */
@Component
@RequiredArgsConstructor
public class BookingEntryGuard {
    private final PerformanceSaleFinder performanceSaleFinder;
    private final AdmissionVerifier admissionVerifier;

    /**
     * 판매 정책이 없으면 404, 예매 기간 밖이면 E3001/E3002를 던진다. 대기열이 필요한 회차면 토큰을 검증하고, 실패하면 admission이 소유한 예외를 던진다 -- 토큰 없음 (E8000),
     * 만료(E8001), 그 밖의 검증 실패(E8002). 셋 다 HTTP 403이다.
     *
     * @return 통과한 회차의 판매 정책. 호출자가 hold 한도 같은 나머지 판정에 그대로 쓴다
     */
    public PerformanceSalesPolicy check(
            final Long performanceId,
            final Long memberId,
            final @Nullable String admissionToken,
            final LocalDateTime now) {
        final PerformanceSalesPolicy policy = performanceSaleFinder.requirePolicy(performanceId);
        policy.ensureWithinBookingWindow(now);
        if (policy.isQueueRequired(now)) {
            admissionVerifier.verify(performanceId, memberId, admissionToken);
        }
        return policy;
    }
}
