package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.repository.OutboxRepository.Event;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ActionHandlerRegistry {
    private final Map<ActionType, ActionHandler> handlers;

    public ActionHandlerRegistry(List<ActionHandler> strategies) {
        Map<ActionType, ActionHandler> registered = new EnumMap<>(ActionType.class);
        for (ActionHandler strategy : strategies) {
            for (ActionType action : strategy.supportedActions()) {
                ActionHandler previous = registered.putIfAbsent(action, strategy);
                if (previous != null) {
                    throw new IllegalStateException("Multiple handlers registered for action " + action);
                }
            }
        }
        Set<ActionType> missing = EnumSet.allOf(ActionType.class);
        missing.removeAll(registered.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No action handler registered for " + missing);
        }
        handlers = Map.copyOf(registered);
    }

    public boolean supportsAll(Iterable<ActionType> actions) {
        for (ActionType action : actions) if (!handlers.containsKey(action)) return false;
        return true;
    }

    public String execute(Event event) {
        ActionHandler handler = handlers.get(event.action());
        if (handler == null) throw new IllegalArgumentException("Unknown action: " + event.action());
        return handler.execute(event.action(), event);
    }
}
