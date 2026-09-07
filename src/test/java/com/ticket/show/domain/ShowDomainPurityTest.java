package com.ticket.show.domain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code show.domain}이 favorite(찜)를 모르게 한다.
 *
 * <p>찜(ShowLike)의 데이터와 불변식은 {@code com.ticket.favorite} module이 소유하고, show는 그
 * 공개 계약({@code ShowLikeQuery}/{@code ShowLikeCommand})을 호출해 응답을 조합한다 — 단
 * <b>어디서</b> 부르느냐가 중요하다. 조합은 {@code show.application}의 조회 서비스가 한다
 * ({@link com.ticket.show.application.show.query.GetShowDetailUseCase},
 * {@link com.ticket.show.application.showlike.query.GetMyShowLikesUseCase} 등). {@code show.domain}
 * (entity·도메인 서비스)이 찜 개념을 알면 도메인 모델이 다른 Bounded Context를 알게 되므로 금지한다 —
 * 예를 들어 {@code Show}가 {@code List<ShowLike>}를 갖거나 도메인 서비스가 favorite의 Repository를
 * 주입받는 것.
 *
 * <p>반대 방향({@code favorite -> show})은 이 테스트가 아니라
 * {@code com.ticket.ModularityTests}(module 의존 DAG)가 막는다 — favorite의
 * {@code allowedDependencies}가 비어 있어 show를 향한 어떤 참조도 통과하지 못한다.
 *
 * <p>배경은 {@code docs/adr/0006-bounded-context-module-boundaries.md}가 원본이다.
 */
@AnalyzeClasses(
        packages = "com.ticket.show.domain",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@SuppressWarnings("NonAsciiCharacters")
class ShowDomainPurityTest {

    @ArchTest
    static final ArchRule show_domain은_favorite를_참조하지_않는다 = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.ticket.favorite..")
            .because("show.domain은 다른 Bounded Context(favorite)를 몰라야 한다 — 조합은 "
                    + "show.application이 favorite의 공개 계약을 호출해서 한다");
}
