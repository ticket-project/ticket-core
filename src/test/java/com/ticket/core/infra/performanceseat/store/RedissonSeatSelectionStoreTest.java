package com.ticket.core.infra.performanceseat.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.List;

import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class RedissonSeatSelectionStoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private RScript script;

    @InjectMocks
    private RedissonSeatSelectionStore redissonSeatSelectionStore;

    @Test
    void 비어있는_좌석이면_selectIfAbsent가_true다() {
        doReturn(script).when(redissonClient).getScript(StringCodec.INSTANCE);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                eq(List.<Object>of(
                        SeatSelectionRedisKey.select(10L, 20L),
                        SeatSelectionRedisKey.selectSeatIndex(10L)
                )),
                eq(Duration.ofMinutes(5).toMillis()),
                eq("3"),
                eq("20")
        );

        boolean result = redissonSeatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5));

        assertThat(result).isTrue();
    }

    @Test
    void holder를_조회하고_소유자가_맞으면_해제한다() {
        doReturn(bucket).when(redissonClient).getBucket(SeatSelectionRedisKey.select(10L, 20L), StringCodec.INSTANCE);
        doReturn("3").when(bucket).get();
        doReturn(script).when(redissonClient).getScript(StringCodec.INSTANCE);
        doReturn(1L).when(script).eval(
                eq(RScript.Mode.READ_WRITE),
                anyString(),
                eq(RScript.ReturnType.LONG),
                eq(List.<Object>of(
                        SeatSelectionRedisKey.select(10L, 20L),
                        SeatSelectionRedisKey.selectSeatIndex(10L)
                )),
                eq("3"),
                eq("20")
        );

        String holder = redissonSeatSelectionStore.getHolder(10L, 20L);
        boolean released = redissonSeatSelectionStore.releaseIfOwned(10L, 20L, "3");

        assertThat(holder).isEqualTo("3");
        assertThat(released).isTrue();
    }

    @Test
    void 공연별_인덱스로_선택좌석을_조회하고_전체키를_SCAN하지_않는다() {
        //given
        doReturn(script).when(redissonClient).getScript(StringCodec.INSTANCE);
        doReturn(List.of("20", "21")).when(script).eval(
                eq(RScript.Mode.READ_ONLY),
                anyString(),
                eq(RScript.ReturnType.LIST),
                eq(List.<Object>of(SeatSelectionRedisKey.selectSeatIndex(10L)))
        );

        Set<Long> result = redissonSeatSelectionStore.getSelectingSeatIds(10L);

        assertThat(result).containsExactlyInAnyOrder(20L, 21L);
        verify(redissonClient, never()).getKeys();
    }
}
