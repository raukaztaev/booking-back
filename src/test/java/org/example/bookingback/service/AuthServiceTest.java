package org.example.bookingback.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.bookingback.dto.auth.AuthResponse;
import org.example.bookingback.dto.auth.RegisterRequest;
import org.example.bookingback.entity.Role;
import org.example.bookingback.entity.User;
import org.example.bookingback.entity.UserRole;
import org.example.bookingback.entity.enums.RoleName;
import org.example.bookingback.exception.ConflictException;
import org.example.bookingback.mapper.UserMapper;
import org.example.bookingback.repository.RefreshTokenRepository;
import org.example.bookingback.repository.RoleRepository;
import org.example.bookingback.repository.UserRepository;
import org.example.bookingback.repository.UserRoleRepository;
import org.example.bookingback.security.JwtProperties;
import org.example.bookingback.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Не регаем пользователя второй раз на тот же email")
    void shouldRejectDuplicateEmailOnRegister() {
        AuthService service = new AuthService(
                userRepository,
                roleRepository,
                userRoleRepository,
                refreshTokenRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                new JwtProperties("issuer", 900_000, 604_800_000, "12345678901234567890123456789012345678901234567890"),
                userMapper
        );
        when(userRepository.existsByEmail("user@test.local")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.register(new RegisterRequest("user@test.local", "Password123!")));
    }

    @Test
    @DisplayName("Рега проходит и токены тоже приходят")
    void shouldRegisterUserAndIssueTokens() {
        AuthService service = new AuthService(
                userRepository,
                roleRepository,
                userRoleRepository,
                refreshTokenRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                new JwtProperties("issuer", 900_000, 604_800_000, "12345678901234567890123456789012345678901234567890"),
                userMapper
        );
        Role role = new Role();
        role.setId(3L);
        role.setName(RoleName.USER);

        when(userRepository.existsByEmail("user@test.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return user;
        });
        when(userRoleRepository.save(any(UserRole.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class), any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh");
        when(userMapper.toAuthView(any(User.class))).thenReturn(new AuthResponse.UserAuthView(100L, "user@test.local", java.util.Set.of("USER")));

        AuthResponse response = service.register(new RegisterRequest("user@test.local", "Password123!"));

        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        verify(refreshTokenRepository).save(any());
    }
}
