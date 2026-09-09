package com.ticket.show.application.port;

import com.ticket.show.application.ShowDetailView;
import java.util.Optional;

public interface ShowDetailQueryPort {

    Optional<ShowDetailView> findShowDetail(Long showId);
}
