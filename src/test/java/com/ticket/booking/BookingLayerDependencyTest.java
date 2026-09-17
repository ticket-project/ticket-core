package com.ticket.booking;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * booking 고유의 기술 격리 규칙이다 — application.concurrency의 락 계약이 저장 기술과 HTTP를 모르게 한다.
 *
 * <p><b>계층 방향 규칙은 여기 없다.</b> "domain은 application·infrastructure·endpoint를 모른다"류는 booking만의 사정이 아니라
 * 계층을 가진 모든 module에 같은 뜻이라, {@code com.ticket.ArchitectureRulesTest}가 module 목록 하나로 전부 덮는다. 예전에는 이
 * 규칙이 booking에만 걸려 있어 show·like·member·venue·payment에는 같은 보호가 없었다.
 *
 * <p>여기 남은 규칙이 전역으로 올라가지 않는 이유: 대상이 {@code booking.application.concurrency} 한 package이고, 다른
 * module에는 대응하는 package가 없다. 락 계약은 {@code application} 아래에 있어 {@code ..domain..} 패턴 밖이다.
 */
@AnalyzeClasses(
        packages = "com.ticket.booking",
        importOptions = {ImportOption.DoNotIncludeTests.class})
@SuppressWarnings("NonAsciiCharacters")
class BookingLayerDependencyTest {
    /**
     * 잠글 대상을 업무 의미로 표현하는 계약이 사는 package다. 저장 기술을 드러내지 않는다.
     *
     * <p>타입 이름 넷을 나열하지 않고 package로 지목한다 — 계약이 늘 때 목록을 같이 고치는 것을 잊으면 새 타입만 규칙 밖에 남는다.
     */
    private static final String LOCK_CONTRACT_PACKAGE =
            "com.ticket.booking.application.concurrency..";

    @ArchTest
    static final ArchRule 락_계약은_Redis_기술을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage(LOCK_CONTRACT_PACKAGE)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.redisson..", "org.springframework.data.redis..")
                    .because("락 계약에 Redis 구현 타입을 노출하지 않는다 — key 형식과 임대 방식은 infrastructure가 정한다");

    @ArchTest
    static final ArchRule 락_계약은_endpoint_계층을_모른다 =
            noClasses()
                    .that()
                    .resideInAPackage(LOCK_CONTRACT_PACKAGE)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..endpoint..", "org.springframework.web..")
                    .because("락 계약은 HTTP 계약을 모른다");
}
