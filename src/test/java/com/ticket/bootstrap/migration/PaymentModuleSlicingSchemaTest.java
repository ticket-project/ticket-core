package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.payment.domain.Payment;
import com.ticket.testsupport.PostgreSqlTestDatabase;

/**
 * {@code payment} module이 {@code __root} + 자신의 migration만으로(booking 등 다른 module의 migration 없이) {@link Payment} 매핑과 실제로
 * 맞는 schema를 만들고, {@code payment_key}/{@code (order_id, attempt_no)}/ {@code provider_payment_key} unique 제약과
 * {@code amount >= 0} CHECK 제약이 실제로 동작하는지 검증한다.
 *
 * <p>기법은 {@link BookingModuleSlicingSchemaTest}를 따른다.
 */
class PaymentModuleSlicingSchemaTest {
    private static final String URL = PostgreSqlTestDatabase.newSchemaUrl("payment_slice");

    @Test
    void root_and_payment_migrations_alone_produce_a_schema_the_payment_mapping_can_use() throws Exception {
        // 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와 payment뿐이다.
        ModulithFlywayTestSupport.applyMigrations(URL, List.of("payment"));

        final StandardServiceRegistry registry = ModulithFlywayTestSupport.hibernateRegistry(URL);
        try {
            final Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Payment.class)
                    .buildMetadata();
            // (1) __root + payment migration만으로 만든 schema가 Payment 매핑과 실제로 맞는지 —
            // 운영이 쓰는 ddl-auto=validate와 같은 검증이다.
            ModulithFlywayTestSupport.validateSchema(registry, metadata);
            // (2) 그 schema에 대해 실제 CRUD와 제약이 동작하는지.
            assertCrudAndConstraintsWork(registry, metadata);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
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
            final List<Payment> paymentsForOrder = session.createQuery(
                            "from Payment where orderId = :orderId order by attemptNo", Payment.class)
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
                    })
                    .isInstanceOf(ConstraintViolationException.class);
            // amount < 0은 CHECK 제약 위반이다.
            final Payment negativeAmount =
                    Payment.request(4L, "payment-key-5", 1, "TOSS", "CARD", BigDecimal.valueOf(-1), now);
            assertThatThrownBy(() -> persist(session, negativeAmount)).isInstanceOf(ConstraintViolationException.class);
        } finally {
            sessionFactory.close();
        }
    }

    private void persist(final Session session, final Object entity) {
        // shared.jpa.AuditedEntity의 감사 필드는 Spring Data JPA auditing(AuditingEntityListener +
        // AuditorAware)이 채운다 — 이 테스트는 Spring context 없이 순수 Hibernate만 쓰므로 직접
        // 채운다. Spring auditing 설정 자체는 다른 통합 테스트가 이미 고정한다.
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
