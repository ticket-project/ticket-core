package com.ticket.show.query;

import java.util.Optional;

public interface ShowDetailQueryPort {
    Optional<ShowDetailView> findShowDetail(Long showId);
}
