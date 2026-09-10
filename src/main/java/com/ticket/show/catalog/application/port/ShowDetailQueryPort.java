package com.ticket.show.catalog.application.port;

import com.ticket.show.catalog.application.ShowDetailView;
import java.util.Optional;

public interface ShowDetailQueryPort {

    Optional<ShowDetailView> findShowDetail(Long showId);
}
