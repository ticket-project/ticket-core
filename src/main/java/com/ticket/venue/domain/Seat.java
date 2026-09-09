package com.ticket.venue.domain;

import com.ticket.venue.domain.VenueAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "SEATS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_SEATS_VENUE_SEAT_ADDRESS",
                columnNames = {"venue_id", "floor", "section", "row_no", "seat_no"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends VenueAuditedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Venue는 Seat와 다른 aggregate라 식별자로만 참조한다(같은 BC 안이어도 aggregate 경계를 넘는
     * 참조는 ID로 한다 — {@code docs/architecture.md}의 참조 규칙). 컬럼명은 옛 {@code @ManyToOne
     * Venue venue} 매핑과 같은 {@code venue_id}를 그대로 써서 스키마가 바뀌지 않는다.
     */
    @Column(name = "venue_id", nullable = false)
    private Long venueId;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_no", nullable = false)
    private String rowNo;

    @Column(name = "seat_no", nullable = false)
    private String seatNo;

    private int floor;

    private double x;

    private double y;

    public Seat(final Long venueId, final String section, final String rowNo, final String seatNo, final int floor, final double x, final double y) {
        this.venueId = venueId;
        this.section = section;
        this.rowNo = rowNo;
        this.seatNo = seatNo;
        this.floor = floor;
        this.x = x;
        this.y = y;
    }

}
