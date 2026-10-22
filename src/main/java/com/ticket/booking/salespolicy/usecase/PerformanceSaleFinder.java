package com.ticket.booking.salespolicy.usecase;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.booking.exception.PerformanceSalesPolicyNotFoundException;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicy;
import com.ticket.booking.salespolicy.domain.PerformanceSalesPolicyRepository;
import com.ticket.shared.api.CacheNames;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * 회차의 판매 정책을 찾고, 없으면 "회차를 찾을 수 없다"로 끊는다.
 *
 * <p><b>왜 별도 class인가.</b> 같은 조회가 세 곳(예매 진입 검사 {@code BookingEntryGuard}, 잔여석 조회, 진입 방식 조회)에서 필요하고, 세 곳 모두 "없으면 404" 라는
 * 같은 결론을 낸다. 같은 결론을 여러 번 적으면 문구가 어긋나는 순간 클라이언트가 보는 메시지가 화면마다 달라진다 -- 그런데 그 어긋남은 컴파일러도 테스트도 잡아 주지 않는다.
 *
 * <p><b>왜 Repository에 두지 않았나.</b> {@link PerformanceSalesPolicyRepository}는 "조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는
 * 쪽이 정한다"는 계약이다. 그 판단을 Repository로 내리면 다른 뜻으로 쓰고 싶은 호출자가 생겼을 때 되돌릴 자리가 없어진다. 그래서 결론은 application에 두고, Repository는
 * {@code Optional} 그대로 남긴다.
 *
 * <p><b>이 class는 조회만 한다.</b> 예매 기간·Hold 한도·대기열 요구 여부 같은 판정은 돌려받은 {@link PerformanceSalesPolicy}가 소유한다 -- 호출자가 필요한 판정을
 * 직접 부른다. 그래야 use case를 읽을 때 무엇을 검증하는지가 보인다.
 *
 * <p><b>판매 정책은 회차별로 1초 재사용한다({@link CacheNames#SALES_POLICY}).</b> 예매 사용자는 좌석 화면·좌석 선택·주문 생성에서 진입 검사를 세 번 받고, 매번 같은 행을
 * DB에서 읽었다. 운영의 한계가 Core CPU라(gatling-test capacity-log 2026-10-22) 이 반복 읽기를 줄인다. 검사는 요청마다 그대로 한다 — 줄이는 것은 읽기뿐이다. 판매
 * 기간·대기열 변경은 서버마다 최대 1초 늦게 반영된다.
 *
 * <p>캐시는 {@link PerformanceSalesPolicy} 인스턴스를 여러 요청이 함께 쓴다. 이 엔티티에는 상태를 바꾸는 메서드도 다른 엔티티와의 연관도 없고 저장소에 {@code save}도 없어서
 * 그대로 담는다. 상태를 바꾸는 메서드를 넣으면 캐시에는 값 객체를 담도록 바꿔야 한다. 캐시는 다른 bean에서 부를 때만 걸린다.
 */
@Component
@RequiredArgsConstructor
public class PerformanceSaleFinder {
    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;

    /**
     * @return 회차의 판매 정책. 이 조회는 회차 존재 확인을 겸한다
     * @throws NotFoundException 그 회차의 판매 정책이 없을 때. 예외는 캐시되지 않는다
     */
    @Cacheable(cacheNames = CacheNames.SALES_POLICY, key = "#performanceId", sync = true)
    public PerformanceSalesPolicy requirePolicy(final Long performanceId) {
        return performanceSalesPolicyRepository
                .findById(performanceId)
                .orElseThrow(() -> new PerformanceSalesPolicyNotFoundException(performanceId));
    }
}
