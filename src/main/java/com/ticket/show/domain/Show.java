package com.ticket.show.domain;

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

    /**
     * 화면에 보여주는 판매 유형이다 — booking이 이 회차의 실제 접수 가능 여부를 판단하는 것과는
     * 별개다. 컬럼명은 옛 {@code sale_type}을 그대로 써서 스키마가 바뀌지 않는다(ADR 0007).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "sale_type")
    private SaleType displaySaleType;

    /**
     * 화면에 보여주는 판매 기간과 그 기간으로부터의 상태 판정을 함께 갖는다. 컬럼명은 옛
     * {@code sale_start_date}/{@code sale_end_date}를 그대로 써서 스키마가 바뀌지 않는다
     * (ADR 0007).
     */
    @Embedded
    private DisplaySaleWindow displaySaleWindow;

    private String image;

    /**
     * venue module이 소유한 Venue의 scalar 참조다. 모듈을 넘나드는 JPA 연관관계는 금지되므로
     * {@code @ManyToOne}이 아니라 id 컬럼만 갖는다(ADR 0003 §4). 컬럼명은 옛 {@code @ManyToOne
     * Venue venue} 암묵 매핑과 같은 {@code venue_id}를 그대로 써서 스키마가 바뀌지 않는다.
     */
    @Column(name = "venue_id")
    private Long venueId;

    private Integer runningMinutes;

    /**
     * Performer는 Show와 다른 aggregate라 식별자로만 참조한다(같은 BC 안이어도 aggregate 경계를
     * 넘는 참조는 ID로 한다 — {@code docs/architecture.md}의 참조 규칙). 컬럼명은 옛
     * {@code @ManyToOne Performer performer} 암묵 매핑과 같은 {@code performer_id}를 그대로 쓴다.
     */
    @Column(name = "performer_id")
    private Long performerId;

    public Show(final String title, final String subTitle, final String info, final LocalDate startDate, final LocalDate endDate, final long viewCount, final SaleType displaySaleType, final LocalDateTime displaySaleStartsAt, final LocalDateTime displaySaleEndsAt, final String image, final Long venueId, final Long performerId, final Integer runningMinutes) {
        this.title = title;
        this.subTitle = subTitle;
        this.info = info;
        this.startDate = startDate;
        this.endDate = endDate;
        this.viewCount = viewCount;
        this.displaySaleType = displaySaleType;
        this.displaySaleWindow = new DisplaySaleWindow(displaySaleStartsAt, displaySaleEndsAt);
        this.image = image;
        this.venueId = venueId;
        this.performerId = performerId;
        this.runningMinutes = runningMinutes;
    }

    /**
     * 화면에 보여줄 판매 상태다. 실제 주문 접수 가능 여부는 booking의
     * {@code PerformanceSalesPolicy}가 회차 단위로 따로 판단한다 — 이 메서드는 그 판단을
     * 대체하지 않는다.
     */
    public SaleDisplayStatus saleDisplayStatusAt(final LocalDateTime now) {
        return displaySaleWindow.statusAt(now);
    }

    public LocalDateTime getDisplaySaleStartsAt() {
        return displaySaleWindow.getStartsAt();
    }

    public LocalDateTime getDisplaySaleEndsAt() {
        return displaySaleWindow.getEndsAt();
    }

}
