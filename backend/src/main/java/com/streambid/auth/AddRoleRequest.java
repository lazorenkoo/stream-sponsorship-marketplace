package com.streambid.auth;

import com.streambid.user.UserRole;
import jakarta.validation.constraints.NotNull;

public record AddRoleRequest(@NotNull UserRole role) {
}
