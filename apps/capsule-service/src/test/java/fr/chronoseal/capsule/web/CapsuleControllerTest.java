package fr.chronoseal.capsule.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;
import fr.chronoseal.capsule.seal.SealIntegrityException;
import fr.chronoseal.capsule.seal.SealServiceException;
import fr.chronoseal.capsule.seal.SealServiceUnavailableException;
import fr.chronoseal.capsule.service.CapsuleService;
import fr.chronoseal.capsule.service.OpenedCapsule;

@WebMvcTest(CapsuleController.class)
@Import(CapsuleControllerTest.FixedClockConfiguration.class)
class CapsuleControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final UUID ID = UUID.fromString("7f6c1d1e-5b0a-4c55-9a43-0c7c2f0e9a11");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CapsuleService service;

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private static Capsule capsule(Instant unlockAt) {
        return new Capsule(ID, "Pronostic", "ali", NOW, unlockAt, "k1", new byte[12], new byte[] {1, 2, 3}, "c0ffee");
    }

    @Test
    void createsCapsuleAndReturnsLocation() throws Exception {
        given(service.create(any())).willReturn(capsule(NOW.plusSeconds(3600)));

        mvc.perform(post("/api/capsules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Pronostic", "author": "ali", "message": "France 2 - 1",
                                 "unlockAt": "2026-11-25T11:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/capsules/" + ID)))
                .andExpect(jsonPath("$.sealed").value(true))
                .andExpect(jsonPath("$.secondsRemaining").value(3600))
                .andExpect(jsonPath("$.commitment").value("c0ffee"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.ciphertext").doesNotExist());
    }

    @Test
    void rejectsInvalidPayloadWithProblemDetail() throws Exception {
        mvc.perform(post("/api/capsules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "message": "x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")));
    }

    @Test
    void reportsBusinessRuleViolation() throws Exception {
        given(service.create(any())).willThrow(new InvalidUnlockDateException("La date d'ouverture doit être dans le futur."));

        mvc.perform(post("/api/capsules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "t", "message": "m", "unlockAt": "2020-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Date d'ouverture invalide"));
    }

    @Test
    void listsCapsulesWithoutContent() throws Exception {
        given(service.list()).willReturn(List.of(capsule(NOW.minusSeconds(10))));

        mvc.perform(get("/api/capsules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].sealed").value(false))
                .andExpect(jsonPath("$[0].message").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnknownCapsule() throws Exception {
        given(service.get(ID)).willThrow(new CapsuleNotFoundException(ID));

        mvc.perform(get("/api/capsules/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Capsule introuvable"));
    }

    @Test
    void returnsBadRequestForMalformedIdentifier() throws Exception {
        mvc.perform(get("/api/capsules/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsLockedWhileSealed() throws Exception {
        Instant unlockAt = NOW.plusSeconds(120);
        given(service.open(ID)).willThrow(new CapsuleStillSealedException(ID, unlockAt, Duration.ofSeconds(120)));

        mvc.perform(get("/api/capsules/" + ID + "/content"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.secondsRemaining").value(120))
                .andExpect(jsonPath("$.unlockAt").value("2026-11-25T10:02:00Z"));
    }

    @Test
    void returnsContentOnceOpened() throws Exception {
        given(service.open(ID)).willReturn(new OpenedCapsule(capsule(NOW.minusSeconds(1)), "France 2 - 1", "0102"));

        mvc.perform(get("/api/capsules/" + ID + "/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("France 2 - 1"))
                .andExpect(jsonPath("$.commitment").value("c0ffee"))
                .andExpect(jsonPath("$.salt").value("0102"));
    }

    @Test
    void returnsServiceUnavailableWhenSealServiceIsDown() throws Exception {
        given(service.open(ID)).willThrow(new SealServiceUnavailableException("indisponible", null));

        mvc.perform(get("/api/capsules/" + ID + "/content"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Scellement indisponible"));
    }

    @Test
    void returnsServerErrorWhenIntegrityIsCompromised() throws Exception {
        given(service.open(ID)).willThrow(new SealIntegrityException("altéré", null));

        mvc.perform(get("/api/capsules/" + ID + "/content"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Intégrité de la capsule compromise"));
    }

    @Test
    void returnsBadGatewayOnUnexpectedSealError() throws Exception {
        given(service.create(any())).willThrow(new SealServiceException("erreur", null));

        mvc.perform(post("/api/capsules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "t", "message": "m", "unlockAt": "2026-11-25T11:00:00Z"}
                                """))
                .andExpect(status().isBadGateway());
    }
}
