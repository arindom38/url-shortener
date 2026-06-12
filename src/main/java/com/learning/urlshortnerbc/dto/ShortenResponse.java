package com.learning.urlshortnerbc.dto;

public record ShortenResponse(
        String shortCode,
        String shortUrl,
        String longUrl
) {}
