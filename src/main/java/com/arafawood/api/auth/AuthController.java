package com.arafawood.api.auth;

import com.arafawood.api.auth.AuthDtos.LoginRequest;
import com.arafawood.api.auth.AuthDtos.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public MeResponse login(
            @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return authService.login(request, http, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }
}
