package com.snovasys.trustvault.controller;

import com.snovasys.trustvault.dto.ApiDtos.*;
import com.snovasys.trustvault.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    public AdminController(AdminService admin) { this.admin=admin; }
    @GetMapping("/overview") public OverviewResponse overview() { return admin.overview(); }
    @GetMapping("/users") public List<UserResponse> users() { return admin.users(); }
    @PostMapping("/users") public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) { return admin.createUser(request); }
    @PatchMapping("/users/{id}/status") public UserResponse status(@PathVariable long id, @Valid @RequestBody StatusRequest request) { return admin.setEnabled(id, request.enabled()); }
    @GetMapping("/audit") public List<AuditResponse> audit(@RequestParam(defaultValue = "100") int limit) { return admin.auditEvents(limit); }
    @GetMapping("/audit/verify") public AuditVerifyResponse verify() { return admin.verifyAudit(); }
}
