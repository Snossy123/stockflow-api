package com.arafawood.api.security;

import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.repo.AppUserRepository;
import com.arafawood.api.repo.PermissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final AppUserRepository users;
    private final PermissionRepository permissions;

    public CurrentUserService(AppUserRepository users, PermissionRepository permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public AppUser requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "سجّل الدخول أولًا");
        }
        return users.findByUsername(auth.getName()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "المستخدم غير موجود"));
    }

    @Transactional(readOnly = true)
    public AppUser require(String resource, String action) {
        AppUser user = requireUser();
        if (!permissions.existsByRoleIdAndResourceAndAction(user.getRole().getId(), resource, action)) {
            throw ApiException.forbidden();
        }
        return user;
    }

    @Transactional(readOnly = true)
    public boolean can(String resource, String action) {
        AppUser user = requireUser();
        return permissions.existsByRoleIdAndResourceAndAction(user.getRole().getId(), resource, action);
    }
}
