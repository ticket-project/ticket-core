package com.ticket.core.app.show.query;

import java.util.Optional;

public interface ShowDetailReadRepository {

    Optional<GetShowDetailUseCase.Output> findShowDetail(Long showId);
}
