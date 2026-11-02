package com.streambid.auth;

import java.util.UUID;

public record UserProfile(UUID id, String email, String displayName) {
}
