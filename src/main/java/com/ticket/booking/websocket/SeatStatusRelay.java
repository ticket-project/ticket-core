package com.ticket.booking.websocket;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 좌석 상태 알림 relay. 좌석 상태를 바꾸는 Lua 스크립트가 바꾼 그 자리에서 채널 하나({@link #CHANNEL})에 {@code PUBLISH}하고, 모든 인스턴스가 그 채널을 구독해 자기
 * STOMP 사용자에게 보낸다. 발행한 인스턴스도 자기 사용자에게 직접 보내지 않고 구독으로 받는다 — 전달 경로가 하나라 중복도, 경로 사이의 순서 차이도 없다(스케일 아웃 결정 D2-1).
 *
 * <p><b>받은 알림은 바로 보내지 않고 회차별로 모았다가 {@link #FLUSH_INTERVAL}마다 한 메시지로 보낸다</b>(2026-11-06). 알림 하나를 그 회차 구독자 전원에게 하나씩 보내면
 * 보내는 수가 "알림 수 × 구독자 수"라 사용자 수의 제곱으로 는다(운영 03-3 25 u/s에서 알림 1,567건 → 메시지 26만 7,742건). 모아 보내면 구독자마다 간격당 메시지 하나라 사용자 수에
 * 비례한다. 같은 좌석이 그 사이 여러 번 바뀌면 마지막 상태만 보낸다(선택했다 바로 풀면 DESELECTED 하나) — 화면은 좌석의 지금 상태만 그린다. 대가로 알림이 최대 간격만큼 늦다.
 *
 * <p>Redis가 스크립트를 하나씩 실행하고 {@code PUBLISH}를 처리한 순서대로 보내며, 구독은 단일 스레드로 받고({@link SeatStatusRelayConfig}) 묶음은 단일 스레드가 차례로
 * 보내므로 같은 좌석의 상태 순서가 바뀐 순서와 같다. 묶음 안 좌석 순서는 그 간격에서 마지막으로 바뀐 순서다.
 *
 * <p>Redis pub/sub은 최대 한 번 전달이다. 구독이 끊긴 동안의 알림, 서버가 멈출 때 아직 보내지 않은 묶음은 다시 오지 않는다. 사용자가 화면을 새로 불러오면 맞춰진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatStatusRelay implements MessageListener, SmartLifecycle {
    public static final String CHANNEL = "seat:status:relay";

    /** 좌석 알림을 모으는 간격. 길수록 구독자마다 보내는 메시지가 줄고 알림은 늦어진다. 250ms면 구독자마다 초당 메시지 4개다(2026-11-06 결정). 사람이 거의 느끼지 못하는 늦음이다. */
    static final Duration FLUSH_INTERVAL = Duration.ofMillis(250);

    private static final String SEAT_TOPIC_FORMAT = "/topic/performance/%d/seats";

    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper jsonMapper;

    /** 회차 → (좌석 → 그 좌석의 마지막 알림). 좌석 순서는 마지막으로 바뀐 순서다. {@code this}로 잠근다. */
    private Map<Long, LinkedHashMap<Long, SeatStatusEvent>> pending = new HashMap<>();

    /**
     * 묶음을 보내는 스레드. 스프링 빈으로 두지 않는다 — {@code TaskScheduler} 빈이 하나 더 생기면 {@code @Scheduled} 작업이 어느 스케줄러를 쓸지가 바뀐다. 다른 예약
     * 작업이 늦어도 알림이 밀리지 않게 따로 두고, 스레드가 하나라 묶음이 차례로 나간다.
     */
    private @Nullable ScheduledExecutorService flusher;

    /** 회차 destination에 가는 묶음 payload다. 좌석마다 마지막 상태 하나다. */
    public record SeatStatusBatch(Long performanceId, List<SeatStatusChange> changes) {}

    public record SeatStatusChange(@Nullable Long performanceSeatId, Long seatId, SeatStatusAction action) {}

    /** 스크립트가 {@code PUBLISH}할 메시지. 상태를 바꾸는 쪽이 알림이 나갈지 모르는 채로 미리 만들어 넘긴다. */
    public String encode(final SeatStatusEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

    @Override
    public void onMessage(final Message message, final byte @Nullable [] pattern) {
        final SeatStatusEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), SeatStatusEvent.class);
        } catch (final JacksonException e) {
            log.warn("좌석 상태 relay 메시지를 읽지 못해 버립니다.", e);
            return;
        }
        synchronized (this) {
            final LinkedHashMap<Long, SeatStatusEvent> seats =
                    pending.computeIfAbsent(event.performanceId(), id -> new LinkedHashMap<>());
            // 마지막으로 바뀐 순서를 지키려고 지웠다가 다시 넣는다.
            seats.remove(event.seatId());
            seats.put(event.seatId(), event);
        }
    }

    @Override
    public synchronized void start() {
        if (flusher != null) {
            return;
        }
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "seat-status-flush");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                this::flushSafely, FLUSH_INTERVAL.toMillis(), FLUSH_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
        flusher = executor;
    }

    /** 멈추기 전에 남은 묶음을 한 번 보낸다. 브로커가 이미 멈췄으면 버려진다(최대 한 번 전달과 같음). */
    @Override
    public synchronized void stop() {
        final ScheduledExecutorService executor = flusher;
        if (executor == null) {
            return;
        }
        flusher = null;
        executor.shutdown();
        flushSafely();
    }

    @Override
    public synchronized boolean isRunning() {
        return flusher != null;
    }

    /** 예외가 나도 다음 간격의 전송은 계속된다. {@code scheduleWithFixedDelay}는 작업이 예외를 던지면 반복을 멈춘다. */
    private void flushSafely() {
        try {
            flush();
        } catch (final RuntimeException e) {
            log.warn("좌석 상태 묶음 전송에 실패했습니다.", e);
        }
    }

    /** 모아 둔 알림을 회차마다 한 메시지로 보낸다. 묶음 전송 스레드가 {@link #FLUSH_INTERVAL}마다 부른다. */
    public void flush() {
        final Map<Long, LinkedHashMap<Long, SeatStatusEvent>> batch;
        synchronized (this) {
            if (pending.isEmpty()) {
                return;
            }
            batch = pending;
            pending = new HashMap<>();
        }
        batch.forEach((performanceId, seats) -> {
            final List<SeatStatusChange> changes = new ArrayList<>(seats.size());
            seats.values()
                    .forEach(event -> changes.add(
                            new SeatStatusChange(event.performanceSeatId(), event.seatId(), event.action())));
            // 그 회차를 구독한 사용자가 이 인스턴스에 없으면 simple broker가 버린다.
            messagingTemplate.convertAndSend(
                    String.format(SEAT_TOPIC_FORMAT, performanceId), new SeatStatusBatch(performanceId, changes));
        });
    }
}
