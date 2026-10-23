package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.repository.OutboxRepository.Event;
import java.util.Set;

/** Real integrations must use event.eventId() as the provider's idempotency key. */
public interface ActionHandler {
    Set<ActionType> supportedActions();
    String execute(ActionType action, Event event);
}
