package com.ticket.archfixture.left.endpoint;

import com.ticket.archfixture.left.usecase.FixtureRow;

/** 허용 사례: endpoint가 use case가 소유한 응답 record를 변환한다. */
public class ReadModelFixtureController {
    public String render(final FixtureRow row) {
        return row.name();
    }
}
