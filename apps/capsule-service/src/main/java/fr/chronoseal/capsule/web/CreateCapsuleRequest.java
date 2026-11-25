package fr.chronoseal.capsule.web;

import java.time.Instant;

import fr.chronoseal.capsule.service.NewCapsule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCapsuleRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 60) String author,
        @NotBlank @Size(max = 10_000) String message,
        @NotNull Instant unlockAt) {

    NewCapsule toCommand() {
        return new NewCapsule(title, author, message, unlockAt);
    }
}
