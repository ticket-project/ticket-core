package com.ticket.core.domain.performanceseat.command;

import java.util.List;
import java.util.function.Consumer;

public final class DeselectedSeatIds {

    private final List<Long> values;

    private DeselectedSeatIds(final List<Long> values) {
        this.values = List.copyOf(values);
    }

    public static DeselectedSeatIds from(final List<Long> values) {
        return new DeselectedSeatIds(values);
    }

    public void forEach(final Consumer<Long> action) {
        values.forEach(action);
    }

    public List<Long> values() {
        return values;
    }
}
