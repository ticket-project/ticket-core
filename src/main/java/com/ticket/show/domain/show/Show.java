package com.ticket.show.domain.show;


import com.ticket.show.domain.ShowAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "SHOWS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Show extends ShowAuditedEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String title;

    @Column(length = 500)
    private String subTitle;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String info;

    private LocalDate startDate;

    private LocalDate endDate;

    private long viewCount;

    @Enumerated(EnumType.STRING)
    private SaleType saleType;

    private LocalDateTime saleStartDate;

    private LocalDateTime saleEndDate;

    private String image;

    /**
     * venue module이 소유한 Venue의 scalar 참조다. 모듈을 넘나드는 JPA 연관관계는 금지되므로
     * {@code @ManyToOne}이 아니라 id 컬럼만 갖는다(ADR 0003 §4). 컬럼명은 옛 {@code @ManyToOne
     * Venue venue} 암묵 매핑과 같은 {@code venue_id}를 그대로 써서 스키마가 바뀌지 않는다.
     */
    @Column(name = "venue_id")
    private Long venueId;

    private Integer runningMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    private Performer performer;

    public Show(final String title, final String subTitle, final String info, final LocalDate startDate, final LocalDate endDate, final long viewCount, final SaleType saleType, final LocalDateTime saleStartDate, final LocalDateTime saleEndDate, final String image, final Long venueId, final Performer performer, final Integer runningMinutes) {
        this.title = title;
        this.subTitle = subTitle;
        this.info = info;
        this.startDate = startDate;
        this.endDate = endDate;
        this.viewCount = viewCount;
        this.saleType = saleType;
        this.saleStartDate = saleStartDate;
        this.saleEndDate = saleEndDate;
        this.image = image;
        this.venueId = venueId;
        this.performer = performer;
        this.runningMinutes = runningMinutes;
    }

    public BookingStatus getBookingStatus(final LocalDateTime now) {
        if (saleStartDate == null || saleEndDate == null) {
            return BookingStatus.CLOSED;
        }
        if (now.isBefore(saleStartDate)) {
            return BookingStatus.BEFORE_OPEN;
        }
        if (now.isAfter(saleEndDate)) {
            return BookingStatus.CLOSED;
        }
        return BookingStatus.ON_SALE;
    }

}
