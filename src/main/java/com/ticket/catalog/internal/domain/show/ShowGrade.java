package com.ticket.catalog.internal.domain.show;

import com.ticket.catalog.internal.domain.CatalogAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "SHOW_GRADES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowGrade extends CatalogAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(nullable = false)
    private String gradeCode;

    @Column(nullable = false)
    private String gradeName;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer sortOrder;

    private ShowGrade(
            final Show show,
            final String gradeCode,
            final String gradeName,
            final BigDecimal price,
            final Integer sortOrder
    ) {
        this.show = show;
        this.gradeCode = gradeCode;
        this.gradeName = gradeName;
        this.price = price;
        this.sortOrder = sortOrder;
    }

    public static ShowGrade link(
            final Show show,
            final String gradeCode,
            final String gradeName,
            final BigDecimal price,
            final Integer sortOrder
    ) {
        return new ShowGrade(show, gradeCode, gradeName, price, sortOrder);
    }

}
