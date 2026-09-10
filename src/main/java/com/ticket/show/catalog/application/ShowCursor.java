package com.ticket.show.catalog.application;

import com.ticket.show.catalog.application.ShowSort;

public record ShowCursor(
        ShowSort sort,
        String dir,
        String lastValue,
        Long lastId
) {}
