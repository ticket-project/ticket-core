package com.ticket.core.domain.commoncode.model;

import com.ticket.core.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "COMMON_CODE_GROUPS")
public class CommonCodeGroupEntity extends BaseEntity {

    @Id
    @Column(name = "group_code", length = 50)
    private String groupCode;

    @Column(name = "group_name", length = 100, nullable = false)
    private String groupName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "use_yn", length = 1)
    private boolean useYn;

    protected CommonCodeGroupEntity() {}

}
