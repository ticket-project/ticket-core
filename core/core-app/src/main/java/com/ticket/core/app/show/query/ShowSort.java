package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.meta.ShowSortKey;

public final class ShowSort {

    private final ShowSortKey key;

    private ShowSort(final ShowSortKey key) {
        this.key = key;
    }

    public static ShowSort from(final String apiValue) {
        return new ShowSort(ShowSortKey.fromApiValue(apiValue));
    }

    public ShowSortKey key() {
        return key;
    }

    public String apiValue() {
        return key.getApiValue();
    }
}
