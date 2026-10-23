package com.papaya.claimworkflow.service.guard;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.service.GuardHandler;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class AmountWithinPolicyLimitHandler implements GuardHandler {
    @Override
    public GuardType type() {
        return GuardType.AMOUNT_WITHIN_POLICY_LIMIT;
    }

    @Override
    public boolean test(Claim claim) {
        return new BigDecimal(claim.facts().get("amount").toString())
                .compareTo(new BigDecimal(claim.facts().get("policyLimit").toString())) <= 0;
    }
}
