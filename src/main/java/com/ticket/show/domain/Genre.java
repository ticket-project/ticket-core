package com.ticket.show.domain;

import com.ticket.show.domain.ShowAuditedEntity;
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
public class Genre extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /**
     * Category는 Genre와 다른 aggregate라 식별자로만 참조한다(같은 BC 안이어도 aggregate 경계를
     * 넘는 참조는 ID로 한다 — {@code docs/architecture.md}의 참조 규칙). 컬럼명은 옛
     * {@code @ManyToOne Category category} 매핑과 같은 {@code category_id}를 그대로 쓴다.
     */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    public Genre(String code, String name, Long categoryId) {
        this.code = code;
        this.name = name;
        this.categoryId = categoryId;
    }

}
