package com.learning.urlshortnerbc.controller;

import com.learning.urlshortnerbc.exception.ShortCodeNotFoundException;
import com.learning.urlshortnerbc.service.UrlResolverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UrlResolverService urlResolverService;

    @Test
    void redirect_knownCode_returns302WithLocationHeader() throws Exception {
        when(urlResolverService.resolve("abc123")).thenReturn("https://example.com/original");

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/original"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        when(urlResolverService.resolve("missing"))
                .thenThrow(new ShortCodeNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());
    }
}
