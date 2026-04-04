package org.example.bookingback.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Создаем бронь, ловим пересечение и потом подтверждаем")
    void shouldCompleteBookingFlowAndRejectOverlap() throws Exception {
        String managerToken = login("manager@booking.local", "Password123!");
        String userToken = registerAndGetToken("flow-user@test.local");

        Long resourceId = createResource(managerToken, "Board Room");
        String firstRequest = """
                {
                  "resourceId": %d,
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(resourceId, OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(1).plusHours(2));

        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long bookingId = objectMapper.readTree(bookingJson).get("id").asLong();

        mockMvc.perform(post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/bookings/{id}/status", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Чужую бронь по id не отдаем")
    void shouldReturnNotFoundForForeignBookingAccess() throws Exception {
        String managerToken = login("manager@booking.local", "Password123!");
        String firstUserToken = registerAndGetToken("owner-user@test.local");
        String secondUserToken = registerAndGetToken("foreign-user@test.local");

        Long resourceId = createResource(managerToken, "Focus Room");
        String createBookingRequest = """
                {
                  "resourceId": %d,
                  "startTime": "%s",
                  "endTime": "%s"
                }
                """.formatted(resourceId, OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusDays(2).plusHours(1));

        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBookingRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long bookingId = objectMapper.readTree(bookingJson).get("id").asLong();

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondUserToken)))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private Long createResource(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/resources")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Main room","capacity":10,"restricted":false}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
