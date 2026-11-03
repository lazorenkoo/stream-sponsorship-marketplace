package com.streambid.auth;

import java.util.UUID;
import java.util.List;

public record UserProfile(UUID id, String email, String displayName, List<String> roles) {
}
