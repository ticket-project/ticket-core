package com.ticket.bootstrap.migration;

import com.ticket.show.domain.seat.Seat;
import com.ticket.show.domain.show.Region;
import com.ticket.show.domain.show.Venue;
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
 * ticket-domain-module-redesign Phase 2 Task 3(ADR 0005): {@code show} module이 {@code __root} +
 * 자신의 migration(V3 Seat-Venue 관계 추가, V4 Grade/PerformanceGrade 추가)만으로
 * (booking·member 등 다른 module의 migration 없이) {@link Seat}/{@link Venue} 매핑과 실제로 맞는
 * schema를 만들고, Venue별 좌석 주소 unique 제약이 실제로 동작하는지 검증한다. 옛 catalog V1(SHOW_LIKES
 * member FK 제거)은 찜이 favorite module로 분리되며 {@code favorite}의 migration으로 옮겨갔다 —
 * {@code FavoriteModuleMigrationTest}가 검증한다.
 *
 * <p>{@code BookingModuleSlicingSchemaTest}와 같은 기법이다 — Spring context 없이 순수 Hibernate로
 * {@code ddl-auto=validate}와 같은 검증, 그리고 CRUD/제약 위반을 확인한다. {@code SEATS}/{@code VENUES}는
 * 어떤 Flyway migration도 만들지 않는 pre-Flyway baseline이므로(V3는 기존 SEATS에 컬럼을 더할
 * 뿐이다) legacy baseline schema를 먼저 만든 뒤 module migration을 적용한다.
 */
class ShowModuleSlicingSchemaTest {

    private static final String URL =
            "jdbc:h2:mem:show-module-slicing-schema;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void root_and_show_migrations_alone_produce_a_schema_the_seat_venue_mapping_can_use() throws Exception {
        createLegacyBaselineSchema();
        // booking/member 등 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와
        // show뿐이다.
        ModulithFlywayTestSupport.migrate(URL, List.of("show"));

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
                    .addAnnotatedClass(Venue.class)
                    .addAnnotatedClass(Seat.class)
                    .buildMetadata();

            // (1) __root + show migration만으로 만든 schema가 Seat/Venue 매핑과 실제로 맞는지 —
            // 운영이 쓰는 ddl-auto=validate와 같은 검증이다.
            validateSchema(registry, metadata);

            // (2) 그 schema에 대해 실제 CRUD와 unique 제약이 동작하는지.
            assertCrudAndUniqueConstraintWork(registry, metadata);
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

    private void assertCrudAndUniqueConstraintWork(final StandardServiceRegistry registry, final Metadata metadata) {
        final SessionFactory sessionFactory = metadata.buildSessionFactory();
        try (Session session = sessionFactory.openSession()) {
            final Venue venueA = venue("Venue A");
            final Venue venueB = venue("Venue B");
            persist(session, venueA);
            persist(session, venueB);

            final Seat seatInVenueA = new Seat(venueA, "가", "A", "1", 1, 10.0, 20.0);
            persist(session, seatInVenueA);

            session.clear();
            final Seat found = session.find(Seat.class, seatInVenueA.getId());
            assertThat(found).isNotNull();
            assertThat(found.getVenue().getId()).isEqualTo(venueA.getId());

            // 다른 Venue의 동일 좌석 주소는 성공한다.
            final Seat seatInVenueB = new Seat(venueB, "가", "A", "1", 1, 10.0, 20.0);
            persist(session, seatInVenueB);

            // 같은 Venue 안 동일 좌석 주소는 unique 제약 위반이다.
            final Seat duplicateInVenueA = new Seat(venueA, "가", "A", "1", 1, 99.0, 99.0);
            assertThatThrownBy(() -> persist(session, duplicateInVenueA))
                    .isInstanceOf(ConstraintViolationException.class);
        } finally {
            sessionFactory.close();
        }
    }

    private Venue venue(final String name) {
        return Venue.create(
                name,
                name + " 주소",
                Region.SEOUL,
                "상세",
                "12345",
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                500,
                356,
                4.8,
                2.5,
                2.5
        );
    }

    private void persist(final Session session, final Object entity) {
        // BaseEntity의 감사 필드는 Spring Data JPA auditing이 채운다 — 이 테스트는 Spring context
        // 없이 순수 Hibernate만 쓰므로 직접 채운다.
        ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(entity, "createdBy", "show-module-slicing-test");

        session.getTransaction().begin();
        try {
            session.persist(entity);
            session.flush();
            session.getTransaction().commit();
        } catch (RuntimeException e) {
            session.getTransaction().rollback();
            throw e;
        }
    }

    /**
     * SEATS/VENUES는 어떤 Flyway migration도 만들지 않는 pre-Flyway baseline이다(V2는 기존 SEATS에
     * venue_id를 더할 뿐이다, docs/operations.md 참고). V2가 정상 동작을 검증할 수 있도록 Task 3
     * 이전에 존재했을 법한 schema를 재현한다.
     *
     * <p>Task 5(V5/V6)가 추가되며 SHOWS/SHOW_GRADES/SHOW_SEATS도 같은 이유로 필요해졌다 -- 이
     * 셋도 어떤 Flyway migration도 만들지 않은 pre-Flyway baseline이고, V5/V6이 그 데이터를 읽어
     * GRADES/PERFORMANCE_GRADES/PERFORMANCE_SEATS로 옮긴다. PERFORMANCES.show_id와
     * PERFORMANCE_SEATS.id/price도 V5/V6이 참조하므로 함께 추가한다.
     */
    private void createLegacyBaselineSchema() throws Exception {
        try (Connection connection = ModulithFlywayTestSupport.connect(URL);
             Statement statement = connection.createStatement()) {
            // __root의 V2(performance_queue_policies FK)~V4(order_seats 인덱스)는 다른 module의
            // schema에도 걸쳐 있으므로, 이 module만 적용하는 테스트에서도 최소 baseline으로 있어야
            // 한다(BookingModuleSlicingSchemaTest의 legacy baseline과 같은 이유).
            statement.execute("CREATE TABLE SHOWS (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY)");
            statement.execute("CREATE TABLE PERFORMANCES (id BIGINT PRIMARY KEY, show_id BIGINT NOT NULL)");
            statement.execute(
                    "CREATE TABLE SHOW_GRADES (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  show_id BIGINT NOT NULL, grade_code VARCHAR(20) NOT NULL, " +
                    "  grade_name VARCHAR(255) NOT NULL, price DECIMAL(19,2) NOT NULL, sort_order INT NOT NULL" +
                    ")");
            statement.execute(
                    "CREATE TABLE SHOW_SEATS (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  show_id BIGINT NOT NULL, seat_id BIGINT NOT NULL, show_grade_id BIGINT NOT NULL" +
                    ")");
            statement.execute(
                    "CREATE TABLE PERFORMANCE_SEATS (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  performance_id BIGINT NOT NULL, seat_id BIGINT NOT NULL, price DECIMAL(19,2)" +
                    ")");
            statement.execute("CREATE TABLE ORDER_SEATS (order_id BIGINT NOT NULL)");
            statement.execute("""
                    CREATE TABLE VENUES (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      name VARCHAR(255), address VARCHAR(255), region VARCHAR(50),
                      address_detail VARCHAR(255), zip_code VARCHAR(20),
                      latitude DECIMAL(10,8), longitude DECIMAL(11,8),
                      phone VARCHAR(255), image_url VARCHAR(255),
                      view_box_width INT NOT NULL, view_box_height INT NOT NULL,
                      seat_diameter DOUBLE NOT NULL, gap_x DOUBLE NOT NULL, gap_y DOUBLE NOT NULL,
                      created_at TIMESTAMP NOT NULL, created_by VARCHAR(255) NOT NULL,
                      updated_at TIMESTAMP, updated_by VARCHAR(255)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE SEATS (
                      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                      section VARCHAR(255) NOT NULL, row_no VARCHAR(255) NOT NULL,
                      seat_no VARCHAR(255) NOT NULL, floor INT NOT NULL,
                      x DOUBLE NOT NULL, y DOUBLE NOT NULL,
                      created_at TIMESTAMP NOT NULL, created_by VARCHAR(255) NOT NULL,
                      updated_at TIMESTAMP, updated_by VARCHAR(255)
                    )
                    """);
        }
    }
}
