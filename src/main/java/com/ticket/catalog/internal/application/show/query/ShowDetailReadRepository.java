package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.internal.application.show.query.model.ShowDetailView;
import java.util.Optional;

public interface ShowDetailReadRepository {

    Optional<ShowDetailView> findShowDetail(Long showId);
}
