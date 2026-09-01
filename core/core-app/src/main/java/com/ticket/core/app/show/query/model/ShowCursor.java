package com.ticket.core.app.show.query.model;

import com.ticket.core.app.show.query.ShowSort;

public record ShowCursor(
        ShowSort sort,
        String dir,
        String lastValue,
        Long lastId
) {}
