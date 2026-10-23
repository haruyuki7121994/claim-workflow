package com.papaya.claimworkflow.repository;

import com.papaya.claimworkflow.entity.EffectReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EffectReceiptJpaRepository extends JpaRepository<EffectReceipt, UUID> { }
