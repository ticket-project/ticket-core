package com.ticket.bootstrap;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 실행 모듈의 경계를 고정한다.
 *
 * <p>bootstrap은 조립과 실행 설정만 맡는다. 유스케이스와 어댑터를 부르되 도메인 엔티티나
 * 저장소에 직접 닿지 않는다. 그렇게 되면 실행 모듈이 업무 규칙을 우회하게 된다.
 */
@AnalyzeClasses(
        packages = "com.ticket.bootstrap",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@SuppressWarnings("NonAsciiCharacters")
class BootstrapArchitectureTest {

    @ArchTest
    static final ArchRule bootstrap은_도메인에_직접_닿지_않는다 =
            noClasses()
                    .that().resideInAPackage("com.ticket.bootstrap..")
                    .should().dependOnClassesThat().resideInAPackage("com.ticket.core.domain..");

    @ArchTest
    static final ArchRule background_트리거는_worker_패키지에_모은다 =
            classes()
                    .that().haveSimpleNameEndingWith("Trigger")
                    .should().resideInAPackage("com.ticket.bootstrap.worker..");
}
