package com.ticket.show.catalog.application.port;

import java.util.Optional;

import com.ticket.show.catalog.application.ShowDetailView;

public interface ShowDetailQueryPort {
    Optional<ShowDetailView> findShowDetail(Long showId);
}
