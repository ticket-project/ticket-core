package com.ticket.booking.selection.usecase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** 좌석 선택 만료 알림을 주기적으로 깨운다(기본 1초). 모든 인스턴스에서 돈다 — 장부 항목은 한 인스턴스만 꺼낸다. */
@Component
@ConditionalOnProperty(prefix = "worker", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SeatSelectionExpirationTrigger {
    private final NotifyExpiredSelectionsUseCase notifyExpiredSelectionsUseCase;

    @Scheduled(fixedDelayString = "${worker.seat-selection-expiration.fixed-delay:1000}")
    public void run() {
        notifyExpiredSelectionsUseCase.execute();
    }
}
