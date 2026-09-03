package com.ticket.showlike;

import com.ticket.catalog.ShowLookup;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import com.ticket.identity.MemberLookup;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다. 기본값(true)으로
 * 두면 이 STANDALONE 테스트가 별도로 {@code verify()}를 실행하는데, showlike가 의존하는 {@code
 * com.ticket.core.support.exception}이 legacy {@code com.ticket.core} 아래에 있고, {@code ShowLike}
 * entity·{@code GetMyShowLikesUseCase} 등 이번 Task 9에서 옮기지 못한 legacy showlike 코드(package-info
 * 참고)가 이 module과 얽혀 "showlike → core"·"core → showlike" 순환으로 오탐된다. {@code spring.modulith.detection-strategy}를 전역으로 바꾸는
 * 대신 이 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은 미래 module이
 * 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.showlike} package tree만 component-scan한다.
 * catalog {@code ShowLookup}, identity {@code MemberLookup}은 그 필터 밖이라 {@code @MockitoBean}으로
 * 대체한다. {@code ShowLikeRepository}의 구현({@code ShowLikeRepositoryAdapter})도 package-info에
 * 문서화한 대로 legacy {@code com.ticket.core.infra.showlike}에 남아 있어 같은 이유로 {@code
 * @MockitoBean}이 필요하다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
class ShowLikeModuleTests {

    @MockitoBean
    private ShowLookup showLookup;

    @MockitoBean
    private MemberLookup memberLookup;

    @MockitoBean
    private ShowLikeRepository showLikeRepository;

    @Test
    void bootstraps() {
    }
}
