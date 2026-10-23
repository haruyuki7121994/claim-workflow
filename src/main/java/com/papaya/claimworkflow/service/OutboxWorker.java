package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.repository.OutboxRepository;
import java.time.Clock;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxWorker {
    private final OutboxRepository repository;
    private final ActionHandlerRegistry handlers;
    private final Clock clock;
    private final int maxAttempts;
    private final int leaseSeconds;
    private final int retrySeconds;

    public OutboxWorker(OutboxRepository repository, ActionHandlerRegistry handlers, Clock clock,
                        @Value("${app.outbox.max-attempts}") int maxAttempts,
                        @Value("${app.outbox.lease-seconds}") int leaseSeconds,
                        @Value("${app.outbox.retry-seconds}") int retrySeconds) {
        this.repository = repository;
        this.handlers = handlers;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
        this.retrySeconds = retrySeconds;
        if (maxAttempts < 1 || leaseSeconds < 1 || retrySeconds < 1) {
            throw new IllegalArgumentException("Outbox settings must be positive");
        }
    }

    public boolean processOne() {
        return processOne(null);
    }

    public boolean processOne(java.util.UUID claimId) {
        var next = repository.claimNext(claimId, clock.instant(), leaseSeconds, maxAttempts);
        if (next.isEmpty()) { return false; }
        var event = next.get();
        try {
            // The claim transaction and the lease-acquisition transaction have both committed.
            String result = handlers.execute(event);
            repository.succeed(event, result, clock.instant());
        } catch (Exception exception) {
            LoggerFactory.getLogger(OutboxWorker.class).warn("Action {} failed for event {}: {}",
                    event.action(), event.eventId(), exception.getClass().getSimpleName());
            repository.fail(event, "Action failed: " + exception.getClass().getSimpleName(), clock.instant(), maxAttempts, retrySeconds);
        }
        return true;
    }
}
