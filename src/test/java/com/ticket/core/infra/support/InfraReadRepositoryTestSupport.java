package com.ticket.core.infra.support;

import com.ticket.show.infrastructure.SaleDisplayStatusPredicateFactory;
import com.ticket.show.infrastructure.QuerydslShowConditionBuilder;
import com.ticket.show.infrastructure.QuerydslShowCursorConditionBuilder;
import com.ticket.show.infrastructure.QuerydslShowPredicates;
import com.ticket.show.infrastructure.QuerydslShowSortResolver;
import com.ticket.venue.application.VenueLookupService;
import com.ticket.venue.application.VenueSeatLookupService;
import com.ticket.venue.infrastructure.QuerydslVenueSeatReadRepository;
import com.ticket.venue.infrastructure.QuerydslVenueSummaryReadRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Querydsl 조회 어댑터 테스트의 베이스다. 조건 생성·정렬·커서 헬퍼를 빈으로 올린다.
 *
 * <p>{@code QuerydslShowConditionBuilder}가 Region 검색 조건을 venueId로 바꾸기 위해
 * {@code VenueLookup}을 주입받으므로, venue module의 공개 계약 구현 4개도 여기서 함께 올린다 —
 * 없으면 이 베이스를 쓰는 booking 테스트까지 컨텍스트 기동에 실패한다(observed-failures 참고).
 */
@Import({
        QuerydslShowPredicates.class,
        SaleDisplayStatusPredicateFactory.class,
        QuerydslShowConditionBuilder.class,
        QuerydslShowSortResolver.class,
        QuerydslShowCursorConditionBuilder.class,
        VenueLookupService.class,
        QuerydslVenueSummaryReadRepository.class,
        VenueSeatLookupService.class,
        QuerydslVenueSeatReadRepository.class,
        InfraReadRepositoryTestSupport.InfraJpaRepositoriesTestConfig.class
})
public abstract class InfraReadRepositoryTestSupport extends ReadRepositoryTestSupport {

    /**
     * RepositoryAdapter가 쓰는 Spring Data 인터페이스를 테스트 컨텍스트에 올린다.
     *
     * <p>module을 추출할 때마다 이 목록에 package를 하나씩 추가해 왔다(Task 5~7). 앞으로도 module
     * 추출이 이어지므로(Task 9~10 등) 넓은 base package 하나로 통합해, 추출할 때마다 이 공유 파일을
     * 건드려 병렬 작업 간 merge 충돌이 나는 일을 없앤다. {@code com.ticket} 전체를 스캔해도 실제
     * {@code @Repository} Spring Data 인터페이스가 아닌 클래스는 걸러지므로 이전보다 넓게 잡아도
     * 안전하다.
     */
    @Configuration
    @EnableJpaRepositories(basePackages = "com.ticket")
    static class InfraJpaRepositoriesTestConfig {
    }
}
