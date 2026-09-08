package com.ticket.shared;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code com.ticket.shared}에는 다른 module이 호출하는 계약만 두고, bean을 등록하는 코드({@code
 * @Configuration}/{@code @Component})는 두지 않는다.
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
