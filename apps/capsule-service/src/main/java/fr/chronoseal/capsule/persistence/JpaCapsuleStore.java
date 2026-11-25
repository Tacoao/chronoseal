package fr.chronoseal.capsule.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.service.CapsuleStore;

@Component
public class JpaCapsuleStore implements CapsuleStore {

    private final CapsuleJpaRepository repository;

    JpaCapsuleStore(CapsuleJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Capsule save(Capsule capsule) {
        return repository.save(capsule);
    }

    @Override
    public Optional<Capsule> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Capsule> findAllOrderedByUnlockAt() {
        return repository.findAllByOrderByUnlockAtAsc();
    }
}
