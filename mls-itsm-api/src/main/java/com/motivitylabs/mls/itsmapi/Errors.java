package com.motivitylabs.mls.itsmapi;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** What can go wrong, and the answer each gets. Messages are written for the person who asked, never with internals. */
public final class Errors {

    private Errors() {
    }

    public static class NotFound extends RuntimeException {
        public NotFound() {
            super("No such ticket");
        }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) {
            super(message);
        }
    }

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) {
            super(message);
        }
    }

    public static class TooLarge extends RuntimeException {
        public TooLarge(long max) {
            super("The build is larger than the " + (max / 1024 / 1024) + " MB limit");
        }
    }

    @RestControllerAdvice
    static class Handler {

        @ExceptionHandler(NotFound.class)
        ProblemDetail notFound(NotFound e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        }

        @ExceptionHandler(Forbidden.class)
        ProblemDetail forbidden(Forbidden e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        }

        @ExceptionHandler(BadRequest.class)
        ProblemDetail badRequest(BadRequest e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        @ExceptionHandler({TooLarge.class, MaxUploadSizeExceededException.class})
        ProblemDetail tooLarge(Exception e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e instanceof TooLarge ? e.getMessage() : "The build is larger than the limit");
        }

        @ExceptionHandler(TicketState.IllegalTransitionException.class)
        ProblemDetail illegal(TicketState.IllegalTransitionException e) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
