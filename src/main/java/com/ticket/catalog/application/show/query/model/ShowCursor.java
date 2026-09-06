package com.ticket.catalog.application.show.query.model;

import com.ticket.catalog.application.show.query.ShowSort;

public record ShowCursor(
        ShowSort sort,
        String dir,
        String lastValue,
        Long lastId
) {}
