package com.ticket.core.api.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class QueueAdminRemovalTest {

    @Test
    void queue_관리자_전용_타입은_더이상_존재하지_않아야_한다() {
        assertMissing("com.ticket.core.api.controller.QueueAdminController");
        assertMissing("com.ticket.core.api.controller.docs.QueueAdminControllerDocs");
        assertMissing("com.ticket.core.api.controller.request.UpdateQueuePolicyRequest");
        assertMissing("com.ticket.core.domain.response.QueuePolicyResponse");
        assertMissing("com.ticket.core.domain.queue.support.QueuePolicyAdminService");
    }

    private void assertMissing(final String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
