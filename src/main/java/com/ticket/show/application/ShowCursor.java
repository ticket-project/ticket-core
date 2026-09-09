package com.ticket.show.application;

import com.ticket.show.application.ShowSort;

public record ShowCursor(
        ShowSort sort,
        String dir,
        String lastValue,
        Long lastId
) {}
