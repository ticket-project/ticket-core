package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import com.ticket.shared.config.H2OracleModeDialect;
import com.ticket.testsupport.persistence.MigratedSchema;

/**
 * <b>빈 DB에 migration만 적용해도 entity 매핑과 맞는 스키마가 나오는지</b> 고정한다.
 *
 * <p>스키마의 원본은 migration이다. entity만 바꾸고 migration을 빠뜨리면 운영의 {@code validate}에서야 기동이 실패한다(옛 ORDER_HOLD_RELEASE_PROGRESS가
 * 그렇게 운영에만 없었다). 여기서는 __root V1(pre-Flyway 기준 스키마)부터 모든 module migration을 운영과 같은 순서로 적용한 뒤 Hibernate {@code validate}로
 * context를 띄운다.
 *
 * <p>Hibernate {@code validate}는 테이블·컬럼·타입만 본다. 인덱스와 유니크 제약은 확인하지 않는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class MigrationChainSchemaTest {
    @Test
    void 빈_H2에_migration만_적용한_스키마가_entity_매핑과_맞다() {
        final String url = "jdbc:h2:mem:migration-chain;MODE=Oracle;DB_CLOSE_DELAY=-1";

        ModulithFlywayTestSupport.migrateFromEmpty(url, MigratedSchema.MODULES_IN_RUNTIME_ORDER);

        assertThatCode(() ->
                        validate(url, "sa", "", "spring.jpa.database-platform=" + H2OracleModeDialect.class.getName()))
                .doesNotThrowAnyException();
    }

    /** 당분간 PK·유니크가 아닌 보조 인덱스를 두지 않는다. 옛 migration이 만든 것은 booking V7·payment V2·show V11이 지운다. */
    @Test
    void 빈_H2에_migration을_모두_적용하면_옛_보조_인덱스가_남지_않는다() throws Exception {
        final String url = "jdbc:h2:mem:migration-chain-indexes;MODE=Oracle;DB_CLOSE_DELAY=-1";

        ModulithFlywayTestSupport.migrateFromEmpty(url, MigratedSchema.MODULES_IN_RUNTIME_ORDER);

        final List<String> names = new ArrayList<>();
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
                Statement statement = connection.createStatement();
                ResultSet indexes = statement.executeQuery("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES")) {
            while (indexes.next()) {
                names.add(indexes.getString(1));
            }
        }
        assertThat(names)
                .doesNotContain(
                        "IDX_ORDER_SEATS_ORDER_ID",
                        "IDX_ORDER_SEATS_PERF_SEAT_ID",
                        "IDX_PERFORMANCE_SEATS_GRADE",
                        "IDX_TICKETS_OWNER_MEMBER_STATUS",
                        "IDX_PAYMENTS_ORDER_STATUS",
                        "IDX_PERFORMANCE_GRADES_PERF_SORT");
    }

    /** entity 매핑으로 context를 띄워 Hibernate {@code validate}를 거친다. 스키마가 다르면 context 생성이 실패한다. */
    static void validate(
            final String url, final String username, final String password, final String... extraProperties) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SchemaValidation.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .properties(
                        "spring.datasource.url=" + url,
                        "spring.datasource.username=" + username,
                        "spring.datasource.password=" + password,
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.jpa.open-in-view=false",
                        "spring.flyway.enabled=false",
                        "logging.level.root=WARN")
                .properties(extraProperties)
                .run()) {
            context.getBeanFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EntityScan("com.ticket")
    static class SchemaValidation {}
}
