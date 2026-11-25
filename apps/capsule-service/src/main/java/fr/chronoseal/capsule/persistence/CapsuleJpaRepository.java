package fr.chronoseal.capsule.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import fr.chronoseal.capsule.domain.Capsule;

interface CapsuleJpaRepository extends JpaRepository<Capsule, UUID> {

    List<Capsule> findAllByOrderByUnlockAtAsc();
}
