package com.streambid.user;

import java.util.UUID;

public record UserAccount(UUID id, String email, String passwordHash, String displayName) {
}
