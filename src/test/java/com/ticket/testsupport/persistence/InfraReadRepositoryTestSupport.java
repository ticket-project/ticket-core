package com.ticket.testsupport.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.ticket.venue.persistence.SeatRepositoryAdapter;
import com.ticket.venue.persistence.VenueRepositoryAdapter;

/**
 * Querydsl 조회 Repository 테스트의 베이스다.
 *
 * <p>venue 공개 계약({@code VenueLookupApi}/{@code VenueSeatLookupApi})의 구현을 빈으로 올린다 — 이 베이스를 쓰는 테스트가 그
 * 계약을 주입받으면 없을 때 컨텍스트 기동부터 실패한다(observed-failures 참고). 두 계약은 이제 {@link VenueRepositoryAdapter} 하나가
 * 함께 구현한다.
 *
 * <p>show의 정렬·커서·판매 상태 조건 helper는 더 이상 별도 빈이 아니다 — {@code ShowQueryRepository}가 private 메서드로 갖는다.
 */
@Import({
    VenueRepositoryAdapter.class,
    SeatRepositoryAdapter.class,
    InfraReadRepositoryTestSupport.InfraJpaRepositoriesTestConfig.class
})
public abstract class InfraReadRepositoryTestSupport extends ReadRepositoryTestSupport {
    /**
     * RepositoryAdapter가 쓰는 Spring Data 인터페이스를 테스트 컨텍스트에 올린다.
     *
     * <p>module을 추출할 때마다 이 목록에 package를 하나씩 추가해 왔다(Task 5~7). 앞으로도 module 추출이 이어지므로(Task 9~10 등) 넓은
     * base package 하나로 통합해, 추출할 때마다 이 공유 파일을 건드려 병렬 작업 간 merge 충돌이 나는 일을 없앤다. {@code com.ticket}
     * 전체를 스캔해도 실제 {@code @Repository} Spring Data 인터페이스가 아닌 클래스는 걸러지므로 이전보다 넓게 잡아도 안전하다.
     */
    @Configuration
    @EnableJpaRepositories(basePackages = "com.ticket")
    static class InfraJpaRepositoriesTestConfig {}
}
