package com.ticket.show.performance.domain;

import com.ticket.show.catalog.domain.ShowAuditedEntity;
import com.ticket.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 특정 Performance에서 사용할 Grade다.
 *
 * <p>단순 join table이 아니라 회차별 가격과 표시 순서를 갖는 연결 entity다 — 가격의 원본은 이
 * entity이고, {@code PerformanceSeat.unitPrice}/{@code OrderSeat.unitPrice}는 판매 오픈·주문
 * 시점에 이 값을 snapshot한다. 설계 배경은
 * {@code docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md}를 본다.
 */
@Getter
@Entity
@Table(
        name = "PERFORMANCE_GRADES",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_PERFORMANCE_GRADES_PERFORMANCE_GRADE",
                columnNames = {"performance_id", "grade_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceGrade extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    /**
     * Grade는 PerformanceGrade와 다른 aggregate라 식별자로만 참조한다(같은 BC 안이어도 aggregate
     * 경계를 넘는 참조는 ID로 한다 — {@code docs/architecture.md}의 참조 규칙). 반면 위
     * {@code performance}는 같은 aggregate 안(부모)이라 객체 참조를 유지한다.
     */
    @Column(name = "grade_id", nullable = false)
    private Long gradeId;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer sortOrder;

    private PerformanceGrade(
            final Performance performance,
            final Long gradeId,
            final BigDecimal price,
            final Integer sortOrder
    ) {
        this.performance = performance;
        this.gradeId = gradeId;
        this.price = validatePrice(price);
        this.sortOrder = sortOrder;
    }

    public static PerformanceGrade assign(
            final Performance performance,
            final Long gradeId,
            final BigDecimal price,
            final Integer sortOrder
    ) {
        return new PerformanceGrade(performance, gradeId, price, sortOrder);
    }

    private BigDecimal validatePrice(final BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new InvalidRequestException("price는 0 이상이어야 합니다.");
        }
        return price;
    }
}
