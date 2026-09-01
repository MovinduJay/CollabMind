package org.collabmind.identity.auth.application;

import org.collabmind.identity.auth.domain.UserAccount;
import org.collabmind.identity.auth.infrastructure.UserAccountRepository;
import org.collabmind.identity.auth.web.AuthResponse;
import org.collabmind.identity.auth.web.LoginRequest;
import org.collabmind.identity.auth.web.RegisterRequest;
import org.collabmind.identity.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.collabmind.identity.common.exception.EmailAlreadyRegisteredException;
import org.collabmind.identity.common.exception.InvalidCredentialsException;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase();

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        UserAccount user = new UserAccount(
                request.displayName(),
                normalizedEmail,
                passwordHash
        );

        UserAccount savedUser = userAccountRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        return new AuthResponse(
                savedUser.getId(),
                savedUser.getDisplayName(),
                savedUser.getEmail(),
                token
        );
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().toLowerCase();

        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                token
        );
    }
}
