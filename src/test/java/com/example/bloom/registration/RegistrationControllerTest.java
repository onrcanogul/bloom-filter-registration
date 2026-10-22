package com.example.bloom.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.seed.user-count=500", "app.db.simulated-latency-micros=0"})
@AutoConfigureMockMvc
class RegistrationControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registersValidUserThenRejectsDuplicate() throws Exception {
        String body = """
                {"username":"controller_user","email":"cu@example.com","password":"password123"}
                """;

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("controller_user"))
                .andExpect(jsonPath("$.availabilityCheck.bloomVerdict").value("DEFINITELY_ABSENT"));

        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void rejectsInvalidForm() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a b","email":"not-an-email","password":"123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void reportsAvailabilityAndStats() throws Exception {
        mvc.perform(get("/api/users/availability").param("username", "zzz_unused_name").param("useBloom", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.bloomVerdict").value("NOT_USED"))
                .andExpect(jsonPath("$.databaseQueried").value(true));

        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloomFilter.sizeInBytes").isNumber());
    }
}
