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

    @ArchTest
    static final ArchRule core_domain_module_should_not_contain_infra_packages =
            noClasses()
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

    @ArchTest
    static final ArchRule infra_패키지_밖에서는_websocket_messaging에_직접_의존하지_않는다 =
            noClasses()
                    .that().resideOutsideOfPackages("..infra..")
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
