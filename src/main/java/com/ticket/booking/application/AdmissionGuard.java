package com.ticket.booking.application;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.ticket.booking.application.port.AdmissionVerifier;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 입장을 요구하는 회차에서만 ticket-queue가 발급한 입장 토큰을 검증한다.
 *
 * <p><b>왜 별도 class인가.</b> "대기열이 필요한 회차인가"를 먼저 묻고 필요할 때만 토큰을 본다는 것은 업무 규칙이고, 예매 시작·좌석 선택·좌석 상태 조회 세
 * 곳이 같은 규칙을 따라야 한다. 세 곳에 같은 분기를 두면 한 곳만 고쳐도 아무도 모른다 -- 더 나쁜 것은 <b>틀리는 방향이 조용하다</b>는 점이다. 분기를 잘못 지우면
 * 대기열을 우회한 요청이 그대로 통과한다.
 *
 * <p>판정 자체는 여기 없다. 대기열이 필요한지는 {@link PerformanceSalesPolicy}가, 토큰이 유효한지는 {@link AdmissionVerifier}
 * 구현이 정한다. 이 class는 그 둘을 잇는 순서만 소유한다.
 */
@Component
@RequiredArgsConstructor
public class AdmissionGuard {
    private final AdmissionVerifier admissionVerifier;

    /**
     * 대기열이 필요 없는 회차면 아무것도 하지 않는다. 필요한 회차면 토큰을 검증하고, 실패하면 admission이 소유한 예외를 던진다 -- 토큰 없음 (E8000),
     * 만료(E8001), 그 밖의 검증 실패(E8002). 셋 다 HTTP 403이다.
     */
    public void verifyIfRequired(
            final PerformanceSalesPolicy policy,
            final Long performanceId,
            final Long memberId,
            final @Nullable String admissionToken,
            final LocalDateTime now) {
        if (!policy.isQueueRequired(now)) {
            return;
        }
        admissionVerifier.verify(performanceId, memberId, admissionToken);
    }
}
