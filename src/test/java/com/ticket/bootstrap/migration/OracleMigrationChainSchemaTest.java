package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.ticket.testsupport.TestContainerImages;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * {@link MigrationChainSchemaTest}의 Oracle 판이다. 운영과 같은 방언의 migration이 빈 Oracle에서 V1부터 끝까지 적용되고, 그 결과가 entity 매핑과 맞는지
 * 본다. Docker가 없으면 건너뛴다 — 그때는 Oracle 경로가 미검증이다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("NonAsciiCharacters")
class OracleMigrationChainSchemaTest {
    @Container
    private static final OracleContainer ORACLE = new OracleContainer(TestContainerImages.ORACLE);

    @Test
    void 빈_Oracle에_migration만_적용한_스키마가_entity_매핑과_맞다() {
        ModulithFlywayTestSupport.applyMigrations(
                ORACLE.getJdbcUrl(),
                ORACLE.getUsername(),
                ORACLE.getPassword(),
                "oracle",
                MigratedSchema.MODULES_IN_RUNTIME_ORDER);

        assertThatCode(() -> MigrationChainSchemaTest.validate(
                        ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword()))
                .doesNotThrowAnyException();
    }
}
