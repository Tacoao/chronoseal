package fr.chronoseal.capsule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;
import fr.chronoseal.capsule.support.MutableClock;

class CapsuleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private MutableClock clock;
    private CapsuleService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new CapsuleService(new InMemoryCapsuleStore(), clock);
    }

    @Test
    void createsCapsuleWithNormalizedFields() {
        Capsule capsule = service.create(new NewCapsule("  Pronostic  ", "  ", "France 2 - 1", NOW.plusSeconds(3600)));

        assertThat(capsule.getId()).isNotNull();
        assertThat(capsule.getTitle()).isEqualTo("Pronostic");
        assertThat(capsule.getAuthor()).isNull();
        assertThat(capsule.getCreatedAt()).isEqualTo(NOW);
        assertThat(service.get(capsule.getId())).isSameAs(capsule);
    }

    @Test
    void truncatesUnlockDateToTheSecond() {
        Capsule capsule = service.create(new NewCapsule("t", null, "m", NOW.plusMillis(60_500)));

        assertThat(capsule.getUnlockAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void rejectsUnlockDateInThePast() {
        assertThatThrownBy(() -> service.create(new NewCapsule("t", null, "m", NOW.minusSeconds(1))))
                .isInstanceOf(InvalidUnlockDateException.class);
    }

    @Test
    void rejectsUnlockDateEqualToNow() {
        assertThatThrownBy(() -> service.create(new NewCapsule("t", null, "m", NOW)))
                .isInstanceOf(InvalidUnlockDateException.class);
    }

    @Test
    void rejectsUnlockDateBeyondHorizon() {
        Instant tooFar = NOW.plus(CapsuleService.MAX_HORIZON).plusSeconds(1);

        assertThatThrownBy(() -> service.create(new NewCapsule("t", null, "m", tooFar)))
                .isInstanceOf(InvalidUnlockDateException.class);
    }

    @Test
    void refusesToOpenBeforeUnlockDate() {
        Capsule capsule = service.create(new NewCapsule("t", null, "secret", NOW.plusSeconds(90)));
        clock.advance(Duration.ofSeconds(30));

        assertThatThrownBy(() -> service.open(capsule.getId()))
                .isInstanceOfSatisfying(CapsuleStillSealedException.class, error -> {
                    assertThat(error.getUnlockAt()).isEqualTo(NOW.plusSeconds(90));
                    assertThat(error.getRemaining()).isEqualTo(Duration.ofSeconds(60));
                });
    }

    @Test
    void opensExactlyAtUnlockDate() {
        Capsule capsule = service.create(new NewCapsule("t", null, "secret", NOW.plusSeconds(90)));
        clock.advance(Duration.ofSeconds(90));

        OpenedCapsule opened = service.open(capsule.getId());

        assertThat(opened.message()).isEqualTo("secret");
    }

    @Test
    void listsCapsulesByUnlockDate() {
        Capsule later = service.create(new NewCapsule("later", null, "m", NOW.plusSeconds(500)));
        Capsule sooner = service.create(new NewCapsule("sooner", null, "m", NOW.plusSeconds(100)));

        assertThat(service.list()).containsExactly(sooner, later);
    }

    @Test
    void failsOnUnknownCapsule() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(CapsuleNotFoundException.class);
    }
}
