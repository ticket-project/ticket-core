package com.ticket.bootstrap.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticket.booking.OrderTerminated;

/**
 * {@code __root} V9가 {@code serialized_event}를 넓히기 전에는 현실적인 {@link OrderTerminated} 직렬화 결과가 옛 VARCHAR(255) 컬럼에 들어가지
 * 못한다는 것을 재현하고, V9 적용 후에는 저장·보관·완료 조회가 모두 되는 것을 고정한다.
 *
 * <p>직렬화는 Spring Modulith의 {@code JacksonEventSerializer}가 애플리케이션 {@code ObjectMapper}로 수행한다. 이 저장소는 Jackson을 따로 설정하지
 * 않으므로 Boot 기본값(JavaTimeModule 등록, timestamp 비활성)을 그대로 재현한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class EventPublicationSerializedEventLengthTest {
    private static final int LEGACY_COLUMN_LENGTH = 255;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void 현실적인_주문종료_이벤트_직렬화는_옛_255자_컬럼을_넘는다() throws Exception {
        final String serialized = objectMapper.writeValueAsString(realisticOrderTerminated());

        assertThat(serialized.length()).isGreaterThan(LEGACY_COLUMN_LENGTH);
    }

    @Test
    void 옛_255자_컬럼에는_저장이_실패한다() throws Exception {
        final String url = databaseUrl("legacy");
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
                Statement statement = connection.createStatement()) {
            statement.execute(legacyPublicationTableDdl("EVENT_PUBLICATION"));
        }

        final String serialized = objectMapper.writeValueAsString(realisticOrderTerminated());

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThatThrownBy(() -> insertPublication(connection, "EVENT_PUBLICATION", serialized))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void V9_적용_후에는_활성과_보관_테이블에_저장하고_완료_조회할_수_있다() throws Exception {
        final String url = databaseUrl("widened");
        createBaseline(url);
        ModulithFlywayTestSupport.migrateRootOnly(url);

        final String serialized = objectMapper.writeValueAsString(realisticOrderTerminated());

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(serializedEventColumnLength(connection, "EVENT_PUBLICATION"))
                    .isGreaterThanOrEqualTo(4000);
            assertThat(serializedEventColumnLength(connection, "EVENT_PUBLICATION_ARCHIVE"))
                    .isGreaterThanOrEqualTo(4000);

            insertPublication(connection, "EVENT_PUBLICATION", serialized);
            insertPublication(connection, "EVENT_PUBLICATION_ARCHIVE", serialized);

            // Modulith의 JpaEventPublicationRepository는 완료·재제출에서 serialized_event를 동등
            // 비교한다. CLOB이 아니라 VARCHAR로 넓혔기 때문에 이 조회가 성립한다.
            assertThat(countBySerializedEvent(connection, "EVENT_PUBLICATION", serialized))
                    .isEqualTo(1);
            assertThat(countBySerializedEvent(connection, "EVENT_PUBLICATION_ARCHIVE", serialized))
                    .isEqualTo(1);
        }
    }

    /** {@code __root} V2~V4가 전제하는 기존 schema baseline이다({@code CoreQueryIndexMigrationTest}와 같다). */
    private void createBaseline(final String url) throws SQLException {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
        }
    }

    private OrderTerminated realisticOrderTerminated() {
        return new OrderTerminated(
                UUID.randomUUID(),
                OrderTerminated.SCHEMA_VERSION,
                1234567L,
                7654321L,
                "HOLD-" + UUID.randomUUID().toString().replace("-", ""),
                Set.copyOf(List.of(10000001L, 10000002L, 10000003L, 10000004L)),
                "CANCELED",
                Instant.parse("2026-09-14T12:34:56.123456789Z"));
    }

    private void insertPublication(final Connection connection, final String table, final String serializedEvent)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO "
                + table
                + " (id, publication_date, listener_id, serialized_event,"
                + " event_type, completion_attempts, status)"
                + " VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, 0, 'PUBLISHED')")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "com.ticket.booking.event.BookingEventListeners.on(...)");
            statement.setString(3, serializedEvent);
            statement.setString(4, OrderTerminated.class.getName());
            statement.executeUpdate();
        }
    }

    private int countBySerializedEvent(final Connection connection, final String table, final String serializedEvent)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE serialized_event = ?")) {
            statement.setString(1, serializedEvent);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int serializedEventColumnLength(final Connection connection, final String table) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, "SERIALIZED_EVENT")) {
            assertThat(columns.next()).as("%s.serialized_event 컬럼", table).isTrue();
            return columns.getInt("COLUMN_SIZE");
        }
    }

    private String legacyPublicationTableDdl(final String table) {
        return "CREATE TABLE "
                + table
                + " (id UUID NOT NULL, publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,"
                + " listener_id VARCHAR(255) NOT NULL, serialized_event VARCHAR(255) NOT NULL,"
                + " event_type VARCHAR(255) NOT NULL,"
                + " completion_date TIMESTAMP(6) WITH TIME ZONE,"
                + " last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,"
                + " completion_attempts INTEGER NOT NULL, status VARCHAR(32),"
                + " CONSTRAINT pk_"
                + table.toLowerCase()
                + " PRIMARY KEY (id))";
    }

    private String databaseUrl(final String name) {
        return "jdbc:h2:mem:event-publication-length-" + name + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=TRUE";
    }
}
