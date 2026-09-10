package com.ticket.bootstrap.migration;

import com.ticket.payment.attempt.domain.Payment;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ticket-domain-module-redesign Phase 5 Task 11(ADR 0005): {@code payment} module이 {@code __root} +
 * 자신의 migration(V1 {@code PAYMENTS} 생성)만으로(booking 등 다른 module의 migration 없이)
 * {@link Payment} 매핑과 실제로 맞는 schema를 만들고, {@code payment_key}/{@code (order_id,
 * attempt_no)}/{@code provider_payment_key} unique 제약과 {@code amount >= 0} CHECK 제약이 실제로
 * 동작하는지 검증한다.
 *
 * <p>{@code BookingModuleSlicingSchemaTest}/{@code ShowModuleSlicingSchemaTest}와 같은 기법이다 —
 * Spring context 없이 순수 Hibernate로 {@code ddl-auto=validate}와 같은 검증, 그리고 CRUD/제약 위반을
 * 확인한다. {@code PAYMENTS} 자체는 이번에 새로 생기는 table이지만, {@code __root} 이력의 기존
 * V2(`PERFORMANCE_QUEUE_POLICIES`)가 pre-Flyway baseline인 {@code PERFORMANCES}를 이미 전제하므로
 * (payment와 무관하게 __root가 항상 요구한다), {@link BookingModuleSlicingSchemaTest}/
 * {@link ShowModuleSlicingSchemaTest}와 같은 최소 legacy baseline을 재현한다.
 */
class PaymentModuleSlicingSchemaTest {

    private static final String URL =
            "jdbc:h2:mem:payment-module-slicing-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void root_and_payment_migrations_alone_produce_a_schema_the_payment_mapping_can_use() throws Exception {
        createLegacyBaselineSchema();
        // 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와 payment뿐이다.
        ModulithFlywayTestSupport.migrate(URL, List.of("payment"));

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
                    .addAnnotatedClass(Payment.class)
                    .buildMetadata();

            // (1) __root + payment migration만으로 만든 schema가 Payment 매핑과 실제로 맞는지 —
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

            // 같은 Order(orderId=1)에 대해 첫 결제 시도가 실패해도, 다른 attemptNo로 다시 결제를
            // 시도할 수 있다(Order 1 : 0..N Payment, ADR 0005).
            final Payment firstAttempt = Payment.request(1L, "payment-key-1", 1, "TOSS", "CARD", BigDecimal.TEN, now);
            persist(session, firstAttempt);
            firstAttempt.fail("PG_DECLINED", "한도 초과", now.plusMinutes(1));
            session.getTransaction().begin();
            session.merge(firstAttempt);
            session.getTransaction().commit();

            final Payment secondAttempt = Payment.request(1L, "payment-key-2", 2, "TOSS", "CARD", BigDecimal.TEN, now);
            persist(session, secondAttempt);

            session.clear();
            final List<Payment> paymentsForOrder = session
                    .createQuery("from Payment where orderId = :orderId order by attemptNo", Payment.class)
                    .setParameter("orderId", 1L)
                    .list();
            assertThat(paymentsForOrder).hasSize(2);
            assertThat(paymentsForOrder.get(0).getStatus().name()).isEqualTo("FAILED");
            assertThat(paymentsForOrder.get(1).getStatus().name()).isEqualTo("READY");

            // payment_key 중복은 unique 제약 위반이다.
            final Payment duplicatePaymentKey =
                    Payment.request(2L, "payment-key-1", 1, "TOSS", "CARD", BigDecimal.ONE, now);
            assertThatThrownBy(() -> persist(session, duplicatePaymentKey))
                    .isInstanceOf(ConstraintViolationException.class);

            // 같은 Order의 같은 attemptNo 중복은 unique 제약 위반이다.
            final Payment duplicateOrderAttempt =
                    Payment.request(1L, "payment-key-3", 1, "TOSS", "CARD", BigDecimal.ONE, now);
            assertThatThrownBy(() -> persist(session, duplicateOrderAttempt))
                    .isInstanceOf(ConstraintViolationException.class);

            // providerPaymentKey 중복은 unique 제약 위반이다(NULL은 여러 건 허용).
            secondAttempt.approve("provider-key-1", now.plusMinutes(2));
            session.getTransaction().begin();
            session.merge(secondAttempt);
            session.getTransaction().commit();

            final Payment thirdAttempt = Payment.request(3L, "payment-key-4", 1, "TOSS", "CARD", BigDecimal.ONE, now);
            persist(session, thirdAttempt);
            thirdAttempt.approve("provider-key-1", now.plusMinutes(3));
            assertThatThrownBy(() -> {
                session.getTransaction().begin();
                try {
                    session.merge(thirdAttempt);
                    session.flush();
                    session.getTransaction().commit();
                } catch (final RuntimeException exception) {
                    session.getTransaction().rollback();
                    throw exception;
                }
            }).isInstanceOf(ConstraintViolationException.class);

            // amount < 0은 CHECK 제약 위반이다.
            final Payment negativeAmount =
                    Payment.request(4L, "payment-key-5", 1, "TOSS", "CARD", BigDecimal.valueOf(-1), now);
            assertThatThrownBy(() -> persist(session, negativeAmount))
                    .isInstanceOf(ConstraintViolationException.class);
        } finally {
            sessionFactory.close();
        }
    }

    /**
     * PERFORMANCES/SEATS/PERFORMANCE_SEATS/ORDER_SEATS는 어떤 Flyway migration도 만들지 않는
     * pre-Flyway baseline이다(docs/operations.md 참고). payment schema 자체는 이 table들을
     * 참조하지 않지만, {@code __root} 이력의 기존 V2~V4가 이를 전제하므로 payment만 골라
     * 검증하더라도 __root 이력이 요구하는 만큼은 재현해야 한다({@link BookingModuleSlicingSchemaTest}의
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
        // PaymentAuditedEntity의 감사 필드는 Spring Data JPA auditing(AuditingEntityListener +
        // AuditorAware)이 채운다 — 이 테스트는 Spring context 없이 순수 Hibernate만 쓰므로 직접
        // 채운다. Spring auditing 배선 자체는 다른 통합 테스트가 이미 고정한다.
        ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(entity, "createdBy", "payment-module-slicing-test");

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
