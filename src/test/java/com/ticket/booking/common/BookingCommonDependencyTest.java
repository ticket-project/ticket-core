package com.ticket.booking.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * {@code booking.common}은 하위 package 없이 평탄하다. 그래서 "어떤 계층이 어떤 계층을 참조해도 되는가"를 package 경계로 표현할 수 없고, 이
 * 테스트가 그 자리를 대신한다.
 *
 * <p>특히 {@code BookingAuditedEntity}와 {@code RequestedSeatIds}는 {@code booking.domain}에 있던 시절
 * {@code com.ticket.DomainIsolationTest}의 {@code ..domain..} 패턴이 덮고 있었다. 평탄화로 그 패턴에서 빠졌으므로, 여기서 같은
 * 보장(다른 Bounded Context를 참조하지 않는다)을 이어받는다.
 *
 * <p>반대로 {@code common} 전체를 domain처럼 취급하지는 않는다. 이 package에는 Redisson·Spring Data Redis에 직접 의존하는
 * 구현({@code RedissonLockManager}, {@code RedisKeyExpirationListener}, {@code
 * RedisExpirationListenerConfig})이 함께 있고, 그것이 이 package의 설계다. 기술 의존을 금지하는 대상은 계약·기반 타입 여섯 개뿐이다.
 */
@AnalyzeClasses(
        packages = "com.ticket",
        importOptions = {ImportOption.DoNotIncludeTests.class})
@SuppressWarnings("NonAsciiCharacters")
class BookingCommonDependencyTest {
    /** {@code com.ticket.DomainIsolationTest}와 같은 목록이다. */
    private static final String[] OTHER_BOUNDED_CONTEXTS = {
        "com.ticket.show..",
        "com.ticket.venue..",
        "com.ticket.like..",
        "com.ticket.member..",
        "com.ticket.payment.."
    };

    /** booking 안의 업무별 package다. common은 이 중 어느 것도 참조하지 않는다. */
    private static final String[] BOOKING_CAPABILITY_PACKAGES = {
        "com.ticket.booking.order..",
        "com.ticket.booking.hold..",
        "com.ticket.booking.selection..",
        "com.ticket.booking.seat..",
        "com.ticket.booking.salespolicy..",
        "com.ticket.booking.admission..",
        "com.ticket.booking.ticket.."
    };

    /** 락 계약과 공통 기반 타입이다. Redis 구현({@code Redisson*}, {@code Redis*})은 여기 넣지 않는다. */
    private static final String[] CONTRACT_AND_BASE_TYPES = {
        "com.ticket.booking.common.LockManager",
        "com.ticket.booking.common.LockKey",
        "com.ticket.booking.common.LockOptions",
        "com.ticket.booking.common.LockScope",
        "com.ticket.booking.common.BookingAuditedEntity",
        "com.ticket.booking.common.RequestedSeatIds"
    };

    @ArchTest
    static final ArchRule common은_다른_BC를_참조하지_않는다 =
            noClasses()
                    .that()
                    .resideInAPackage("com.ticket.booking.common")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(OTHER_BOUNDED_CONTEXTS)
                    .because(
                            "booking.common은 booking 안의 공통 기반이다 — 다른 Bounded Context를 알면 조합이 기반 코드로 새어 들어온다");

    @ArchTest
    static final ArchRule common은_업무별_package를_참조하지_않는다 =
            noClasses()
                    .that()
                    .resideInAPackage("com.ticket.booking.common")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(BOOKING_CAPABILITY_PACKAGES)
                    .because(
                            "공통 기반이 특정 업무 구현을 직접 참조하면 기반과 업무가 서로를 잡아 순환이 된다 — Redis 만료 수신부처럼 업무로 나가야 하는 흐름은 common이 소유한 처리 계약(RedisKeyExpirationHandler)으로 위임한다");

    @ArchTest
    static final ArchRule 락_계약과_기반_타입은_Redis_기술을_모른다 =
            noClasses()
                    .that()
                    .haveNameMatching(namePattern(CONTRACT_AND_BASE_TYPES))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.redisson..", "org.springframework.data.redis..")
                    .because("평탄화는 package만 합친 것이다 — 락 계약에 Redis 구현 타입을 노출하지 않는다");

    @ArchTest
    static final ArchRule 락_계약과_기반_타입은_web_계층을_모른다 =
            noClasses()
                    .that()
                    .haveNameMatching(namePattern(CONTRACT_AND_BASE_TYPES))
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..web..", "org.springframework.web..")
                    .because("RequestedSeatIds와 BookingAuditedEntity는 HTTP 계약을 모른다");

    private static String namePattern(final String[] typeNames) {
        return Arrays.stream(typeNames).map(Pattern::quote).collect(Collectors.joining("|"));
    }
}
