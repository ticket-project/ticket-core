package com.ticket.show.domain;

import com.ticket.show.domain.ShowAuditedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "PERFORMERS")
public class Performer extends ShowAuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String profileImageUrl;

    private Performer(final String name, final String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
    }

    public static Performer create(final String name, final String profileImageUrl) {
        return new Performer(name, profileImageUrl);
    }

}
