package com.irp.HR.Service;

import com.irp.HR.Dto.LoginRequest;
import com.irp.HR.Dto.LoginResponse;
import com.irp.HR.Dto.RegisterRequest;
import com.irp.HR.Dto.RegisterResponse;
import com.irp.HR.Model.Role;
import com.irp.HR.Model.User;
import com.irp.HR.Model.UserStatus;
import com.irp.HR.Repository.RoleRepository;
import com.irp.HR.Repository.UserRepository;
import com.irp.HR.Security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public RegisterResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String roleName = request.getRoleName().trim().toUpperCase();

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }

        Role role = roleRepository.findByRoleName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setRoleName(roleName);
                    return roleRepository.save(newRole);
                });

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .name(savedUser.getName())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().getRoleName())
                .status(savedUser.getStatus().name())
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("Account is " + user.getStatus());
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().getRoleName()
        );

        return new LoginResponse(
                token,
                user.getId(),
                user.getName(),
                user.getRole().getRoleName()
        );
    }
}
