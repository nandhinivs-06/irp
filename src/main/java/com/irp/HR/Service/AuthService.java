package com.irp.HR.Service;

import com.irp.HR.Dto.LoginRequest;
import com.irp.HR.Dto.LoginResponse;
import com.irp.HR.Model.User;
import com.irp.HR.Repository.UserRepository;
import com.irp.HR.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {

        // Step 1 — Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        // Step 2 — Check password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        // Step 3 — Check user status
        if (!user.getStatus().equals("ACTIVE")) {
            throw new RuntimeException("Account is " + user.getStatus());
        }

        // Step 4 — Generate JWT token
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getRoleName()
        );

        // Step 5 — Return response
        return new LoginResponse(
                token,
                user.getId(),
                user.getName(),
                user.getRole().getRoleName()
        );
    }
}