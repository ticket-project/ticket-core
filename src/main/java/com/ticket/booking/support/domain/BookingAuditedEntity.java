package com.ticket.booking.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Booking module 소유 entity의 audited base다. module 밖의 공용 base entity를 상속하지 않기 위해
 * 같은 필드 구성을 booking 안에 둔다 — 각 module이 자기 audited base를 갖는다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public class BookingAuditedEntity {

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false, nullable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}
