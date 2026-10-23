package com.papaya.claimworkflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.repository.OutboxRepository.Event;
import com.papaya.claimworkflow.service.ActionHandlerRegistry;
import com.papaya.claimworkflow.service.InternalActionHandler;
import com.papaya.claimworkflow.service.NotificationActionHandler;
import com.papaya.claimworkflow.service.PaymentActionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionHandlerRegistryTests {
    private final ActionHandlerRegistry registry = new ActionHandlerRegistry(List.of(
            new NotificationActionHandler(), new PaymentActionHandler(), new InternalActionHandler()));

    @Test
    void dispatchesEachEnumToItsInjectedStrategy() {
        for (ActionType action : ActionType.values()) {
            UUID eventId = UUID.randomUUID();
            String result = registry.execute(new Event(eventId, UUID.randomUUID(), action, Map.of(), 1, UUID.randomUUID()));
            String expectedPrefix = switch (action) {
                case CREATE_PAYMENT_REQUEST -> "PAY-";
                case TRIGGER_PAYMENT -> "TRANSFER-";
                default -> "ACK-";
            };
            assertThat(result).isEqualTo(expectedPrefix + eventId);
        }
    }
}
