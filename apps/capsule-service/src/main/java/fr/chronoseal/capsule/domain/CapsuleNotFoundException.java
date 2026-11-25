package fr.chronoseal.capsule.domain;

import java.util.UUID;

public class CapsuleNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CapsuleNotFoundException(UUID id) {
        super("Aucune capsule ne porte l'identifiant " + id + ".");
    }
}
