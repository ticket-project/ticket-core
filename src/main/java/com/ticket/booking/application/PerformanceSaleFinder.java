package com.ticket.booking.application;

import org.springframework.stereotype.Component;

import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicyRepository;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * 회차의 판매 정책을 찾고, 없으면 "회차를 찾을 수 없다"로 끊는다.
 *
 * <p><b>왜 별도 class인가.</b> 같은 조회가 다섯 곳(예매 시작, 좌석 선택, 좌석 상태 조회, 잔여석 조회, 예매 방식 조회)에서 필요하고, 다섯 곳 모두
 * "없으면 404" 라는 같은 결론을 낸다. 같은 결론을 다섯 번 적으면 문구가 어긋나는 순간 클라이언트가 보는 메시지가 화면마다 달라진다 -- 그런데 그 어긋남은 컴파일러도
 * 테스트도 잡아 주지 않는다.
 *
 * <p><b>왜 Repository에 두지 않았나.</b> {@link PerformanceSalesPolicyRepository}는 "조회 결과가 없다는 사실만 알려 주고,
 * 그것을 어떤 오류로 볼지는 호출하는 쪽이 정한다"는 계약이다. 그 판단을 Repository로 내리면 다른 뜻으로 쓰고 싶은 호출자가 생겼을 때 되돌릴 자리가 없어진다.
 * 그래서 결론은 application에 두고, Repository는 {@code Optional} 그대로 남긴다.
 *
 * <p><b>이 class는 조회만 한다.</b> 접수 기간·Hold 한도·대기열 요구 여부 같은 판정은 돌려받은 {@link PerformanceSalesPolicy}가
 * 소유한다 -- 호출자가 필요한 판정을 직접 부른다. 그래야 use case를 읽을 때 무엇을 검증하는지가 보인다.
 */
@Component
@RequiredArgsConstructor
public class PerformanceSaleFinder {
    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    /**
     * @return 회차의 판매 정책. 이 조회는 회차 존재 확인을 겸한다
     * @throws NotFoundException 그 회차의 판매 정책이 없을 때
     */
    public PerformanceSalesPolicy findPolicy(final Long performanceId) {
        return performanceSalesPolicyRepository
                .findById(performanceId)
                .orElseThrow(
                        () -> new NotFoundException("회차 판매 정책을 찾을 수 없습니다. id=" + performanceId));
    }
}
