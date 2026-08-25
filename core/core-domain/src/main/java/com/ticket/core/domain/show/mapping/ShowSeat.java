package com.ticket.core.domain.show.mapping;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.seat.model.Seat;
import com.ticket.core.domain.show.model.Show;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "SHOW_SEATS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_grade_id", nullable = false)
    private ShowGrade showGrade;

    private ShowSeat(final Show show, final Seat seat, final ShowGrade showGrade) {
        this.show = show;
        this.seat = seat;
        this.showGrade = showGrade;
    }

    public static ShowSeat link(final Show show, final Seat seat, final ShowGrade showGrade) {
        if (show != null && showGrade != null && showGrade.getShow() != show) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "show와 showGrade의 show가 일치하지 않습니다.");
        }
        return new ShowSeat(show, seat, showGrade);
    }

}
