package com.ticket.core;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.ticket.core",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class CoreApiArchitectureTest {

    @ArchTest
    static final ArchRule security_should_not_depend_on_auth_infra =
            noClasses()
                    .that().resideInAnyPackage("com.ticket.core.config.security..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.ticket.core.infra.auth..");

    @ArchTest
    static final ArchRule core_api_should_not_depend_on_infra =
            noClasses()
                    .that().resideInAnyPackage("com.ticket.core.api..", "com.ticket.core.config..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.ticket.core.infra..");
}
