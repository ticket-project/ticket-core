package com.ticket.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code com.ticket.core} 내부 계층(api -> infra 금지)을 고정한다.
 *
 * <p>{@code com.ticket.ModularityTests}와 겹치지 않는다 — {@code ModularityTests}는
 * {@code com.ticket.core} 전체를 legacy로 분석 대상에서 빼므로, 여기서 보는 규칙은 Spring
 * Modulith가 전혀 검사하지 않는다. {@code com.ticket.core}가 7개 module로 완전히 옮겨지면 이
 * 파일도 함께 지운다.
 *
 * <p>{@code com.ticket.core.config}(security 하위 포함)는 8번째 module {@code com.ticket.config}로
 * 전부 옮겨져 지금은 class가 없다 — 그 안에서 identity 인증 infra를 참조하지 않는지 보던
 * {@code security_should_not_depend_on_auth_infra} 규칙은 검사 대상이 없어져 지웠다. 같은 참조는
 * 이제 Spring Modulith가 {@code com.ticket.config}의 {@code @NamedInterface} 경계로 검증한다
 * ({@code com.ticket.ModularityTests}, {@code com.ticket.config}의 package-info 참고).
 */
@AnalyzeClasses(
        packages = "com.ticket.core",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class CoreApiArchitectureTest {

    @ArchTest
    static final ArchRule core_api_should_not_depend_on_infra =
            noClasses()
                    .that().resideInAnyPackage("com.ticket.core.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.ticket.core.infra..");
}
