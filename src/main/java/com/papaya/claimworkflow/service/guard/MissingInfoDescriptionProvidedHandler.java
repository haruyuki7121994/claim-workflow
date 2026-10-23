package com.papaya.claimworkflow.service.guard;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.service.GuardHandler;
import org.springframework.stereotype.Service;

@Service
public class MissingInfoDescriptionProvidedHandler implements GuardHandler {
    @Override
    public GuardType type() {
        return GuardType.MISSING_INFO_DESCRIPTION_PROVIDED;
    }

    @Override
    public boolean test(Claim claim) {
        return !claim.text("missingInfoDescription").isBlank();
    }
}
