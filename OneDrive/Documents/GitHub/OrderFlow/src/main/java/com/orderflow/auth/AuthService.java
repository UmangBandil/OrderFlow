package com.orderflow.auth;

import com.orderflow.auth.AuthDtos.AuthResponse;
import com.orderflow.auth.AuthDtos.LoginRequest;
import com.orderflow.auth.AuthDtos.RefreshRequest;
import com.orderflow.auth.AuthDtos.RegisterRequest;
import com.orderflow.auth.jwt.JwtService;
import com.orderflow.auth.token.RefreshTokenStore;
import com.orderflow.common.error.ApiException;
import com.orderflow.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(ErrorCode.CONFLICT, "Email already registered");
        }
        User user = new User(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                Role.CUSTOMER);
        userRepository.save(user);

        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenStore
                .consume(request.refreshToken())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token"));
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "User no longer exists"));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getEmail(), user.getRole().name());
        refreshTokenStore.store(user.getId(), refreshToken);
        return new AuthResponse(
                accessToken, refreshToken, user.getEmail(), user.getRole().name());
    }
}
