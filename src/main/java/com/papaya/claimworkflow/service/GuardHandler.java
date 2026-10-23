package com.papaya.claimworkflow.service;

import com.papaya.claimworkflow.entity.Claim;
import com.papaya.claimworkflow.enums.GuardType;

public interface GuardHandler extends java.util.function.Predicate<Claim> {
    GuardType type();
    default void init() { }
}
