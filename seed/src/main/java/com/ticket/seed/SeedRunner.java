package com.ticket.seed;

import java.util.ArrayList;
import java.util.List;

/**
 * 시드 작업을 순서대로 실행하고 작업별 결과를 모은다.
 *
 * <p>작업 하나가 실패하면 그 자리에서 멈춘다. 이미 커밋된 앞 작업은 남아 있으므로, 보고에서
 * "적재됨 / 건너뜀 / 실패(롤백) / 실행 안 함"을 작업 단위로 구분해 출력한다.
 */
final class SeedRunner {

    private SeedRunner() {
    }

    static List<Report> run(final List<SeedTask> tasks) {
        final List<Report> reports = new ArrayList<>(tasks.size());
        boolean stopped = false;

        for (final SeedTask task : tasks) {
            if (stopped) {
                reports.add(new Report(task.name(), Status.NOT_RUN, "앞 작업이 실패해 실행하지 않았습니다.", null));
                continue;
            }

            try {
                final SeedTask.Outcome outcome = task.run();
                reports.add(new Report(
                        task.name(),
                        outcome.skipped() ? Status.SKIPPED : Status.LOADED,
                        outcome.summary(),
                        null));
            } catch (final RuntimeException exception) {
                reports.add(new Report(
                        task.name(),
                        Status.FAILED,
                        "실패했습니다. 이 작업의 트랜잭션은 롤백됐습니다.",
                        exception));
                stopped = true;
            }
        }

        return reports;
    }

    enum Status {
        LOADED("적재"), SKIPPED("건너뜀"), FAILED("실패(롤백)"), NOT_RUN("실행 안 함");

        private final String label;

        Status(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record Report(String taskName, Status status, String summary, RuntimeException failure) {
    }
}
