package com.ticket;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.LinkedHashSet;
import java.util.Set;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 6개 Bounded Context(BC) 전부에서 {@code <bc>}의 모든 {@code domain} 계층이 다른 BC를 모르게 한다.
 *
 * <p>검사 대상 패턴은 {@code com.ticket.<bc>..domain..}이다 — 모듈 직속 {@code booking.domain}과 그 아래 묶음 ({@code
 * booking.domain.order}, {@code show.domain.performance} 등)을 함께 잡는다. 한쪽만 검사하면 검사 대상이 비어 조용히 통과하는
 * 구간이 생긴다.
 *
 * <p>계층 방향(domain → application/infrastructure/endpoint 금지 등)은 {@code
 * com.ticket.ArchitectureRulesTest}가 전 module에 대해 맡고, booking 고유의 락 계약 규칙만 {@code
 * com.ticket.booking.BookingLayerDependencyTest}에 남는다.
 *
 * <p>도메인 계층이 다른 BC를 알면 조합이 domain 안으로 새어 들어와 BC 사이 결합이 생긴다. 조합은 {@code <bc>.application}이 상대 BC의 공개
 * 계약을 호출해서 수행한다.
 *
 * <p>{@code shared}는 {@code @Modulith(sharedModules = "shared")}로 모든 module에 허용되는 기술 모듈이라 이 규칙의 대상이
 * 아니다 — 실측상 각 BC의 domain이 참조하는 유일한 cross-module 타입도 {@code
 * com.ticket.shared.exception.InvalidRequestException}뿐이다. (최상위 {@code web}/{@code error} module은
 * ADR 0011이 {@code shared} 하나로 합쳐 더 이상 없다.)
 *
 * <p>module(BC) 사이 의존 자체(어떤 module이 어떤 module을 참조할 수 있는지)는 이 테스트가 아니라 {@code
 * com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG}가 막는다. 이 테스트는 그 의존이 domain 계층으로 새는 것만 막는다 —
 * application이 참조하는 것은 허용된다.
 *
 * <p><b>이 테스트가 보장하지 않는 것</b>: domain의 기술 의존이다. 실제로 6개 BC 전부의 domain이 {@code
 * jakarta.persistence}(JPA entity)와 {@code org.springframework.data.*}(auditing)를 참조하고, {@code
 * booking}/{@code show}의 domain 클래스 일부는 {@code @Component}/ {@code @Service}를 갖는다. 계층 방향(domain이
 * application·infrastructure·endpoint를 모르는 것)은 {@code com.ticket.ArchitectureRulesTest}가 전 module에
 * 대해 막는다.
 *
 * <p>배경은 {@code docs/adr/0006-bounded-context-module-boundaries.md} §1이 원본이다.
 */
@AnalyzeClasses(
        packages = "com.ticket",
        importOptions = {ImportOption.DoNotIncludeTests.class})
@SuppressWarnings("NonAsciiCharacters")
class DomainIsolationTest {
    /** 기술 모듈을 제외한 6개 BC다. {@code docs/architecture.md}의 "Bounded Context" 절이 원본이다. */
    private static final Set<String> BOUNDED_CONTEXTS =
            Set.of("booking", "show", "venue", "like", "member", "payment");

    @ArchTest
    static final ArchRule booking_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("booking");

    @ArchTest
    static final ArchRule show_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("show");

    @ArchTest
    static final ArchRule venue_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("venue");

    @ArchTest
    static final ArchRule like_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("like");

    @ArchTest
    static final ArchRule member_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("member");

    @ArchTest
    static final ArchRule payment_domain은_다른_BC를_참조하지_않는다 = domainDoesNotDependOnOtherBc("payment");

    private static ArchRule domainDoesNotDependOnOtherBc(final String bc) {
        final Set<String> others = new LinkedHashSet<>(BOUNDED_CONTEXTS);
        others.remove(bc);

        final String[] otherBcPackages =
                others.stream().map(other -> "com.ticket." + other + "..").toArray(String[]::new);

        return noClasses()
                .that()
                .resideInAPackage("com.ticket." + bc + "..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(otherBcPackages)
                .because(
                        bc
                                + ".domain은 다른 Bounded Context를 몰라야 한다 — 조합은 "
                                + bc
                                + ".application이 상대 BC의 공개 계약을 호출해서 한다");
    }
}
