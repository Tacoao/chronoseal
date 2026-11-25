package fr.chronoseal.capsule.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import fr.chronoseal.capsule.domain.Capsule;

public interface CapsuleStore {

    Capsule save(Capsule capsule);

    Optional<Capsule> findById(UUID id);

    List<Capsule> findAllOrderedByUnlockAt();
}
