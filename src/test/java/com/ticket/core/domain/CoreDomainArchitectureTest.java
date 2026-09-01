package com.ticket.core.domain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(
        packages = "com.ticket.core",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
@SuppressWarnings("NonAsciiCharacters")
class CoreDomainArchitectureTest {

    // @AnalyzeClasses(packages = "com.ticket.core")는 multi-module 시절 core-domain 모듈
    // 자신의 classpath만 스캔했다(core-domain은 core-infra에 의존하지 않으므로 infra 클래스는
    // 애초에 스캔 대상에 없었다). 단일 Gradle 프로젝트로 합쳐지며 이 스캔이 core-infra까지 포함한
    // 전체 classpath를 보게 됐고, .that() 필터 없이 "no classes"라고 쓰면 스캔된 모든 클래스를
    // 대상으로 검사해 실제 infra 패키지의 클래스 전부(101개)가 위반으로 잡힌다. 이 규칙의 실제
    // 의도("domain 모듈은 infra 패키지를 담지 않는다")대로 domain 패키지의 클래스만 검사하도록
    // 필터를 되살린다.
    @ArchTest
    static final ArchRule core_domain_module_should_not_contain_infra_packages =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().resideInAnyPackage("com.ticket.core.infra..", "..domain.*.infra..");

    @ArchTest
    static final ArchRule infra_패키지_밖에서는_redisson에_직접_의존하지_않는다 =
            noClasses()
                    .that().resideOutsideOfPackages("..infra..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.redisson..");

    @ArchTest
    static final ArchRule infra_밖에서는_spring_data_redis를_직접_참조할_수_없다 =
            noClasses()
                    .that().resideOutsideOfPackages("..infra..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.redis..");

    // WebSocketAuthInterceptor(com.ticket.core.config.security)는 core-api 모듈에 있었고,
    // core-domain은 core-api에 의존하지 않아 multi-module 시절엔 이 스캔에 잡히지 않았다. 단일
    // 프로젝트 통합으로 스캔 범위가 넓어지며 처음 노출됐다. 바로 아래 http-interface-client
    // 규칙이 이미 "com.ticket.core.config.."를 어댑터에 준하는 예외로 취급하고 있으므로(보안
    // 설정이 프레임워크 인터페이스를 직접 구현/의존하는 것을 허용), 같은 예외를 여기도 일관되게
    // 적용한다.
    @ArchTest
    static final ArchRule infra_패키지_밖에서는_websocket_messaging에_직접_의존하지_않는다 =
            noClasses()
                    .that().resideOutsideOfPackages("..infra..", "com.ticket.core.config..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.messaging..");

    @ArchTest
    static final ArchRule infra_패키지_밖에서는_http_interface_client에_직접_의존하지_않는다 =
            noClasses()
                    .that().resideOutsideOfPackages("..infra..", "com.ticket.core.config..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web.service.annotation..");


    @ArchTest
    static final ArchRule core_domain은_scheduler를_소유하지_않는다 =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should().beAnnotatedWith(Scheduled.class);

    @ArchTest
    static final ArchRule core_domain은_transaction_event_listener를_소유하지_않는다 =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
                    .should().beAnnotatedWith(TransactionalEventListener.class);


}
