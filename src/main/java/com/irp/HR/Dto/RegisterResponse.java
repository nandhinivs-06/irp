package com.irp.HR.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
public class RegisterResponse {
    private UUID userId;
    private String name;
    private String email;
    private String role;
    private String status;
}
