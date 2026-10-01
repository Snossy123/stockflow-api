package com.arafawood.api.service;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.Role;
import com.arafawood.api.dto.Dtos.AuthPerm;
import com.arafawood.api.dto.Dtos.LoginEventDto;
import com.arafawood.api.dto.Dtos.RoleMatrix;
import com.arafawood.api.dto.Dtos.UserDto;
import com.arafawood.api.dto.Dtos.UserWrite;
import com.arafawood.api.repo.AppUserRepository;
import com.arafawood.api.repo.LoginEventRepository;
import com.arafawood.api.repo.PermissionRepository;
import com.arafawood.api.repo.RoleRepository;
import com.arafawood.api.security.CurrentUserService;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final LoginEventRepository loginEvents;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public UserAdminService(
            AppUserRepository users,
            RoleRepository roles,
            PermissionRepository permissions,
            LoginEventRepository loginEvents,
            PasswordEncoder encoder,
            AuditService audit,
            CurrentUserService currentUser) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.loginEvents = loginEvents;
        this.encoder = encoder;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        currentUser.require("users", "view");
        return users.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public UserDto create(UserWrite write) {
        AppUser actor = currentUser.require("users", "create");
        if (users.existsByUsername(write.username())) {
            throw ApiException.badRequest("اسم المستخدم مستخدم قبل كده");
        }
        Role role = roles.findById(write.roleId()).orElseThrow(() -> ApiException.badRequest("الدور غير موجود"));
        AppUser user = new AppUser();
        user.setName(write.name());
        user.setUsername(write.username());
        user.setPasswordHash(encoder.encode(write.password() == null || write.password().isBlank() ? "arafa123" : write.password()));
        user.setPhone(write.phone());
        user.setRole(role);
        user.setStatus(write.status() == null ? "active" : write.status());
        users.save(user);
        audit.record(actor, "create", "user", user.getId(), null, user.getUsername());
        return toDto(user);
    }

    @Transactional
    public UserDto update(Long id, UserWrite write) {
        AppUser actor = currentUser.require("users", "create");
        AppUser user = users.findById(id).orElseThrow(() -> ApiException.notFound("المستخدم غير موجود"));
        Role role = roles.findById(write.roleId()).orElseThrow(() -> ApiException.badRequest("الدور غير موجود"));
        user.setName(write.name());
        user.setPhone(write.phone());
        user.setRole(role);
        if (write.status() != null) {
            user.setStatus(write.status());
        }
        if (write.password() != null && !write.password().isBlank()) {
            user.setPasswordHash(encoder.encode(write.password()));
            audit.record(actor, "reset-password", "user", user.getId(), null, user.getUsername());
        }
        return toDto(users.save(user));
    }

    @Transactional(readOnly = true)
    public List<RoleMatrix> roles() {
        currentUser.require("users", "view");
        return roles.findAll().stream()
                .map(role -> new RoleMatrix(
                        role.getId(),
                        role.getCode(),
                        role.getName(),
                        permissions.findByRoleId(role.getId()).stream()
                                .map(p -> new AuthPerm(p.getResource(), p.getAction()))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LoginEventDto> loginEvents() {
        currentUser.require("users", "view");
        return loginEvents.findAllByOrderByOccurredAtDesc().stream()
                .map(e -> new LoginEventDto(
                        e.getId(),
                        e.getUsername(),
                        e.getUser() == null ? null : e.getUser().getName(),
                        e.isSuccess(),
                        e.getOccurredAt()))
                .toList();
    }

    private UserDto toDto(AppUser user) {
        return new UserDto(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getPhone(),
                user.getStatus(),
                user.getRole().getId(),
                user.getRole().getCode(),
                user.getRole().getName(),
                user.getLastLoginAt());
    }
}
