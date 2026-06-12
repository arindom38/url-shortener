package com.learning.urlshortnerbc.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record ShortenRequest(
        @NotBlank @URL String longUrl
) {}
