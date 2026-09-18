package com.ticket.archfixture.left.endpoint;

import com.ticket.archfixture.left.query.FixtureRow;

/** 허용 사례: endpoint가 query package의 읽기 모델을 응답으로 변환한다. */
public class ReadModelFixtureController {
    public String render(final FixtureRow row) {
        return row.name();
    }
}
