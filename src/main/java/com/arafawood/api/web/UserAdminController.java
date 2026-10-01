package com.arafawood.api.web;

import com.arafawood.api.dto.Dtos.LoginEventDto;
import com.arafawood.api.dto.Dtos.RoleMatrix;
import com.arafawood.api.dto.Dtos.UserDto;
import com.arafawood.api.dto.Dtos.UserWrite;
import com.arafawood.api.service.UserAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserAdminController {

    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping("/users")
    public List<UserDto> list() {
        return users.list();
    }

    @PostMapping("/users")
    public UserDto create(@RequestBody UserWrite write) {
        return users.create(write);
    }

    @PutMapping("/users/{id}")
    public UserDto update(@PathVariable Long id, @RequestBody UserWrite write) {
        return users.update(id, write);
    }

    @GetMapping("/roles")
    public List<RoleMatrix> roles() {
        return users.roles();
    }

    @GetMapping("/login-events")
    public List<LoginEventDto> loginEvents() {
        return users.loginEvents();
    }
}
