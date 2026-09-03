package com.ticket.shared;

public enum ResultType {
    SUCCESS("성공"),
    ERROR("오류"),
    ;

    private final String description;

    ResultType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
