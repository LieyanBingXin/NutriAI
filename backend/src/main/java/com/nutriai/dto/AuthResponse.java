package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private Long userId;
    private String username;
    private String email;
    private String displayName;
    private String message;
}