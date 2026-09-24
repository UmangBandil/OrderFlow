package com.orderflow.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Users", description = "User management (ADMIN only)")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all users (paginated)")
    public Page<UserService.UserDto> list(@PageableDefault(size = 20) Pageable pageable) {
        return userService.listUsers(pageable);
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "Change a user's role")
    public ResponseEntity<UserService.UserDto> changeRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        return ResponseEntity.ok(userService.changeRole(id, Role.valueOf(request.role())));
    }

    public record RoleRequest(@NotBlank String role) {}
}
