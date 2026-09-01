package com.ticket.core.domain.commoncode.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CommonCodeDetailId implements Serializable {

    @Column(name = "group_code", length = 50, nullable = false)
    private String groupCode;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    protected CommonCodeDetailId() {}

    public CommonCodeDetailId(String groupCode, String code) {
        this.groupCode = groupCode;
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommonCodeDetailId that)) return false;
        return Objects.equals(groupCode, that.groupCode) && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupCode, code);
    }
}
