package fr.chronoseal.capsule.service;

import fr.chronoseal.capsule.domain.Capsule;

public record OpenedCapsule(Capsule capsule, String message, String salt) {
}
