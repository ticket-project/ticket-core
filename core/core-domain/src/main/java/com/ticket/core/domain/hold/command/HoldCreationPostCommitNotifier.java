package com.ticket.core.domain.hold.command;

public interface HoldCreationPostCommitNotifier {

    void notify(Long outboxId);
}
