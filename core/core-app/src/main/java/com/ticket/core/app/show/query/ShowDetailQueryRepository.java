package com.ticket.core.app.show.query;

import java.util.Optional;

public interface ShowDetailQueryRepository {

    Optional<GetShowDetailUseCase.Output> findShowDetail(Long showId);
}
