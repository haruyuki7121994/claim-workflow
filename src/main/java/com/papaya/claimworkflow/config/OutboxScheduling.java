package com.papaya.claimworkflow.config;

import com.papaya.claimworkflow.service.OutboxWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduling {
    private final OutboxWorker worker;
    public OutboxScheduling(OutboxWorker worker) { this.worker = worker; }

    @Scheduled(fixedDelayString = "${app.outbox.delay-ms}")
    public void poll() {
        for (int i = 0; i < 50 && worker.processOne(); i++) {
            // Bound each polling pass so one instance cannot monopolize the queue.
        }
    }
}
