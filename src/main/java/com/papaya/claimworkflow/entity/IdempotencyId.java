package com.papaya.claimworkflow.entity;

import java.io.Serializable;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class IdempotencyId implements Serializable {
    private String tenantId;
    private String actorId;
    private String idempotencyKey;
}
