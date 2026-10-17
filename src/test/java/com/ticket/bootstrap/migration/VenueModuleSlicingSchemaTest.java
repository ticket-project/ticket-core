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

import com.ticket.testsupport.PostgreSqlTestDatabase;
import com.ticket.venue.domain.Region;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.Venue;

/**
 * {@code venue} module이 {@code __root} + 자신의 migration만으로(booking·member·show 등 다른 module의 migration 없이)
 * {@link Seat}/{@link Venue} 매핑과 실제로 맞는 schema를 만들고, Venue별 좌석 주소 unique 제약이 실제로 동작하는지 검증한다.
 *
 * <p>기법은 {@link BookingModuleSlicingSchemaTest}를 따른다.
 */
class VenueModuleSlicingSchemaTest {
    private static final String URL = PostgreSqlTestDatabase.newSchemaUrl("venue_slice");

    @Test
    void root_and_venue_migrations_alone_produce_a_schema_the_seat_venue_mapping_can_use() throws Exception {
        // booking/member/show 등 다른 module의 migration은 이 DB에 전혀 적용하지 않는다 — __root와
        // venue뿐이다.
        ModulithFlywayTestSupport.applyMigrations(URL, List.of("venue"));

        final StandardServiceRegistry registry = ModulithFlywayTestSupport.hibernateRegistry(URL);
        try {
            final Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(Venue.class)
                    .addAnnotatedClass(Seat.class)
                    .buildMetadata();
            // (1) __root + venue migration만으로 만든 schema가 Seat/Venue 매핑과 실제로 맞는지 —
            // 운영이 쓰는 ddl-auto=validate와 같은 검증이다.
            ModulithFlywayTestSupport.validateSchema(registry, metadata);
            // (2) 그 schema에 대해 실제 CRUD와 unique 제약이 동작하는지.
            assertCrudAndUniqueConstraintWork(registry, metadata);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void assertCrudAndUniqueConstraintWork(final StandardServiceRegistry registry, final Metadata metadata) {
        final SessionFactory sessionFactory = metadata.buildSessionFactory();
        try (Session session = sessionFactory.openSession()) {
            final Venue venueA = venue("Venue A");
            final Venue venueB = venue("Venue B");
            persist(session, venueA);
            persist(session, venueB);

            final Seat seatInVenueA = new Seat(venueA.getId(), "가", "A", "1", 1, 10.0, 20.0);
            persist(session, seatInVenueA);

            session.clear();
            final Seat found = session.find(Seat.class, seatInVenueA.getId());
            assertThat(found).isNotNull();
            assertThat(found.getVenueId()).isEqualTo(venueA.getId());
            // 다른 Venue의 동일 좌석 주소는 성공한다.
            final Seat seatInVenueB = new Seat(venueB.getId(), "가", "A", "1", 1, 10.0, 20.0);
            persist(session, seatInVenueB);
            // 같은 Venue 안 동일 좌석 주소는 unique 제약 위반이다.
            final Seat duplicateInVenueA = new Seat(venueA.getId(), "가", "A", "1", 1, 99.0, 99.0);
            assertThatThrownBy(() -> persist(session, duplicateInVenueA))
                    .isInstanceOf(ConstraintViolationException.class);
        } finally {
            sessionFactory.close();
        }
    }

    private Venue venue(final String name) {
        return new Venue(
                name,
                name + " 주소",
                Region.SEOUL,
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                "02-0000-0000",
                "https://example.com/venue.png",
                500,
                356,
                4.8);
    }

    private void persist(final Session session, final Object entity) {
        // BaseEntity의 감사 필드는 Spring Data JPA auditing이 채운다 — 이 테스트는 Spring context
        // 없이 순수 Hibernate만 쓰므로 직접 채운다.
        ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(entity, "createdBy", "venue-module-slicing-test");

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
}
