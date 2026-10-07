package com.ticket.venue.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.ticket.shared.jpa.AuditedEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** VENUES의 {@code address_detail}·{@code zip_code}·{@code gap_x}·{@code gap_y} 컬럼은 읽는 곳이 없어 매핑하지 않는다(Issue #252). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "VENUES")
public class Venue extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;

    @Enumerated(EnumType.STRING)
    private Region region;

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

    public Venue(
            final String name,
            final String address,
            final Region region,
            final BigDecimal latitude,
            final BigDecimal longitude,
            final String phone,
            final String imageUrl,
            final int viewBoxWidth,
            final int viewBoxHeight,
            final double seatDiameter) {
        this.name = name;
        this.address = address;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.imageUrl = imageUrl;
        this.viewBoxWidth = viewBoxWidth;
        this.viewBoxHeight = viewBoxHeight;
        this.seatDiameter = seatDiameter;
    }
}
