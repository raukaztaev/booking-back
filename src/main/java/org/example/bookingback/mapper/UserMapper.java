package org.example.bookingback.mapper;

import java.util.Set;
import java.util.stream.Collectors;
import org.example.bookingback.dto.auth.AuthResponse;
import org.example.bookingback.dto.user.UserResponse;
import org.example.bookingback.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.isEnabled(),
                extractRoles(user),
                user.getCreatedAt()
        );
    }

    public AuthResponse.UserAuthView toAuthView(User user) {
        return new AuthResponse.UserAuthView(user.getId(), user.getEmail(), extractRoles(user));
    }

    private Set<String> extractRoles(User user) {
        return user.getUserRoles().stream()
                .map(userRole -> userRole.getRole().getName().name())
                .collect(Collectors.toSet());
    }
}
