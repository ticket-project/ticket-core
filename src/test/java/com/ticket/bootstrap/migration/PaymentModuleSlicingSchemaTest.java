package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@code payment} module이 {@code __root} + 자신의 migration만으로(booking 등 다른 module의 migration 없이) {@code PAYMENTS} 테이블을
 * 만들고, {@code payment_key}/{@code (order_id, attempt_no)}/{@code provider_payment_key} unique 제약과 {@code amount >= 0}
 * CHECK 제약이 실제로 동작하는지 검증한다. 결제 흐름이 아직 없어 매핑 entity는 두지 않는다(ADR 0005).
 *
 * <p>{@code __root} 이력의 V2~V4가 pre-Flyway baseline을 전제하므로 {@link BookingTicketSlicingSchemaTest}와 같은 baseline을 재현한다.
 */
class PaymentModuleSlicingSchemaTest {
    private static final String URL =
            "jdbc:h2:mem:payment-module-slicing-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void root_and_payment_migrations_alone_create_payments_table_with_constraints() throws Exception {
        ModulithFlywayTestSupport.createBookingLegacyBaseline(URL);
        // 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와 payment뿐이다.
        ModulithFlywayTestSupport.migrate(URL, List.of("payment"));

        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
                Statement statement = connection.createStatement()) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "PAYMENTS"))
                    .isTrue();
            // 같은 Order(orderId=1)에 다른 attemptNo로 다시 결제를 시도할 수 있다(Order 1 : 0..N Payment, ADR 0005).
            insertPayment(statement, 1L, "payment-key-1", 1, null, "10.00");
            insertPayment(statement, 1L, "payment-key-2", 2, "provider-key-1", "10.00");
            // providerPaymentKey가 NULL인 행은 여러 건 허용된다.
            insertPayment(statement, 2L, "payment-key-3", 1, null, "1.00");

            // payment_key 중복은 unique 제약 위반이다.
            assertThatThrownBy(() -> insertPayment(statement, 3L, "payment-key-1", 1, null, "1.00"))
                    .isInstanceOf(SQLException.class);
            // 같은 Order의 같은 attemptNo 중복은 unique 제약 위반이다.
            assertThatThrownBy(() -> insertPayment(statement, 1L, "payment-key-4", 1, null, "1.00"))
                    .isInstanceOf(SQLException.class);
            // providerPaymentKey 중복은 unique 제약 위반이다.
            assertThatThrownBy(() -> insertPayment(statement, 4L, "payment-key-5", 1, "provider-key-1", "1.00"))
                    .isInstanceOf(SQLException.class);
            // amount < 0은 CHECK 제약 위반이다.
            assertThatThrownBy(() -> insertPayment(statement, 5L, "payment-key-6", 1, null, "-1.00"))
                    .isInstanceOf(SQLException.class);

            try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM PAYMENTS")) {
                resultSet.next();
                assertThat(resultSet.getLong(1)).isEqualTo(3L);
            }
        }
    }

    private void insertPayment(
            final Statement statement,
            final long orderId,
            final String paymentKey,
            final int attemptNo,
            final String providerPaymentKey,
            final String amount)
            throws SQLException {
        final String provider = providerPaymentKey == null ? "NULL" : "'" + providerPaymentKey + "'";
        statement.executeUpdate("INSERT INTO PAYMENTS (order_id, payment_key, attempt_no, provider, method, amount,"
                + " status, provider_payment_key, requested_at, created_at, created_by) VALUES ("
                + orderId + ", '" + paymentKey + "', " + attemptNo + ", 'TOSS', 'CARD', " + amount + ", 'READY', "
                + provider + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'payment-module-slicing-test')");
    }
}
