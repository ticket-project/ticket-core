package com.ticket.payment.domain;

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
 * payment module이 소유하는 entity의 감사(auditing) 공통 필드다.
 *
 * <p>module 간 JPA 상속을 피하려고 다른 module의 {@code XxxAuditedEntity}(예:
 * {@code CatalogAuditedEntity}, {@code BookingAuditedEntity})를 그대로 쓰지 않고 module-local로
 * 복제했다. 필드와 동작은 동일하다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public class PaymentAuditedEntity {

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
