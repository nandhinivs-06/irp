package com.irp.HR.Config;

import com.irp.HR.Security.JwtFilter;
import com.irp.HR.Security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()

                        // Job reads are public — "**" instead of "*" so this covers
                        // /jobs, /jobs/{id}, and any deeper path, not just one segment.
                        .requestMatchers(HttpMethod.GET, "/jobs/**").permitAll()

                        // Resume upload + reads + match generation are public for now.
                        // Using "**" everywhere below so /resumes, /resumes/{id},
                        // /resumes/{id}/matches, /resumes/{id}/match all match —
                        // the old "*" pattern only covered a single path segment,
                        // which silently excluded plain POST /resumes (upload) and
                        // any two-segment path.
                        .requestMatchers(HttpMethod.POST, "/resumes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/resumes/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/resumes/*/match").permitAll()

                        // Recruiter pipeline view — was missing entirely before,
                        // which is why it 403'd. Locked to RECRUITER since this is
                        // the HR-facing screening view; needs a JWT from a
                        // recruiter account when testing in Postman.
                        .requestMatchers(HttpMethod.GET, "/jobs/*/matches").hasRole("RECRUITER")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}