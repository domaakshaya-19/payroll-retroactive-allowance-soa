package com.example.payroll.contracts;
public record TokenRequest(
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=80) String username,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=72) String password) {}
