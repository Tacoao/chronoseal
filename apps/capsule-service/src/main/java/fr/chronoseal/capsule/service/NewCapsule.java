package fr.chronoseal.capsule.service;

import java.time.Instant;

public record NewCapsule(String title, String author, String message, Instant unlockAt) {
}
