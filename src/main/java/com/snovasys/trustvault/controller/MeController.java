package com.snovasys.trustvault.controller;

import com.snovasys.trustvault.dto.ApiDtos.LoginResponse;
import com.snovasys.trustvault.model.AppUser;
import com.snovasys.trustvault.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {
    private final CurrentUserService currentUser;
    public MeController(CurrentUserService currentUser) { this.currentUser=currentUser; }
    @GetMapping("/me")
    public LoginResponse me() {
        AppUser user = currentUser.requireUser();
        return new LoginResponse(user.getEmail(), user.getDisplayName(), user.getRole());
    }
}
