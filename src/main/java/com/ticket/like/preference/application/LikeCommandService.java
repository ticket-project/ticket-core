package com.ticket.like.preference.application;

import com.ticket.like.LikeCommand;
import com.ticket.like.LikeInfo;
import com.ticket.like.LikeType;
import com.ticket.like.preference.domain.LikeRepository;
import com.ticket.like.preference.exception.LikeAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link LikeCommand}의 like 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LikeCommandService implements LikeCommand {

    private final LikeRepository likeRepository;

    @Override
    public LikeInfo like(final long memberId, final LikeType likeType, final long targetId) {
        if (likeRepository.existsByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId)) {
            return new LikeInfo(true, likeRepository.countByLikeTypeAndTargetId(likeType, targetId));
        }

        try {
            likeRepository.like(memberId, likeType, targetId);
        } catch (final DataIntegrityViolationException e) {
            throw new LikeAlreadyExistsException(memberId, likeType, targetId);
        }

        return new LikeInfo(true, likeRepository.countByLikeTypeAndTargetId(likeType, targetId));
    }

    @Override
    public LikeInfo unlike(final long memberId, final LikeType likeType, final long targetId) {
        likeRepository.findByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId)
                .ifPresent(likeRepository::delete);

        return new LikeInfo(false, likeRepository.countByLikeTypeAndTargetId(likeType, targetId));
    }
}
