package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@code booking} module이 {@code __root} + 자신의 migration만으로(show·member 등 다른 module의 migration 없이) {@code TICKETS} 테이블을
 * 만들고, {@code ticket_key}/{@code order_seat_id} unique 제약이 실제로 동작하는지 검증한다. {@code TICKETS} 생성 migration은 booking의 V5다.
 * 발급 흐름이 아직 없어 매핑 entity는 두지 않는다(ADR 0005).
 *
 * <p>기법은 {@link BookingModuleSlicingSchemaTest}를 따른다. booking의 V1(cross-module FK 제거)이 정상 동작하도록 legacy baseline도 같은 형태로
 * 재현한다.
 */
class BookingTicketSlicingSchemaTest {
    private static final String URL =
            "jdbc:h2:mem:booking-ticket-slicing-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void root_and_booking_migrations_alone_create_tickets_table_with_unique_constraints() throws Exception {
        ModulithFlywayTestSupport.createBookingLegacyBaseline(URL);
        // 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와 booking뿐이다.
        ModulithFlywayTestSupport.migrate(URL, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
                Statement statement = connection.createStatement()) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "TICKETS"))
                    .isTrue();
            // 하나의 OrderSeat(orderSeatId=1)에는 Ticket이 최대 하나만 존재한다(`1:0..1`, ADR 0005).
            insertTicket(statement, "ticket-key-1", 1L, 10L);
            // 같은 order_seat_id로 두 번째 Ticket을 만들면 unique 제약 위반이다.
            assertThatThrownBy(() -> insertTicket(statement, "ticket-key-2", 1L, 20L))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class);
            // ticket_key 중복도 (다른 orderSeatId라도) unique 제약 위반이다.
            assertThatThrownBy(() -> insertTicket(statement, "ticket-key-1", 2L, 30L))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class);
            // 다른 orderSeatId, 다른 ticketKey는 정상 발급된다.
            insertTicket(statement, "ticket-key-3", 3L, 30L);

            try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM TICKETS")) {
                resultSet.next();
                assertThat(resultSet.getLong(1)).isEqualTo(2L);
            }
        }
    }

    private void insertTicket(
            final Statement statement, final String ticketKey, final long orderSeatId, final long ownerMemberId)
            throws SQLException {
        statement.executeUpdate("INSERT INTO TICKETS (ticket_key, order_seat_id, owner_member_id, status, issued_at,"
                + " created_at, created_by) VALUES ('" + ticketKey + "', " + orderSeatId + ", " + ownerMemberId
                + ", 'ISSUED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'booking-ticket-slicing-test')");
    }
}
