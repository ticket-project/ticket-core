package com.ticket.show.domain;

import com.ticket.show.domain.ShowAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좌석 등급의 재사용 가능한 코드·이름이다.
 *
 * <p>가격이나 표시 순서를 갖지 않는다 — 둘 다 같은 Grade라도 회차(Performance)마다 달라질 수 있어
 * {@code PerformanceGrade}가 대신 소유한다. 설계 배경은
 * {@code docs/adr/0005-performance-grade-price-ownership-and-payment-ticketing-modules.md}를 본다.
 */
@Getter
@Entity
@Table(name = "GRADES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Grade extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private Grade(final String code, final String name) {
        this.code = code;
        this.name = name;
    }

    public static Grade of(final String code, final String name) {
        return new Grade(code, name);
    }
}
