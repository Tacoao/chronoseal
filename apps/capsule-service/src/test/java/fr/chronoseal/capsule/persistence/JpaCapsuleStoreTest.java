package fr.chronoseal.capsule.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import fr.chronoseal.capsule.domain.Capsule;

@DataJpaTest
@Import(JpaCapsuleStore.class)
class JpaCapsuleStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Autowired
    private JpaCapsuleStore store;

    @Autowired
    private TestEntityManager entityManager;

    private static Capsule capsule(String title, Instant unlockAt) {
        byte[] ciphertext = new byte[Capsule.MAX_CIPHERTEXT_LENGTH];
        ciphertext[0] = 42;
        return new Capsule(UUID.randomUUID(), title, "ali", NOW, unlockAt, "k1",
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, ciphertext, "ab".repeat(32));
    }

    @Test
    void persistsAndReloadsSealedContent() {
        Capsule saved = store.save(capsule("Pronostic", NOW.plusSeconds(60)));
        entityManager.flush();
        entityManager.clear();

        Capsule reloaded = store.findById(saved.getId()).orElseThrow();

        assertThat(reloaded).isNotSameAs(saved);
        assertThat(reloaded.getTitle()).isEqualTo("Pronostic");
        assertThat(reloaded.getUnlockAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(reloaded.getNonce()).isEqualTo(saved.getNonce());
        assertThat(reloaded.getCiphertext()).hasSize(Capsule.MAX_CIPHERTEXT_LENGTH).startsWith(42);
        assertThat(reloaded.getCommitment()).isEqualTo(saved.getCommitment());
        assertThat(reloaded.isNew()).isFalse();
    }

    @Test
    void listsCapsulesByUnlockDate() {
        Capsule later = store.save(capsule("later", NOW.plusSeconds(500)));
        Capsule sooner = store.save(capsule("sooner", NOW.plusSeconds(100)));
        entityManager.flush();

        assertThat(store.findAllOrderedByUnlockAt())
                .extracting(Capsule::getId)
                .containsExactly(sooner.getId(), later.getId());
    }

    @Test
    void returnsEmptyForUnknownIdentifier() {
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }
}
