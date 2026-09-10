package com.ticket.shared;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * shared의 공개 계약에는 bean을 등록하지 않고, 공통 실행 코드는 {@code shared.config}와
 * {@code shared.exception.handler}에만 둔다.
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
    static final ArchRule shared의_공개_계약에는_bean을_등록하지_않는다 = noClasses()
            .that().resideOutsideOfPackages(
                    "com.ticket.shared.config..",
                    "com.ticket.shared.exception.handler.."
            )
            .should().beAnnotatedWith(Configuration.class)
            .orShould().beMetaAnnotatedWith(Component.class)
            .because("공개 계약에는 상태 없는 타입만 두고, 공통 실행 코드는 정해진 내부 패키지에만 둔다");
}
