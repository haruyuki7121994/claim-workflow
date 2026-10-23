package com.papaya.claimworkflow.service.guard;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.repository.OutboxRepository;
import com.papaya.claimworkflow.service.GuardHandler;
import org.springframework.stereotype.Service;

@Service
public class PaymentConfirmedHandler implements GuardHandler {
    private final OutboxRepository outbox;

    public PaymentConfirmedHandler(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public GuardType type() {
        return GuardType.PAYMENT_CONFIRMED;
    }

    @Override
    public boolean test(Claim claim) {
        return !claim.text("paymentReference").isBlank() && outbox.completed(claim.claimId(), ActionType.TRIGGER_PAYMENT);
    }
}
