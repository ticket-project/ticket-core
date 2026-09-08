package com.ticket;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 6개 Bounded Context(BC) 전부에서 {@code <bc>.domain}이 다른 BC를 모르게 한다.
 *
 * <p>{@code com.ticket.show.domain.ShowDomainPurityTest}를 대체한다 — 그 테스트는
 * {@code show.domain -> favorite} 한 방향만 막았고, {@code booking}/{@code member}/
 * {@code payment}/{@code venue}/{@code favorite}의 domain에는 대응 규칙이 없었다. 도메인
 * 계층이 다른 BC를 알면 조합이 domain 안으로 새어 들어와 BC 사이 결합이 생긴다 — 조합은 항상
 * {@code <bc>.application}이 상대 BC의 공개 계약(작은 interface + 불변 record)을 호출해서
 * 한다.
 *
 * <p>{@code shared}/{@code web}/{@code error}는 {@code @Modulith(sharedModules = ...)}로 모든
 * module에 허용되는 기술 모듈이라 이 규칙의 대상이 아니다 — 실측상 각 BC의 domain이 참조하는
 * 유일한 cross-module 타입도 {@code com.ticket.error.InvalidRequestException}뿐이다.
 *
 * <p>module(BC) 사이 의존 자체(어떤 module이 어떤 module을 참조할 수 있는지)는 이 테스트가
 * 아니라 {@code com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG}가 막는다. 이 테스트는 그
 * 의존이 domain 계층으로 새는 것만 막는다 — application이 참조하는 것은 허용된다.
 *
 * <p>배경은 {@code docs/adr/0006-bounded-context-module-boundaries.md} §1이 원본이다.
 */
@AnalyzeClasses(
        packages = "com.ticket",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@SuppressWarnings("NonAsciiCharacters")
class DomainPurityTest {

    /** 기술 모듈을 제외한 6개 BC다. {@code docs/architecture.md}의 "Bounded Context와 Aggregate" 절이 원본이다. */
    private static final Set<String> BOUNDED_CONTEXTS = Set.of(
            "booking", "show", "venue", "favorite", "member", "payment");

    @ArchTest
    static final ArchRule booking_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("booking");

    @ArchTest
    static final ArchRule show_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("show");

    @ArchTest
    static final ArchRule venue_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("venue");

    @ArchTest
    static final ArchRule favorite_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("favorite");

    @ArchTest
    static final ArchRule member_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("member");

    @ArchTest
    static final ArchRule payment_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("payment");

    private static ArchRule domainDoesNotDependOnOtherBc(final String bc) {
        final Set<String> others = new LinkedHashSet<>(BOUNDED_CONTEXTS);
        others.remove(bc);

        final String[] otherBcPackages = others.stream()
                .map(other -> "com.ticket." + other + "..")
                .toArray(String[]::new);

        return noClasses()
                .that().resideInAPackage("com.ticket." + bc + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(otherBcPackages)
                .because(bc + ".domain은 다른 Bounded Context를 몰라야 한다 — 조합은 "
                        + bc + ".application이 상대 BC의 공개 계약을 호출해서 한다");
    }
}
