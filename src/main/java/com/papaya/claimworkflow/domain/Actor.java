package com.papaya.claimworkflow.domain;

import com.papaya.claimworkflow.enums.Role;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.jwt.Jwt;

public record Actor(String id, String tenantId, Set<Role> roles) {
    public Actor {
        roles = Set.copyOf(roles);
    }

    public static Actor from(Jwt jwt) {
        var claims = jwt.getClaimAsStringList("roles");
        return new Actor(jwt.getSubject(), jwt.getClaimAsString("tenant_id"),
                claims == null ? Set.of() : claims.stream().map(Role::find).flatMap(java.util.Optional::stream)
                        .collect(Collectors.toUnmodifiableSet()));
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
