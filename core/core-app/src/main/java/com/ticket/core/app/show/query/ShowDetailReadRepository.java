package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.ShowDetailView;
import java.util.Optional;

public interface ShowDetailReadRepository {

    Optional<ShowDetailView> findShowDetail(Long showId);
}
