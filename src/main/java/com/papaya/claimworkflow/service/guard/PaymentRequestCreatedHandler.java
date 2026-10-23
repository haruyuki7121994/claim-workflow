package com.papaya.claimworkflow.service.guard;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.ActionType;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.repository.OutboxRepository;
import com.papaya.claimworkflow.service.GuardHandler;
import org.springframework.stereotype.Service;

@Service
public class PaymentRequestCreatedHandler implements GuardHandler {
    private final OutboxRepository outbox;

    public PaymentRequestCreatedHandler(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    public GuardType type() {
        return GuardType.PAYMENT_REQUEST_CREATED;
    }

    @Override
    public boolean test(Claim claim) {
        return outbox.completed(claim.claimId(), ActionType.CREATE_PAYMENT_REQUEST);
    }
}
