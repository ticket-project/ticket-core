package com.ticket.shared;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code com.ticket.shared}가 "가져다 쓸 수 있는" 모듈로 남게 한다.
 *
 * <p>{@code shared}에는 <b>다른 module이 호출하는 계약</b>만 둔다 — bean을 등록하는 코드는 두지
 * 않는다. 둘은 성질이 정반대다: 계약은 내가 불러 쓰는 것이고, {@code @Configuration}은 포함하는
 * 것만으로 나에게 적용되는 것이다. 한 module에 섞이면 {@code shared}를 참조하는 쪽이 Redisson·
 * Querydsl·Swagger 같은 기술 스택과 그 bean까지 강제로 함께 받는다.
 *
 * <p>{@code TicketApplication}이 {@code @Modulith(sharedModules = "shared")}를 선언하므로
 * {@code shared}는 <b>모든 {@code @ApplicationModuleTest}에 항상 포함된다.</b> 여기에 bean 등록이
 * 있으면 모든 module의 STANDALONE 테스트가 그 bean을 함께 띄우게 되고, 실제 Redis 연결처럼 무거운
 * 배선이 module 테스트의 성패를 좌우한다. 전역 기술 설정은 {@code com.ticket.config}가 소유한다.
 *
 * <p>{@code @ConfigurationProperties} 값 홀더({@code CorsProperties})는 금지 대상이 아니다 — 스스로
 * bean을 등록하지 않고 주입받아 읽는 값 타입이며, 등록은 그 값을 쓰는 module이
 * {@code @EnableConfigurationProperties}로 한다.
 *
 * <p>배경은 {@code docs/adr/0003-spring-modulith-application-module-boundaries.md} §6이 원본이다.
 */
@AnalyzeClasses(
        packages = "com.ticket.shared",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@SuppressWarnings("NonAsciiCharacters")
class SharedModulePurityTest {

    @ArchTest
    static final ArchRule shared에는_bean을_등록하는_코드를_두지_않는다 = noClasses()
            .should().beAnnotatedWith(Configuration.class)
            .orShould().beMetaAnnotatedWith(Component.class)
            .because("shared는 호출 대상 계약만 갖는다. bean 등록은 com.ticket.config가 소유한다 "
                    + "(sharedModules 선언 때문에 shared는 모든 module 테스트에 함께 뜬다)");
}
