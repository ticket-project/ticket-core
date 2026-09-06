package com.ticket.catalog.domain.show;

import com.ticket.catalog.domain.CatalogAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "VENUES")
public class Venue extends CatalogAuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;

    @Enumerated(EnumType.STRING)
    private Region region;

    private String addressDetail;
    private String zipCode;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    private String phone;
    private String imageUrl;

    // --- 좌석 맵 SVG 레이아웃 ---
    private int viewBoxWidth;
    private int viewBoxHeight;
    private double seatDiameter;
    @Column(name = "GAP_X")
    private double gapX;
    @Column(name = "GAP_Y")
    private double gapY;

    private Venue(
            final String name,
            final String address,
            final Region region,
            final String addressDetail,
            final String zipCode,
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String phone,
            final String imageUrl,
            final int viewBoxWidth,
            final int viewBoxHeight,
            final double seatDiameter,
            final double gapX,
            final double gapY
    ) {
        this.name = name;
        this.address = address;
        this.region = region;
        this.addressDetail = addressDetail;
        this.zipCode = zipCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.imageUrl = imageUrl;
        this.viewBoxWidth = viewBoxWidth;
        this.viewBoxHeight = viewBoxHeight;
        this.seatDiameter = seatDiameter;
        this.gapX = gapX;
        this.gapY = gapY;
    }

    public static Venue create(
            final String name,
            final String address,
            final Region region,
            final String addressDetail,
            final String zipCode,
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String phone,
            final String imageUrl,
            final int viewBoxWidth,
            final int viewBoxHeight,
            final double seatDiameter,
            final double gapX,
            final double gapY
    ) {
        return new Venue(
                name,
                address,
                region,
                addressDetail,
                zipCode,
                latitude,
                longitude,
                phone,
                imageUrl,
                viewBoxWidth,
                viewBoxHeight,
                seatDiameter,
                gapX,
                gapY
        );
    }
}
