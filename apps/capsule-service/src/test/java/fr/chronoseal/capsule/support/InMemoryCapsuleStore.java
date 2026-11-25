package fr.chronoseal.capsule.support;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.service.CapsuleStore;

public final class InMemoryCapsuleStore implements CapsuleStore {

    private final Map<UUID, Capsule> capsules = new ConcurrentHashMap<>();

    @Override
    public Capsule save(Capsule capsule) {
        capsules.put(capsule.getId(), capsule);
        return capsule;
    }

    @Override
    public Optional<Capsule> findById(UUID id) {
        return Optional.ofNullable(capsules.get(id));
    }

    @Override
    public List<Capsule> findAllOrderedByUnlockAt() {
        return capsules.values().stream()
                .sorted(Comparator.comparing(Capsule::getUnlockAt))
                .toList();
    }
}
