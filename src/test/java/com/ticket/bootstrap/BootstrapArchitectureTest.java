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
 *
 * <p>{@code com.ticket.ModularityTests}와 겹치지 않는다 — {@code com.ticket.bootstrap}은
 * {@code com.ticket.core}/{@code storage}/{@code support}와 함께 legacy로 분석 대상에서 빠진다.
 * Spring Modulith는 실행 모듈이 도메인을 우회하는지, background trigger가 정해진 package에
 * 있는지 전혀 검사하지 않는다. bootstrap이 7개 module 체계 안으로 정리되는 날 이 파일도
 * 다시 검토한다.
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
