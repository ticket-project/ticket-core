package com.ticket.bootstrap.migration;

import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 11 Step 6: {@code booking} module이 {@code __root} + 자신의 migration만으로(catalog·identity
 * 등 다른 module의 migration 없이) 실제 schema를 만들고, {@code booking}의 JPA 매핑({@link
 * PerformanceSeat})이 그 schema에 대해 {@code ddl-auto=validate}를 통과하며, CRUD가 동작하는지
 * 검증한다.
 *
 * <p>이상적으로는 {@code @DataJpaTest @ModuleSlicing}(plan 원문)으로 Spring context 수준에서
 * 검증하고 싶었다. 최초 구현 시도에서 이 조합이 깨진다고 판단해 Hibernate 단독 검증으로
 * 우회했는데, 이후 리뷰에서 그 진단이 부정확했다는 게 밝혀졌다 — 실제 원인은 프레임워크 비호환이
 * 아니라 {@code @ModuleSlicing}의 기본값 {@code verifyAutomatically = true}가
 * {@code com.ticket.ModularityTests}의 legacy package 제외 predicate 없이 전체 구조를 검증하며
 * "catalog → core → catalog" 같은 (이미 알려진) legacy 오탐 순환을 잡아내는 것이었다.
 * {@code BookingModuleTests}가 이미 같은 이유로 {@code verifyAutomatically = false}를 쓰고
 * 있으므로, {@code @DataJpaTest @ModuleSlicing(module = "booking", verifyAutomatically = false)}
 * 조합도 실제로 context가 뜨고 module-scoped entity/repository scan이 동작하는 것까지는 리뷰에서
 * 재현됐다(Flyway 통합까지 마저 배선하는 작업은 남아 있다). 즉 plan 원문의 API가 불가능한 것은
 * 아니다 — 이 클래스는 그 배선을 마치기 전 임시 대안으로 남아 있다.
 *
 * <p>지금은 Spring context 없이 순수 Hibernate로 같은 목표(root+module migration만으로 만든
 * schema가 module의 JPA 매핑과 실제로 맞고 CRUD가 되는지)를 검증한다 — 이 저장소의
 * {@link EventPublicationRegistrySchemaValidationTest}와 같은, 이미 검증된 기법이며 그 자체로
 * 유효한 검증이다. {@code @DataJpaTest @ModuleSlicing}로의 전환은 별도 후속 작업이다.
 */
class BookingModuleSlicingSchemaTest {

    private static final String URL =
            "jdbc:h2:mem:booking-module-slicing-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void root_and_booking_migrations_alone_produce_a_schema_the_booking_mapping_can_use() throws Exception {
        createLegacyBaselineSchema();
        // catalog/identity/showlike 등 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 —
        // __root와 booking뿐이다.
        ModulithFlywayTestSupport.migrate(URL, List.of("booking"));

        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", URL)
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                .build();
        try {
            final Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(PerformanceSeat.class)
                    .buildMetadata();

            // (1) __root + booking migration만으로 만든 schema가 PerformanceSeat 매핑과 실제로
            // 맞는지 — 운영이 쓰는 ddl-auto=validate와 같은 검증이다.
            validateSchema(registry, metadata);

            // (2) 그 schema에 대해 실제 CRUD가 동작하는지.
            assertCrudWorks(registry, metadata);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void validateSchema(final StandardServiceRegistry registry, final Metadata metadata) {
        final SchemaManagementTool tool = registry.getService(SchemaManagementTool.class);
        final Map<String, Object> configValues = Map.of();

        final ExecutionOptions options = new ExecutionOptions() {
            @Override
            public Map<String, Object> getConfigurationValues() {
                return configValues;
            }

            @Override
            public boolean shouldManageNamespaces() {
                return true;
            }

            @Override
            public ExceptionHandler getExceptionHandler() {
                return exception -> {
                    throw exception;
                };
            }
        };

        // 예외 없이 반환하면 검증 통과다.
        tool.getSchemaValidator(configValues).doValidation(metadata, options, ContributableMatcher.ALL);
    }

    private void assertCrudWorks(final StandardServiceRegistry registry, final Metadata metadata) {
        final SessionFactory sessionFactory = metadata.buildSessionFactory();
        try (Session session = sessionFactory.openSession()) {
            final PerformanceSeat seat = new PerformanceSeat(1L, 1L, 1L, PerformanceSeatState.AVAILABLE, BigDecimal.TEN);
            // BookingAuditedEntity의 감사 필드는 Spring Data JPA auditing(AuditingEntityListener +
            // AuditorAware)이 채운다 — 이 테스트는 Spring context 없이 순수 Hibernate만 쓰므로 직접
            // 채운다. Spring auditing 배선 자체는 다른 통합 테스트가 이미 고정한다.
            ReflectionTestUtils.setField(seat, "createdAt", LocalDateTime.now());
            ReflectionTestUtils.setField(seat, "createdBy", "booking-module-slicing-test");

            session.getTransaction().begin();
            session.persist(seat);
            session.getTransaction().commit();

            session.clear();

            final PerformanceSeat found = session.find(PerformanceSeat.class, seat.getId());
            assertThat(found).isNotNull();
            assertThat(found.getPerformanceId()).isEqualTo(1L);
            assertThat(found.getSeatId()).isEqualTo(1L);
            assertThat(found.getState()).isEqualTo(PerformanceSeatState.AVAILABLE);
        } finally {
            sessionFactory.close();
        }
    }

    /**
     * PERFORMANCE_SEATS/PERFORMANCES/SEATS/ORDER_SEATS는 어떤 Flyway migration도 만들지 않는
     * pre-Flyway baseline(V1 없음, docs/operations.md 참고)이다. booking의 FK 제거 migration이
     * 정상 동작을 검증할 수 있도록, Task 7 이전에 존재했을 법한 cross-module FK까지 재현한다.
     */
    private void createLegacyBaselineSchema() throws Exception {
        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE seats (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  performance_id BIGINT NOT NULL, " +
                    "  seat_id BIGINT NOT NULL, " +
                    "  state VARCHAR(255), " +
                    "  price DECIMAL(38, 2), " +
                    "  created_at TIMESTAMP NOT NULL, " +
                    "  created_by VARCHAR(255) NOT NULL, " +
                    "  updated_at TIMESTAMP, " +
                    "  updated_by VARCHAR(255), " +
                    "  CONSTRAINT legacy_fk_performance FOREIGN KEY (performance_id) REFERENCES performances, " +
                    "  CONSTRAINT legacy_fk_seat FOREIGN KEY (seat_id) REFERENCES seats" +
                    ")");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
        }
    }
}
