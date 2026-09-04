package com.ticket.bootstrap.migration;

import com.ticket.ticketing.internal.domain.ticket.model.Ticket;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ticket-domain-module-redesign Phase 5 Task 12(ADR 0005): {@code ticketing} module이 {@code __root} +
 * 자신의 migration(V1 {@code TICKETS} 생성)만으로(booking 등 다른 module의 migration 없이)
 * {@link Ticket} 매핑과 실제로 맞는 schema를 만들고, {@code ticket_key}/{@code order_seat_id} unique
 * 제약이 실제로 동작하는지 검증한다.
 *
 * <p>{@code PaymentModuleSlicingSchemaTest}와 같은 기법이다 — Spring context 없이 순수 Hibernate로
 * {@code ddl-auto=validate}와 같은 검증, 그리고 CRUD/제약 위반을 확인한다. {@code TICKETS} 자체는
 * 이번에 새로 생기는 table이지만, {@code __root} 이력의 기존 V2~V4가 pre-Flyway baseline인
 * {@code PERFORMANCES}/{@code SEATS}/{@code PERFORMANCE_SEATS}/{@code ORDER_SEATS}를 이미 전제하므로
 * (ticketing과 무관하게 __root가 항상 요구한다), {@link PaymentModuleSlicingSchemaTest}와 같은 최소
 * legacy baseline을 재현한다.
 */
class TicketingModuleSlicingSchemaTest {

    private static final String URL =
            "jdbc:h2:mem:ticketing-module-slicing-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void root_and_ticketing_migrations_alone_produce_a_schema_the_ticket_mapping_can_use() throws Exception {
        createLegacyBaselineSchema();
        // 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와 ticketing뿐이다.
        ModulithFlywayTestSupport.migrate(URL, List.of("ticketing"));

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
                    .addAnnotatedClass(Ticket.class)
                    .buildMetadata();

            // (1) __root + ticketing migration만으로 만든 schema가 Ticket 매핑과 실제로 맞는지 —
            // 운영이 쓰는 ddl-auto=validate와 같은 검증이다.
            validateSchema(registry, metadata);

            // (2) 그 schema에 대해 실제 CRUD와 제약이 동작하는지.
            assertCrudAndConstraintsWork(registry, metadata);
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

    private void assertCrudAndConstraintsWork(final StandardServiceRegistry registry, final Metadata metadata) {
        final SessionFactory sessionFactory = metadata.buildSessionFactory();
        try (Session session = sessionFactory.openSession()) {
            final LocalDateTime now = LocalDateTime.now();

            // 하나의 OrderSeat(orderSeatId=1)에는 Ticket이 최대 하나만 존재한다(`1:0..1`, ADR 0005).
            final Ticket firstTicket = Ticket.issue("ticket-key-1", 1L, 10L, now);
            persist(session, firstTicket);

            session.clear();
            final Ticket loaded = session
                    .createQuery("from Ticket where orderSeatId = :orderSeatId", Ticket.class)
                    .setParameter("orderSeatId", 1L)
                    .getSingleResult();
            assertThat(loaded.getTicketKey()).isEqualTo("ticket-key-1");
            assertThat(loaded.getOwnerMemberId()).isEqualTo(10L);
            assertThat(loaded.getStatus().name()).isEqualTo("ISSUED");

            // 같은 order_seat_id로 두 번째 Ticket을 만들면 unique 제약 위반이다.
            final Ticket duplicateOrderSeat = Ticket.issue("ticket-key-2", 1L, 20L, now);
            assertThatThrownBy(() -> persist(session, duplicateOrderSeat))
                    .isInstanceOf(ConstraintViolationException.class);

            // ticket_key 중복도 (다른 orderSeatId라도) unique 제약 위반이다.
            final Ticket duplicateTicketKey = Ticket.issue("ticket-key-1", 2L, 30L, now);
            assertThatThrownBy(() -> persist(session, duplicateTicketKey))
                    .isInstanceOf(ConstraintViolationException.class);

            // 다른 orderSeatId, 다른 ticketKey는 정상 발급된다.
            final Ticket secondTicket = Ticket.issue("ticket-key-3", 3L, 30L, now);
            persist(session, secondTicket);
            session.clear();

            final Long ticketCount = session
                    .createQuery("select count(t) from Ticket t", Long.class)
                    .getSingleResult();
            assertThat(ticketCount).isEqualTo(2L);
        } finally {
            sessionFactory.close();
        }
    }

    /**
     * PERFORMANCES/SEATS/PERFORMANCE_SEATS/ORDER_SEATS는 어떤 Flyway migration도 만들지 않는
     * pre-Flyway baseline이다(docs/operations.md 참고). ticketing schema 자체는 이 table들을
     * 참조하지 않지만, {@code __root} 이력의 기존 V2~V4가 이를 전제하므로 ticketing만 골라
     * 검증하더라도 __root 이력이 요구하는 만큼은 재현해야 한다({@link PaymentModuleSlicingSchemaTest}의
     * baseline과 같다).
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
                    "  updated_by VARCHAR(255)" +
                    ")");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
        }
    }

    private void persist(final Session session, final Object entity) {
        // TicketingAuditedEntity의 감사 필드는 Spring Data JPA auditing(AuditingEntityListener +
        // AuditorAware)이 채운다 — 이 테스트는 Spring context 없이 순수 Hibernate만 쓰므로 직접
        // 채운다. Spring auditing 배선 자체는 다른 통합 테스트가 이미 고정한다.
        ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(entity, "createdBy", "ticketing-module-slicing-test");

        session.getTransaction().begin();
        try {
            session.persist(entity);
            session.flush();
            session.getTransaction().commit();
        } catch (final RuntimeException exception) {
            session.getTransaction().rollback();
            throw exception;
        }
    }
}
