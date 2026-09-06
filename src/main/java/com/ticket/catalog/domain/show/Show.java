package com.ticket.catalog.domain.show;


import com.ticket.catalog.domain.CatalogAuditedEntity;
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
public class Show extends CatalogAuditedEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    private Venue venue;

    private Integer runningMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    private Performer performer;

    public Show(final String title, final String subTitle, final String info, final LocalDate startDate, final LocalDate endDate, final long viewCount, final SaleType saleType, final LocalDateTime saleStartDate, final LocalDateTime saleEndDate, final String image, final Venue venue, final Performer performer, final Integer runningMinutes) {
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
        this.venue = venue;
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
