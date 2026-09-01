package com.ticket.core.domain.order.model;

import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(
        name = "ORDER_SEATS",
        indexes = @Index(name = "IDX_ORDER_SEATS_ORDER_ID", columnList = "order_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long performanceSeatId;

    @Column(nullable = false)
    private Long seatId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    public OrderSeat(
            final Order order,
            final Long performanceSeatId,
            final Long seatId,
            final BigDecimal price
    ) {
        this.order = order;
        this.performanceSeatId = performanceSeatId;
        this.seatId = seatId;
        this.price = price;
    }
}
