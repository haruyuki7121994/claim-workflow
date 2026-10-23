package com.papaya.claimworkflow.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "effect_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class EffectReceipt {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(nullable = false) private String result;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
