package com.example.waitlist.global.security;

import com.example.waitlist.user.Role;

public record AuthUser(Long id, Role role) {
}
