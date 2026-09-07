package com.ticket.show.application.show.query;

import com.ticket.show.application.show.query.model.ShowDetailView;
import java.util.Optional;

public interface ShowDetailReadRepository {

    Optional<ShowDetailView> findShowDetail(Long showId);
}
