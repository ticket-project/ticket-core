package com.ticket.booking.hold.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RedissonHoldStoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RScoredSortedSet<Object> expiryLedger;

    @InjectMocks
    private RedissonHoldStore redissonHoldStore;

    @Test
    void release는_같은_holdKey만_삭제한다() {
        // given
        RBucket<Object> seat10 = bucketReturning("hold-key");
        RBucket<Object> seat20 = bucketReturning("other-hold");
        RBucket<Object> meta = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);

        when(redissonClient.getBucket(HoldRedisKey.hold(1L, 10L), StringCodec.INSTANCE))
                .thenReturn(seat10);
        when(redissonClient.getBucket(HoldRedisKey.hold(1L, 20L), StringCodec.INSTANCE))
                .thenReturn(seat20);
        when(redissonClient.getBucket(HoldRedisKey.holdMeta("hold-key"), StringCodec.INSTANCE))
                .thenReturn(meta);
        when(redissonClient.getSetCache(HoldRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE))
                .thenReturn(holdSeatIndex);
        when(redissonClient.getScoredSortedSet(HoldRedisKey.holdExpiryLedger(), StringCodec.INSTANCE))
                .thenReturn(expiryLedger);
        // when
        redissonHoldStore.release(1L, "hold-key", List.of(20L, 10L, 10L));

        // then
        verify(seat10).delete();
        verify(holdSeatIndex).remove(10L);
        verify(seat20, never()).delete();
        verify(holdSeatIndex, never()).remove(20L);
        verify(meta).delete();
        verify(expiryLedger).remove("hold-key");
    }

    @Test
    void release는_입력_좌석만_해제하고_메타는_항상_삭제한다() {
        RBucket<Object> seat10 = bucketReturning("hold-key");
        RBucket<Object> seat20 = mock(RBucket.class);
        RBucket<Object> meta = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);
        when(redissonClient.getBucket(HoldRedisKey.hold(1L, 10L), StringCodec.INSTANCE))
                .thenReturn(seat10);
        when(redissonClient.getBucket(HoldRedisKey.holdMeta("hold-key"), StringCodec.INSTANCE))
                .thenReturn(meta);
        when(redissonClient.getSetCache(HoldRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE))
                .thenReturn(holdSeatIndex);
        when(redissonClient.getScoredSortedSet(HoldRedisKey.holdExpiryLedger(), StringCodec.INSTANCE))
                .thenReturn(expiryLedger);
        redissonHoldStore.release(1L, "hold-key", List.of(10L));

        verify(seat10).delete();
        verify(holdSeatIndex).remove(10L);
        verify(seat20, never()).delete();
        verify(holdSeatIndex, never()).remove(20L);
        verify(meta).delete();
        verify(expiryLedger).remove("hold-key");
    }

    @Test
    void 현재_hold중인_좌석아이디들을_조회한다() {
        // given
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);
        when(redissonClient.getSetCache(HoldRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE))
                .thenReturn(holdSeatIndex);
        when(holdSeatIndex.readAll()).thenReturn(Set.of(30L, 10L));

        // when
        Set<Long> result = redissonHoldStore.getHoldingSeatIds(1L);

        // then
        assertThat(result).containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    void isHeld는_bucket값_존재여부를_반환한다() {
        // given
        RBucket<Object> seatBucket = bucketReturning("hold-key");
        when(redissonClient.getBucket(HoldRedisKey.hold(1L, 10L), StringCodec.INSTANCE))
                .thenReturn(seatBucket);

        // when
        boolean result = redissonHoldStore.isHeld(1L, 10L);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isHeldBy는_현재_holdKey가_같을_때만_true를_반환한다() {
        RBucket<Object> seatBucket = bucketReturning("current-hold");
        when(redissonClient.getBucket(HoldRedisKey.hold(1L, 10L), StringCodec.INSTANCE))
                .thenReturn(seatBucket);

        assertThat(redissonHoldStore.isHeldBy(1L, 10L, "current-hold")).isTrue();
        assertThat(redissonHoldStore.isHeldBy(1L, 10L, "stale-hold")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private RBucket<Object> bucketReturning(final String value) {
        RBucket<Object> bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn(value);
        return bucket;
    }
}
