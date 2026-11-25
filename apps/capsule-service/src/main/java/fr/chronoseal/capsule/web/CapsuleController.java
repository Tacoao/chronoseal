package fr.chronoseal.capsule.web;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.service.CapsuleService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/capsules")
public class CapsuleController {

    private final CapsuleService capsules;
    private final Clock clock;

    public CapsuleController(CapsuleService capsules, Clock clock) {
        this.capsules = capsules;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<CapsuleView> create(@Valid @RequestBody CreateCapsuleRequest request,
            UriComponentsBuilder uriBuilder) {
        Capsule capsule = capsules.create(request.toCommand());
        URI location = uriBuilder.path("/api/capsules/{id}").buildAndExpand(capsule.getId()).toUri();
        return ResponseEntity.created(location).body(CapsuleView.of(capsule, clock.instant()));
    }

    @GetMapping
    public List<CapsuleView> list() {
        Instant now = clock.instant();
        return capsules.list().stream().map(capsule -> CapsuleView.of(capsule, now)).toList();
    }

    @GetMapping("/{id}")
    public CapsuleView get(@PathVariable UUID id) {
        return CapsuleView.of(capsules.get(id), clock.instant());
    }

    @GetMapping("/{id}/content")
    public OpenedCapsuleView open(@PathVariable UUID id) {
        return OpenedCapsuleView.of(capsules.open(id));
    }
}
