package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.GuardType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class GuardRegistry {
    private final Map<GuardType, Predicate<Claim>> guardOptions = new EnumMap<>(GuardType.class);

    public GuardRegistry(List<GuardHandler> strategies) {
        for (GuardHandler strategy : strategies) {
            register(strategy.type(), strategy);
        }
    }

    public void register(GuardType type, Predicate<Claim> guard) {
        if (guard == null) {
            throw new IllegalArgumentException("Guard cannot be null for " + type);
        }
        if (guardOptions.putIfAbsent(type, guard) != null) {
            throw new IllegalStateException("Duplicate guard handler: " + type);
        }
    }

    public Set<String> names() {
        return guardOptions.keySet().stream().map(GuardType::value)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean passes(String name, Claim claim) {
        var type = GuardType.find(name).orElseThrow(() -> new IllegalArgumentException("Unknown guard: " + name));
        Predicate<Claim> guard = guardOptions.get(type);
        if (guard == null) {
            throw new IllegalArgumentException("Unknown guard: " + name);
        }
        return guard.test(claim);
    }
}
