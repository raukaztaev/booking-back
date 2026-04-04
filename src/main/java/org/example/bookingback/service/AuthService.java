package org.example.bookingback.service;

import io.jsonwebtoken.JwtException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userMapper = userMapper;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Role USER is not configured"));

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user = userRepository.save(user);

        UserRole link = new UserRole();
        link.setUser(user);
        link.setRole(userRole);
        userRoleRepository.save(link);
        user.getUserRoles().add(link);

        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));
        if (storedToken.isRevoked() || storedToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }
        try {
            if (!jwtService.isRefreshToken(storedToken.getToken())) {
                throw new UnauthorizedException("Invalid token type");
            }
            jwtService.extractSubject(storedToken.getToken());
        } catch (JwtException ex) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        storedToken.setRevoked(true);
        User user = storedToken.getUser();
        return issueTokens(user);
    }

    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(token -> token.setRevoked(true));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user, Map.of(
                "roles", user.getUserRoles().stream().map(userRole -> userRole.getRole().getName().name()).toList(),
                "userId", user.getId()
        ));
        String refreshTokenValue = jwtService.generateRefreshToken(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setExpiryDate(OffsetDateTime.now(ZoneOffset.UTC).plusNanos(jwtProperties.refreshTokenExpiration() * 1_000_000));
        refreshToken.setRevoked(false);
        refreshToken.setUser(user);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                "Bearer",
                jwtProperties.accessTokenExpiration() / 1000,
                userMapper.toAuthView(user)
        );
    }
}
