package com.arafawood.api.auth;

import com.arafawood.api.auth.AuthDtos.LoginRequest;
import com.arafawood.api.auth.AuthDtos.MeResponse;
import com.arafawood.api.auth.AuthDtos.PermissionDto;
import com.arafawood.api.common.ApiException;
import com.arafawood.api.domain.AppUser;
import com.arafawood.api.domain.LoginEvent;
import com.arafawood.api.repo.AppUserRepository;
import com.arafawood.api.repo.LoginEventRepository;
import com.arafawood.api.repo.PermissionRepository;
import com.arafawood.api.security.CurrentUserService;
import com.arafawood.api.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AppUserRepository users;
    private final PermissionRepository permissions;
    private final LoginEventRepository loginEvents;
    private final CurrentUserService currentUser;
    private final SettingService settings;

    public AuthService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AppUserRepository users,
            PermissionRepository permissions,
            LoginEventRepository loginEvents,
            CurrentUserService currentUser,
            SettingService settings) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.users = users;
        this.permissions = permissions;
        this.loginEvents = loginEvents;
        this.currentUser = currentUser;
        this.settings = settings;
    }

    @Transactional
    public MeResponse login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, http, response);

            int timeoutMinutes = resolveTimeoutMinutes();
            HttpSession session = http.getSession(true);
            session.setMaxInactiveInterval(timeoutMinutes * 60);

            AppUser user = users.findByUsername(request.username()).orElseThrow();
            user.setLastLoginAt(Instant.now());
            users.save(user);
            recordLogin(user, request.username(), true);
            return toMe(user, timeoutMinutes);
        } catch (BadCredentialsException ex) {
            recordLogin(null, request.username(), false);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "اسم المستخدم أو كلمة المرور غلط");
        }
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(HttpServletRequest request) {
        int timeoutMinutes = resolveTimeoutMinutes();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setMaxInactiveInterval(timeoutMinutes * 60);
        }
        return toMe(currentUser.requireUser(), timeoutMinutes);
    }

    private int resolveTimeoutMinutes() {
        try {
            int val = Integer.parseInt(settings.get("session_timeout_minutes", "30").trim());
            return Math.max(1, val);
        } catch (NumberFormatException ex) {
            return 30;
        }
    }

    private MeResponse toMe(AppUser user, int timeoutMinutes) {
        return new MeResponse(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getRole().getCode(),
                user.getRole().getName(),
                permissions.findByRoleId(user.getRole().getId()).stream()
                        .map(p -> new PermissionDto(p.getResource(), p.getAction()))
                        .toList(),
                timeoutMinutes);
    }

    private void recordLogin(AppUser user, String username, boolean success) {
        LoginEvent event = new LoginEvent();
        event.setUser(user);
        event.setUsername(username);
        event.setSuccess(success);
        loginEvents.save(event);
    }
}
