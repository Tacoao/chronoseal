package fr.chronoseal.capsule.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;
import fr.chronoseal.capsule.seal.SealIntegrityException;
import fr.chronoseal.capsule.seal.SealServiceException;
import fr.chronoseal.capsule.seal.SealServiceUnavailableException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CapsuleNotFoundException.class)
    ProblemDetail handleNotFound(CapsuleNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Capsule introuvable");
        return problem;
    }

    @ExceptionHandler(CapsuleStillSealedException.class)
    ProblemDetail handleStillSealed(CapsuleStillSealedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.LOCKED, exception.getMessage());
        problem.setTitle("Capsule encore scellée");
        problem.setProperty("unlockAt", exception.getUnlockAt());
        problem.setProperty("secondsRemaining", exception.getRemaining().toSeconds());
        return problem;
    }

    @ExceptionHandler(InvalidUnlockDateException.class)
    ProblemDetail handleInvalidUnlockDate(InvalidUnlockDateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Date d'ouverture invalide");
        return problem;
    }

    @ExceptionHandler(SealServiceUnavailableException.class)
    ProblemDetail handleSealUnavailable(SealServiceUnavailableException exception) {
        LOGGER.warn(exception.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service de scellement est momentanément indisponible. Réessayez plus tard.");
        problem.setTitle("Scellement indisponible");
        return problem;
    }

    @ExceptionHandler(SealIntegrityException.class)
    ProblemDetail handleSealIntegrity(SealIntegrityException exception) {
        LOGGER.error(exception.getMessage(), exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Le contenu de la capsule ne peut pas être authentifié.");
        problem.setTitle("Intégrité de la capsule compromise");
        return problem;
    }

    @ExceptionHandler(SealServiceException.class)
    ProblemDetail handleSealFailure(SealServiceException exception) {
        LOGGER.error(exception.getMessage(), exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Le service de scellement a renvoyé une erreur inattendue.");
        problem.setTitle("Erreur du service de scellement");
        return problem;
    }
}
