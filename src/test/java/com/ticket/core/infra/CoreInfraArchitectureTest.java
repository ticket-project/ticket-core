package com.ticket.core.infra;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
