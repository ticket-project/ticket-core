package com.ticket.core.domain.show.model;

import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장르 엔티티
 * - 카테고리에 속하는 세부 분류
 * - 예: 콘서트(카테고리) -> 힙합, R&B, K-POP(장르)
 */
@Getter
@Entity
@Table(name = "GENRES")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Genre extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public Genre(String code, String name, Category category) {
        this.code = code;
        this.name = name;
        this.category = category;
    }

}
