package com.ticket.catalog.internal.domain.show;

import com.ticket.catalog.internal.domain.CatalogAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CATEGORIES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends CatalogAuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private Category(final String code, final String name) {
        this.code = code;
        this.name = name;
    }

    public static Category of(final String code, final String name) {
        return new Category(code, name);
    }

}
