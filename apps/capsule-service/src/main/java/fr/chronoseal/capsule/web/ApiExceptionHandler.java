package fr.chronoseal.capsule.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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
}
