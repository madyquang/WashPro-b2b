package com.washpro.washpro_b2b.controller;

import com.washpro.washpro_b2b.dto.UserDtos.CreateRequest;
import com.washpro.washpro_b2b.dto.UserDtos.EnabledRequest;
import com.washpro.washpro_b2b.dto.UserDtos.ResetPasswordRequest;
import com.washpro.washpro_b2b.dto.UserDtos.Response;
import com.washpro.washpro_b2b.entity.Role;
import com.washpro.washpro_b2b.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('WORKSHOP_ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Response>> list(@RequestParam(required = false) Role role) {
        return ResponseEntity.ok(userService.list(role));
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Response> setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return ResponseEntity.ok(userService.setEnabled(id, request.enabled()));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
