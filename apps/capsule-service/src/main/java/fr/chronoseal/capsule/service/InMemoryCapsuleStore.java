package fr.chronoseal.capsule.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import fr.chronoseal.capsule.domain.Capsule;

/**
 * Stockage volatil, propre à chaque instance du service.
 *
 * <p>Suffisant pour une première version à une seule réplique : les capsules
 * disparaissent au redémarrage du pod.</p>
 */
@Repository
public class InMemoryCapsuleStore implements CapsuleStore {

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
