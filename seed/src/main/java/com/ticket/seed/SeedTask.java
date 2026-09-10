package com.ticket.seed;

/**
 * 시드 작업 하나다. 각 작업은 자기 트랜잭션 안에서 끝나고, 성공·건너뜀·실패를 스스로 설명한다.
 *
 * <p>작업을 나눠 두는 이유는 실패 보고다. 앞 작업이 커밋된 뒤 뒤 작업이 실패하면 "무엇이 남고
 * 무엇이 되돌아갔는지"를 작업 단위로 정확히 말할 수 있어야 한다.
 */
interface SeedTask {

    String name();

    Outcome run();

    /** 작업이 실제로 무엇을 했는지에 대한 한 줄 요약이다. */
    record Outcome(boolean skipped, String summary) {

        static Outcome done(final String summary) {
            return new Outcome(false, summary);
        }

        static Outcome skipped(final String summary) {
            return new Outcome(true, summary);
        }
    }
}
