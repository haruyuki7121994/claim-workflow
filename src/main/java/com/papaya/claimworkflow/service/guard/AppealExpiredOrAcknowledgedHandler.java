package com.papaya.claimworkflow.service.guard;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.GuardType;
import com.papaya.claimworkflow.service.GuardHandler;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AppealExpiredOrAcknowledgedHandler implements GuardHandler {
    private final Clock clock;

    public AppealExpiredOrAcknowledgedHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public GuardType type() {
        return GuardType.APPEAL_EXPIRED_OR_ACKNOWLEDGED;
    }

    @Override
    public boolean test(Claim claim) {
        return claim.flag("memberAcknowledged")
                || (!claim.text("appealDeadline").isBlank()
                && !Instant.parse(claim.text("appealDeadline")).isAfter(clock.instant()));
    }
}
