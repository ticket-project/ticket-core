package com.ticket.favorite.application.showlike;

import com.ticket.favorite.ShowLikeCommand;
import com.ticket.favorite.ShowLikeInfo;
import com.ticket.favorite.domain.showlike.repository.ShowLikeRepository;
import com.ticket.favorite.exception.ShowLikeAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ShowLikeCommand}의 favorite 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ShowLikeCommandService implements ShowLikeCommand {

    private final ShowLikeRepository showLikeRepository;

    @Override
    public ShowLikeInfo like(final long memberId, final long showId) {
        if (showLikeRepository.existsByMemberIdAndShowId(memberId, showId)) {
            return new ShowLikeInfo(true, showLikeRepository.countByShowId(showId));
        }

        try {
            showLikeRepository.like(memberId, showId);
        } catch (final DataIntegrityViolationException e) {
            throw new ShowLikeAlreadyExistsException(memberId, showId);
        }

        return new ShowLikeInfo(true, showLikeRepository.countByShowId(showId));
    }

    @Override
    public ShowLikeInfo unlike(final long memberId, final long showId) {
        showLikeRepository.findByMemberIdAndShowId(memberId, showId)
                .ifPresent(showLikeRepository::delete);

        return new ShowLikeInfo(false, showLikeRepository.countByShowId(showId));
    }
}
