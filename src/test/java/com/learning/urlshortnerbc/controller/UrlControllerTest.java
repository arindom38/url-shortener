package com.learning.urlshortnerbc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.urlshortnerbc.dto.ShortenRequest;
import com.learning.urlshortnerbc.dto.ShortenResponse;
import com.learning.urlshortnerbc.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UrlShortenerService urlShortenerService;

    @Test
    void shorten_validRequest_returns201WithBody() throws Exception {
        when(urlShortenerService.shorten(any())).thenReturn(
                new ShortenResponse("aB3kLp", "http://localhost:8080/aB3kLp", "https://example.com"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShortenRequest("https://example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("aB3kLp"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/aB3kLp"))
                .andExpect(jsonPath("$.longUrl").value("https://example.com"));
    }

    @Test
    void shorten_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_invalidUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"not-a-valid-url\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
