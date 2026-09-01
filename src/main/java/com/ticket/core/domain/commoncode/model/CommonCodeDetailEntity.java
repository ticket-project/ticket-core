package com.ticket.core.domain.commoncode.model;

import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "COMMON_CODE_DETAILS")
public class CommonCodeDetailEntity extends BaseEntity {

    @EmbeddedId
    private CommonCodeDetailId id;

    @MapsId("groupCode") // id.groupCode 값과 FK를 연결
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_code", nullable = false)
    private CommonCodeGroupEntity group;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "use_yn", length = 1, nullable = false)
    private boolean useYn;

    protected CommonCodeDetailEntity() {}
}
