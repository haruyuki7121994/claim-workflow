package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.repository.OutboxRepository.Event;
import java.util.EnumSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalActionHandler implements ActionHandler {
    private static final Set<ActionType> ACTIONS = Set.copyOf(EnumSet.of(
            ActionType.LOG_ASSESSMENT_START,
            ActionType.RESET_ASSESSMENT_TIMER,
            ActionType.ARCHIVE_CLAIM));

    @Override
    public Set<ActionType> supportedActions() {
        return ACTIONS;
    }

    @Override
    public String execute(ActionType action, Event event) {
        String result = "ACK-" + event.eventId();
        log.info("Internal action={} claim={} idempotencyKey={} result={}",
                action.value(), event.claimId(), event.eventId(), result);
        return result;
    }
}
