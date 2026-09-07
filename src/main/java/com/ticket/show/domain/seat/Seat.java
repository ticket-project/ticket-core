package com.ticket.show.domain.seat;


import com.ticket.show.domain.ShowAuditedEntity;
import com.ticket.show.domain.show.Venue;
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
public class Seat extends ShowAuditedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_no", nullable = false)
    private String rowNo;

    @Column(name = "seat_no", nullable = false)
    private String seatNo;

    private int floor;

    private double x;

    private double y;

    public Seat(final Venue venue, final String section, final String rowNo, final String seatNo, final int floor, final double x, final double y) {
        this.venue = venue;
        this.section = section;
        this.rowNo = rowNo;
        this.seatNo = seatNo;
        this.floor = floor;
        this.x = x;
        this.y = y;
    }

}
