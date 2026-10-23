package com.papaya.claimworkflow.api;

import com.papaya.claimworkflow.dto.Requests;
import com.papaya.claimworkflow.entity.AppUser;
import com.papaya.claimworkflow.enums.Role;
import com.papaya.claimworkflow.repository.AppUserRepository;
import com.papaya.claimworkflow.service.JwtTokenService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Validated
public class AuthController {
    private final AppUserRepository users;
    private final JwtTokenService jwtTokenService;

    public AuthController(AppUserRepository users, JwtTokenService jwtTokenService) {
        this.users = users;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Requests.LoginRequest request) {
        AppUser user = users.findByTenantIdAndSubjectAndEnabledTrue(request.tenantId(), request.subject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant or subject"));

        String token = jwtTokenService.issue(user);

        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", 3600,
                "tenantId", user.tenantId(),
                "subject", user.subject(),
                "displayName", user.displayName(),
                "roles", user.roles().stream().map(Role::value).sorted().toList());
    }
}
