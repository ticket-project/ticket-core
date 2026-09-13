package com.ticket.show.catalog.application;

public record ShowCursor(ShowSort sort, String dir, String lastValue, Long lastId) {}
