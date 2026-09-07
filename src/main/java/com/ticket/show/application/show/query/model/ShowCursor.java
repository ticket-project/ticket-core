package com.ticket.show.application.show.query.model;

import com.ticket.show.application.show.query.ShowSort;

public record ShowCursor(
        ShowSort sort,
        String dir,
        String lastValue,
        Long lastId
) {}
