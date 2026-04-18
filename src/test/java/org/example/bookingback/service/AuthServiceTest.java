package org.example.bookingback.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.example.bookingback.dto.auth.AuthResponse;
import org.example.bookingback.dto.auth.LoginRequest;
import org.example.bookingback.dto.auth.LogoutRequest;
import org.example.bookingback.dto.auth.RefreshTokenRequest;
import org.example.bookingback.dto.auth.RegisterRequest;
import org.example.bookingback.entity.RefreshToken;
import org.example.bookingback.entity.Role;
import org.example.bookingback.entity.User;
import org.example.bookingback.entity.UserRole;
import org.example.bookingback.entity.enums.RoleName;
import org.example.bookingback.exception.ConflictException;
import org.example.bookingback.exception.UnauthorizedException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    private static final JwtProperties JWT_PROPERTIES = new JwtProperties(
            "issuer",
            900_000,
            604_800_000,
            "12345678901234567890123456789012345678901234567890"
    );

    @Test
    @DisplayName("Не регаем пользователя второй раз на тот же email")
    void shouldRejectDuplicateEmailOnRegister() {
        AuthService service = newService();
        when(userRepository.existsByEmail("user@test.local")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.register(new RegisterRequest("user@test.local", "Password123!")));
    }

    @Test
    @DisplayName("Рега проходит и токены тоже приходят")
    void shouldRegisterUserAndIssueTokens() {
        AuthService service = newService();
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
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        ArgumentCaptor<Map> claimsCaptor = ArgumentCaptor.forClass(Map.class);

        assertEquals("access", response.accessToken());
        assertEquals("refresh", response.refreshToken());
        assertEquals(900, response.expiresIn());

        verify(userRepository).save(userCaptor.capture());
        verify(userRoleRepository).save(userRoleCaptor.capture());
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        verify(jwtService).generateAccessToken(any(User.class), claimsCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("user@test.local", savedUser.getEmail());
        assertEquals("encoded", savedUser.getPassword());
        assertEquals(true, savedUser.isEnabled());

        UserRole savedLink = userRoleCaptor.getValue();
        assertEquals(savedUser, savedLink.getUser());
        assertEquals(RoleName.USER, savedLink.getRole().getName());

        RefreshToken storedRefresh = refreshTokenCaptor.getValue();
        assertEquals("refresh", storedRefresh.getToken());
        assertEquals(savedUser, storedRefresh.getUser());
        assertEquals(false, storedRefresh.isRevoked());

        @SuppressWarnings("unchecked")
        Map<String, Object> claims = claimsCaptor.getValue();
        assertEquals(savedUser.getId(), claims.get("userId"));
        assertEquals(java.util.List.of("USER"), claims.get("roles"));
    }

    @Test
    @DisplayName("Не пускаем в refresh, если токен уже отозван")
    void shouldRejectRefreshForRevokedToken() {
        AuthService service = newService();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("revoked-token");
        refreshToken.setRevoked(true);
        refreshToken.setExpiryDate(java.time.OffsetDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(refreshToken));

        assertThrows(UnauthorizedException.class, () -> service.refresh(new RefreshTokenRequest("revoked-token")));
    }

    @Test
    @DisplayName("Не пускаем в refresh, если токен вообще не найден")
    void shouldRejectRefreshWhenTokenNotFound() {
        AuthService service = newService();

        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.refresh(new RefreshTokenRequest("missing-token")));
    }

    @Test
    @DisplayName("Не пускаем в refresh, если токен протух")
    void shouldRejectRefreshForExpiredToken() {
        AuthService service = newService();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("expired-token");
        refreshToken.setRevoked(false);
        refreshToken.setExpiryDate(OffsetDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(refreshToken));

        assertThrows(UnauthorizedException.class, () -> service.refresh(new RefreshTokenRequest("expired-token")));
        verify(jwtService, never()).extractSubject(any());
    }

    @Test
    @DisplayName("Не пускаем в refresh, если пришел не refresh-token")
    void shouldRejectRefreshForInvalidTokenType() {
        AuthService service = newService();
        RefreshToken refreshToken = tokenForUser("token-not-refresh", false, OffsetDateTime.now().plusDays(1), user(100L));

        when(refreshTokenRepository.findByToken("token-not-refresh")).thenReturn(Optional.of(refreshToken));
        when(jwtService.isRefreshToken("token-not-refresh")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.refresh(new RefreshTokenRequest("token-not-refresh")));
    }

    @Test
    @DisplayName("Не пускаем в refresh, если JWT поврежден")
    void shouldRejectRefreshForBrokenJwt() {
        AuthService service = newService();
        RefreshToken refreshToken = tokenForUser("broken-jwt", false, OffsetDateTime.now().plusDays(1), user(100L));

        when(refreshTokenRepository.findByToken("broken-jwt")).thenReturn(Optional.of(refreshToken));
        when(jwtService.isRefreshToken("broken-jwt")).thenReturn(true);
        when(jwtService.extractSubject("broken-jwt")).thenThrow(new JwtException("invalid"));

        assertThrows(UnauthorizedException.class, () -> service.refresh(new RefreshTokenRequest("broken-jwt")));
    }

    @Test
    @DisplayName("Refresh отзывает старый токен и выдает новую пару")
    void shouldRevokeOldTokenAndIssueNewTokensOnRefresh() {
        AuthService service = newService();
        User user = user(500L);
        addRole(user, RoleName.USER);
        RefreshToken oldToken = tokenForUser("old-refresh", false, OffsetDateTime.now().plusDays(1), user);

        when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(oldToken));
        when(jwtService.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwtService.extractSubject("old-refresh")).thenReturn(user.getEmail());
        when(jwtService.generateAccessToken(any(User.class), any(Map.class))).thenReturn("new-access");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("new-refresh");
        when(userMapper.toAuthView(any(User.class)))
                .thenReturn(new AuthResponse.UserAuthView(user.getId(), user.getEmail(), java.util.Set.of("USER")));

        AuthResponse response = service.refresh(new RefreshTokenRequest("old-refresh"));
        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);

        assertEquals("new-access", response.accessToken());
        assertEquals("new-refresh", response.refreshToken());
        assertEquals(user.getEmail(), response.user().email());
        assertEquals(900, response.expiresIn());
        assertEquals(true, oldToken.isRevoked());
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertEquals(user, refreshTokenCaptor.getValue().getUser());
        assertEquals(false, refreshTokenCaptor.getValue().isRevoked());
    }

    @Test
    @DisplayName("Login нормализует email и аутентифицирует пользователя")
    void shouldNormalizeEmailAndLogin() {
        AuthService service = newService();
        User user = user(901L);
        addRole(user, RoleName.MANAGER);
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByEmail("manager@booking.local")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(User.class), any(Map.class))).thenReturn("access-login");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-login");
        when(userMapper.toAuthView(any(User.class)))
                .thenReturn(new AuthResponse.UserAuthView(user.getId(), user.getEmail(), java.util.Set.of("MANAGER")));

        AuthResponse response = service.login(new LoginRequest("Manager@Booking.local", "Password123!"));

        assertEquals("access-login", response.accessToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("manager@booking.local");
    }

    @Test
    @DisplayName("Login не проходит, если пользователя нет в БД")
    void shouldRejectLoginWhenUserMissingAfterAuthentication() {
        AuthService service = newService();
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByEmail("missing@booking.local")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () ->
                service.login(new LoginRequest("missing@booking.local", "Password123!"))
        );
    }

    @Test
    @DisplayName("На logout просто отзываем refresh токен")
    void shouldRevokeTokenOnLogout() {
        AuthService service = newService();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("logout-token");
        refreshToken.setRevoked(false);
        refreshToken.setExpiryDate(OffsetDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByToken("logout-token")).thenReturn(Optional.of(refreshToken));

        service.logout(new LogoutRequest("logout-token"));

        assertEquals(true, refreshToken.isRevoked());
    }

    @Test
    @DisplayName("Logout на неизвестный токен не падает")
    void shouldIgnoreUnknownTokenOnLogout() {
        AuthService service = newService();
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        service.logout(new LogoutRequest("unknown"));

        verify(refreshTokenRepository).findByToken("unknown");
    }

    @Test
    @DisplayName("register падает, если роль USER не сконфигурирована")
    void shouldFailRegisterWhenUserRoleMissing() {
        AuthService service = newService();
        when(userRepository.existsByEmail("missing-role@test.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                service.register(new RegisterRequest("missing-role@test.local", "Password123!"))
        );
    }

    private AuthService newService() {
        return new AuthService(
                userRepository,
                roleRepository,
                userRoleRepository,
                refreshTokenRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                JWT_PROPERTIES,
                userMapper
        );
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.local");
        user.setEnabled(true);
        return user;
    }

    private void addRole(User user, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        user.getUserRoles().add(userRole);
    }

    private RefreshToken tokenForUser(String tokenValue, boolean revoked, OffsetDateTime expiryDate, User user) {
        RefreshToken token = new RefreshToken();
        token.setToken(tokenValue);
        token.setRevoked(revoked);
        token.setExpiryDate(expiryDate);
        token.setUser(user);
        return token;
    }
}
