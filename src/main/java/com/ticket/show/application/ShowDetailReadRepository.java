package com.ticket.show.application;

import com.ticket.show.application.ShowDetailView;
import java.util.Optional;

public interface ShowDetailReadRepository {

    Optional<ShowDetailView> findShowDetail(Long showId);
}
