package fr.chronoseal.capsule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;
import fr.chronoseal.capsule.support.FakeSealClient;
import fr.chronoseal.capsule.support.MutableClock;

class CapsuleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private MutableClock clock;
    private FakeSealClient sealClient;
    private CapsuleService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        sealClient = new FakeSealClient();
        service = new CapsuleService(new InMemoryCapsuleStore(), sealClient, clock);
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
    void storesOnlySealedContent() {
        Capsule capsule = service.create(new NewCapsule("t", null, "France 2 - 1", NOW.plusSeconds(3600)));

        assertThat(new String(capsule.getCiphertext(), StandardCharsets.UTF_8)).doesNotContain("France");
        assertThat(capsule.getKeyId()).isEqualTo("fake-key");
        assertThat(capsule.getCommitment()).isEqualTo("commitment-of-" + capsule.getId());
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
    void refusesToOpenBeforeUnlockDateWithoutCallingSealService() {
        Capsule capsule = service.create(new NewCapsule("t", null, "secret", NOW.plusSeconds(90)));
        clock.advance(Duration.ofSeconds(30));

        assertThatThrownBy(() -> service.open(capsule.getId()))
                .isInstanceOfSatisfying(CapsuleStillSealedException.class, error -> {
                    assertThat(error.getUnlockAt()).isEqualTo(NOW.plusSeconds(90));
                    assertThat(error.getRemaining()).isEqualTo(Duration.ofSeconds(60));
                });
        assertThat(sealClient.unsealCalls()).isZero();
    }

    @Test
    void opensExactlyAtUnlockDateAndRevealsSalt() {
        Capsule capsule = service.create(new NewCapsule("t", null, "secret", NOW.plusSeconds(90)));
        clock.advance(Duration.ofSeconds(90));

        OpenedCapsule opened = service.open(capsule.getId());

        assertThat(opened.message()).isEqualTo("secret");
        assertThat(opened.salt()).isEqualTo("01020304");
    }

    @Test
    void sealServiceClockPrevailsWhenItRefusesToOpen() {
        Capsule capsule = service.create(new NewCapsule("t", null, "secret", NOW.plusSeconds(90)));
        clock.advance(Duration.ofSeconds(91));
        sealClient.refuseUnseal();

        assertThatThrownBy(() -> service.open(capsule.getId()))
                .isInstanceOfSatisfying(CapsuleStillSealedException.class,
                        error -> assertThat(error.getRemaining()).isEqualTo(Duration.ZERO));
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
