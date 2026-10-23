package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.repository.OutboxRepository.Event;
import java.util.EnumSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentActionHandler implements ActionHandler {
    private static final Set<ActionType> ACTIONS = Set.copyOf(EnumSet.of(
            ActionType.CREATE_PAYMENT_REQUEST, ActionType.TRIGGER_PAYMENT));

    @Override
    public Set<ActionType> supportedActions() {
        return ACTIONS;
    }

    @Override
    public String execute(ActionType action, Event event) {
        String result = switch (action) {
            case CREATE_PAYMENT_REQUEST -> "PAY-" + event.eventId();
            case TRIGGER_PAYMENT -> "TRANSFER-" + event.eventId();
            default -> throw new IllegalArgumentException("Unsupported payment action: " + action);
        };
        log.info("Payment action={} claim={} idempotencyKey={} result={}",
                action.value(), event.claimId(), event.eventId(), result);
        return result;
    }
}
