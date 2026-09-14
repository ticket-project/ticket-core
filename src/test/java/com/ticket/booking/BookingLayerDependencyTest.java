package com.ticket.booking;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * booking이 모듈 바로 아래 계층형으로 재편되면서 없어진 {@code booking.common}의 의존 규칙을 이어받는다.
 *
 * <p>{@code BookingAuditedEntity}와 {@code RequestedSeatIds}는 {@code booking.domain}으로 돌아가 {@code
 * com.ticket.DomainIsolationTest}의 {@code ..domain..} 패턴이 다시 덮는다(다른 BC 참조 금지). 반면 락 계약 넷은 {@code
 * booking.application}에 있어 그 패턴 밖이라, 여기서 기술 의존만 따로 막는다.
 *
 * <p>계층 방향도 함께 고정한다. 업무별 폴더가 사라진 자리에서 "어느 계층이 어느 계층을 참조해도 되는가"를 말해 주는 것이 폴더 이름뿐이라, 그 방향을 실행 가능한
 * 규칙으로 남긴다.
 */
@AnalyzeClasses(
        packages = "com.ticket.booking",
        importOptions = {ImportOption.DoNotIncludeTests.class})
@SuppressWarnings("NonAsciiCharacters")
class BookingLayerDependencyTest {
    /** 잠글 대상을 업무 의미로 표현하는 계약이다. 저장 기술을 드러내지 않는다. */
    private static final String[] LOCK_CONTRACT_TYPES = {
        "com.ticket.booking.application.LockManager",
        "com.ticket.booking.application.LockKey",
        "com.ticket.booking.application.LockOptions",
        "com.ticket.booking.application.LockScope"
    };

    @ArchTest
    static final ArchRule 락_계약은_Redis_기술을_모른다 =
            noClasses()
                    .that()
                    .haveNameMatching(namePattern(LOCK_CONTRACT_TYPES))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.redisson..", "org.springframework.data.redis..")
                    .because("락 계약에 Redis 구현 타입을 노출하지 않는다 — key 형식과 임대 방식은 infrastructure가 정한다");

    @ArchTest
    static final ArchRule 락_계약은_web_계층을_모른다 =
            noClasses()
                    .that()
                    .haveNameMatching(namePattern(LOCK_CONTRACT_TYPES))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..web..", "org.springframework.web..")
                    .because("락 계약은 HTTP 계약을 모른다");

    @ArchTest
    static final ArchRule domain은_application과_infrastructure를_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage("com.ticket.booking.domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ticket.booking.application..",
                            "com.ticket.booking.infrastructure..",
                            "com.ticket.booking.endpoint..")
                    .because("업무 규칙과 상태는 조립·저장·HTTP를 모른다");

    @ArchTest
    static final ArchRule application은_infrastructure와_web을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage("com.ticket.booking.application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.ticket.booking.infrastructure..", "com.ticket.booking.endpoint..")
                    .because("application은 포트로만 밖을 부른다 — 구현 선택은 infrastructure가 갖는다");

    private static String namePattern(final String[] typeNames) {
        return Arrays.stream(typeNames).map(Pattern::quote).collect(Collectors.joining("|"));
    }
}
