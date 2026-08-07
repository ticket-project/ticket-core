package com.ticket.core.domain.hold.command;

import com.ticket.core.domain.hold.model.HoldSnapshot;

public interface HoldCreationPostCommitNotifier {

    void notify(HoldSnapshot snapshot);
}
