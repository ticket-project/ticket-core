package com.ticket.booking.internal.infrastructure.hold;

import com.ticket.booking.internal.domain.hold.model.Hold;
import com.ticket.booking.internal.infrastructure.performanceseat.store.SeatSelectionRedisKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RedissonHoldStoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private HoldMetaCodec holdMetaCodec;

    @InjectMocks
    private RedissonHoldStore redissonHoldStore;

    @Test
    void 홀드를_저장하면_좌석키와_메타키를_저장한다() {
        //given
        Duration ttl = Duration.ofMinutes(5);
        Hold hold = new Hold("hold-key", 7L, 1L, List.of(10L, 20L), LocalDateTime.of(2026, 3, 15, 19, 5));
        RBucket<Object> seat10 = mock(RBucket.class);
        RBucket<Object> seat20 = mock(RBucket.class);
        RBucket<Object> meta = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);

        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 10L), StringCodec.INSTANCE)).thenReturn(seat10);
        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 20L), StringCodec.INSTANCE)).thenReturn(seat20);
        when(redissonClient.getBucket(SeatSelectionRedisKey.holdMeta("hold-key"), StringCodec.INSTANCE)).thenReturn(meta);
        when(redissonClient.getSetCache(SeatSelectionRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE)).thenReturn(holdSeatIndex);
        when(holdMetaCodec.encode(any(Hold.class))).thenReturn("payload");

        //when
        redissonHoldStore.save(hold, ttl);

        //then
        verify(seat10).set("hold-key", ttl);
        verify(seat20).set("hold-key", ttl);
        verify(meta).set("payload", ttl);
        verify(holdSeatIndex).add(10L, ttl.toMillis(), TimeUnit.MILLISECONDS);
        verify(holdSeatIndex).add(20L, ttl.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void 홀드저장_중_예외가_나면_생성한_좌석키를_롤백한다() {
        //given
        Duration ttl = Duration.ofMinutes(5);
        Hold hold = new Hold("hold-key", 7L, 1L, List.of(10L, 20L), LocalDateTime.of(2026, 3, 15, 19, 5));
        RBucket<Object> seat10 = mock(RBucket.class);
        RBucket<Object> seat20 = mock(RBucket.class);
        RBucket<Object> meta = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);

        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 10L), StringCodec.INSTANCE)).thenReturn(seat10);
        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 20L), StringCodec.INSTANCE)).thenReturn(seat20);
        when(redissonClient.getBucket(SeatSelectionRedisKey.holdMeta("hold-key"), StringCodec.INSTANCE)).thenReturn(meta);
        when(redissonClient.getSetCache(SeatSelectionRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE)).thenReturn(holdSeatIndex);
        doThrow(new RuntimeException("boom")).when(seat20).set("hold-key", ttl);

        //when
        //then
        assertThatThrownBy(() -> redissonHoldStore.save(hold, ttl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hold Redis");

        verify(seat10).delete();
        verify(meta).delete();
    }

    @Test
    void release는_같은_holdKey만_삭제한다() {
        //given
        RBucket<Object> seat10 = bucketReturning("hold-key");
        RBucket<Object> seat20 = bucketReturning("other-hold");
        RBucket<Object> meta = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);

        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 10L), StringCodec.INSTANCE)).thenReturn(seat10);
        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 20L), StringCodec.INSTANCE)).thenReturn(seat20);
        when(redissonClient.getBucket(SeatSelectionRedisKey.holdMeta("hold-key"), StringCodec.INSTANCE)).thenReturn(meta);
        when(redissonClient.getSetCache(SeatSelectionRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE)).thenReturn(holdSeatIndex);
        when(meta.get()).thenReturn("{\"holdKey\":\"hold-key\",\"memberId\":7,\"performanceId\":1,\"seatIds\":[10,20],\"expiresAt\":\"2026-03-15T19:05:00\"}");
        when(holdMetaCodec.decode("{\"holdKey\":\"hold-key\",\"memberId\":7,\"performanceId\":1,\"seatIds\":[10,20],\"expiresAt\":\"2026-03-15T19:05:00\"}"))
                .thenReturn(new Hold("hold-key", 7L, 1L, List.of(10L, 20L), LocalDateTime.of(2026, 3, 15, 19, 5)));

        //when
        List<Long> releasedSeatIds = redissonHoldStore.release(1L, "hold-key", List.of(20L, 10L, 10L));

        //then
        verify(seat10).delete();
        verify(holdSeatIndex).remove(10L);
        verify(meta, org.mockito.Mockito.never()).delete();
        assertThat(releasedSeatIds).containsExactly(10L);
    }

    @Test
    void release는_잠근_입력_좌석만_해제하고_snapshot_전체가_아니면_메타를_유지한다() {
        RBucket<Object> seat10 = bucketReturning("hold-key");
        RBucket<Object> seat20 = mock(RBucket.class);
        RBucket<Object> meta = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);
        String payload = "{\"holdKey\":\"hold-key\",\"memberId\":7,\"performanceId\":1,\"seatIds\":[10,20],\"expiresAt\":\"2026-03-15T19:05:00\"}";

        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 10L), StringCodec.INSTANCE)).thenReturn(seat10);
        when(redissonClient.getBucket(SeatSelectionRedisKey.holdMeta("hold-key"), StringCodec.INSTANCE)).thenReturn(meta);
        when(redissonClient.getSetCache(SeatSelectionRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE)).thenReturn(holdSeatIndex);
        when(meta.get()).thenReturn(payload);
        when(holdMetaCodec.decode(payload))
                .thenReturn(new Hold("hold-key", 7L, 1L, List.of(10L, 20L), LocalDateTime.of(2026, 3, 15, 19, 5)));

        List<Long> releasedSeatIds = redissonHoldStore.release(1L, "hold-key", List.of(10L));

        verify(seat10).delete();
        verify(holdSeatIndex).remove(10L);
        verify(seat20, never()).delete();
        verify(holdSeatIndex, never()).remove(20L);
        verify(meta, never()).delete();
        assertThat(releasedSeatIds).containsExactly(10L);
    }

    @Test
    void 현재_hold중인_좌석아이디들을_조회한다() {
        //given
        @SuppressWarnings("unchecked")
        RSetCache<Object> holdSeatIndex = mock(RSetCache.class);
        when(redissonClient.getSetCache(SeatSelectionRedisKey.holdSeatIndex(1L), LongCodec.INSTANCE)).thenReturn(holdSeatIndex);
        when(holdSeatIndex.readAll()).thenReturn(Set.of(30L, 10L));

        //when
        Set<Long> result = redissonHoldStore.getHoldingSeatIds(1L);

        //then
        assertThat(result).containsExactlyInAnyOrder(10L, 30L);
    }

    @Test
    void isHeld는_bucket값_존재여부를_반환한다() {
        //given
        RBucket<Object> seatBucket = bucketReturning("hold-key");
        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 10L), StringCodec.INSTANCE)).thenReturn(seatBucket);

        //when
        boolean result = redissonHoldStore.isHeld(1L, 10L);

        //then
        assertThat(result).isTrue();
    }

    @Test
    void isHeldBy는_현재_holdKey가_같을_때만_true를_반환한다() {
        RBucket<Object> seatBucket = bucketReturning("current-hold");
        when(redissonClient.getBucket(SeatSelectionRedisKey.hold(1L, 10L), StringCodec.INSTANCE)).thenReturn(seatBucket);

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
