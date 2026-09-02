package com.ticket.bootstrap.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Spring Modulith의 JPA event publication registry를 운영 관점에서 관리한다.
 *
 * <p>완료된 publication은 일정 기간 뒤 archive에서 지우고, 실패한 publication은 주기적으로
 * 재제출한다. 10회를 초과해 계속 실패하는 publication은 자동 재제출 대상에서 제외하고 구조화된
 * 로그로 남겨, 수동 복구 runbook에서 해당 이벤트를 찾아 대응할 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EventPublicationMaintenance {

    private static final int MAX_COMPLETION_ATTEMPTS = 10;

    private final CompletedEventPublications completedEventPublications;
    private final FailedEventPublications failedEventPublications;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    void purgeArchive() {
        completedEventPublications.deletePublicationsOlderThan(Duration.ofDays(30));
    }

    @Scheduled(fixedDelayString = "PT1M")
    void resubmitFailed() {
        failedEventPublications.resubmit(
                ResubmissionOptions.defaults()
                        .withBatchSize(100)
                        .withMaxInFlight(4)
                        .withFilter(this::isRetryable));
    }

    /**
     * {@value #MAX_COMPLETION_ATTEMPTS}회를 초과해 실패한 publication은 자동 재제출에서 제외하고
     * alert 가능한 신호를 구조화된 로그로 남긴다. 이벤트 식별자는 수동 복구 runbook에서 조회 키로 쓴다.
     */
    private boolean isRetryable(final EventPublication publication) {
        if (publication.getCompletionAttempts() <= MAX_COMPLETION_ATTEMPTS) {
            return true;
        }
        log.error(
                "event publication이 최대 재시도 횟수를 초과해 자동 재제출에서 제외됩니다. "
                        + "eventPublicationId={}, completionAttempts={}, event={}",
                publication.getIdentifier(),
                publication.getCompletionAttempts(),
                publication.getEvent()
        );
        return false;
    }
}
