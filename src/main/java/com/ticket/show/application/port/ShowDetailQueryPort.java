package com.ticket.show.application.port;

import java.util.Optional;

import com.ticket.show.application.ShowDetailView;

public interface ShowDetailQueryPort {
    Optional<ShowDetailView> findShowDetail(Long showId);
}
