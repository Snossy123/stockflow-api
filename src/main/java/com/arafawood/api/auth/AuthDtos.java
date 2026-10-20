package com.arafawood.api.auth;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(String username, String password, boolean rememberMe) {}

    public record PermissionDto(String resource, String action) {}

    public record MeResponse(
            Long id,
            String name,
            String username,
            String roleCode,
            String roleName,
            List<PermissionDto> permissions,
            int sessionTimeoutMinutes) {}
}
