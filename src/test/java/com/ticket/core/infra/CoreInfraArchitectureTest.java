package com.ticket.core.infra;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@code com.ticket.core} 아래에 {@code ..domain.*.infra..} 모양의 package를 두지 않는다 —
 * domain 하위에 숨은 infra 구현이 생기는 것을 막는다.
 *
 * <p>{@code com.ticket.ModularityTests}와 겹치지 않는다 — {@code com.ticket.core}는 legacy로
 * 분석 대상에서 빠지므로 Spring Modulith는 이 package 배치를 전혀 검사하지 않는다.
 * {@code com.ticket.core}가 7개 module로 완전히 옮겨지면 이 파일도 함께 지운다.
 */
@AnalyzeClasses(
        packages = "com.ticket.core",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class CoreInfraArchitectureTest {

    @ArchTest
    static final ArchRule core_infra_module_should_not_contain_domain_infra_packages =
            noClasses()
                    .should().resideInAnyPackage("..domain.*.infra..");
}
