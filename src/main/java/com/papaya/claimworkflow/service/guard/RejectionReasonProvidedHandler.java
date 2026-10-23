package com.papaya.claimworkflow.service.guard;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.service.GuardHandler;
import org.springframework.stereotype.Service;

@Service
public class RejectionReasonProvidedHandler implements GuardHandler {
    @Override
    public GuardType type() {
        return GuardType.REJECTION_REASON_PROVIDED;
    }

    @Override
    public boolean test(Claim claim) {
        return !claim.text("rejectionReason").isBlank();
    }
}
