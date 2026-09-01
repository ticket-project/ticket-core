package com.ticket.core.domain.seat.model;


import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "SEATS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_no", nullable = false)
    private String rowNo;

    @Column(name = "seat_no", nullable = false)
    private String seatNo;

    private int floor;

    private double x;

    private double y;

    public Seat(final String section, final String rowNo, final String seatNo, final int floor, final double x, final double y) {
        this.section = section;
        this.rowNo = rowNo;
        this.seatNo = seatNo;
        this.floor = floor;
        this.x = x;
        this.y = y;
    }

}
