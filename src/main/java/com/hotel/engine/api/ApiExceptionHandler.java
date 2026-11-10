package com.hotel.engine.api;

import com.hotel.engine.domain.InvalidInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every error into the same JSON shape so the frontend can show it:
 * <pre>{ "status": 400, "error": "Bad Request", "message": "...", "fields": [ {"field": "...", "problem": "..."} ] }</pre>
 * Bad input is always a 400 with a message the user can act on, never a 500.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record FieldProblem(String field, String problem) {
    }

    public record ApiError(int status, String error, String message, List<FieldProblem> fields) {
    }

    /** Business rules broken in a service, e.g. duplicate room number or check-out before check-in. */
    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ApiError> invalidInput(InvalidInputException e) {
        return badRequest(e.getMessage(), List.of());
    }

    /** Thrown by the algorithm classes on bad arguments that slipped past the service. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> illegalArgument(IllegalArgumentException e) {
        return badRequest(e.getMessage(), List.of());
    }

    /** @Valid on a request body failed, e.g. a guest with a blank name. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        List<FieldProblem> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), messageOf(f)))
                .toList();
        return badRequest("Some fields need fixing.", fields);
    }

    /** Constraints on query parameters failed, e.g. ?n=0 where n must be at least 1. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParameters(HandlerMethodValidationException e) {
        List<FieldProblem> fields = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new FieldProblem(r.getMethodParameter().getParameterName(),
                                err.getDefaultMessage())))
                .toList();
        return badRequest("Some parameters need fixing.", fields);
    }

    /** Body isn't valid JSON, or a value has the wrong type: an unknown room type or a badly written date. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return badRequest("The request body couldn't be read. Check the JSON, that room types are "
                + "STANDARD, DELUXE or SUITE, and that dates look like 2026-11-12.", List.of());
    }

    /** A query parameter has the wrong type, e.g. ?type=PENTHOUSE. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("\"" + e.getValue() + "\" isn't a valid value for " + e.getName() + ".",
                List.of(new FieldProblem(e.getName(), "invalid value")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException e) {
        return badRequest("The " + e.getParameterName() + " parameter is required.",
                List.of(new FieldProblem(e.getParameterName(), "required")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> wrongMethod(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, e.getMethod() + " isn't supported here.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Nothing at /" + e.getResourcePath() + ".", List.of());
    }

    /** Anything unexpected is a bug: log it in full, but don't leak details to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on the server. The error has been logged.", List.of());
    }

    private static String messageOf(FieldError f) {
        return f.getDefaultMessage() == null ? "invalid value" : f.getDefaultMessage();
    }

    private static ResponseEntity<ApiError> badRequest(String message, List<FieldProblem> fields) {
        return error(HttpStatus.BAD_REQUEST, message, fields);
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message, List<FieldProblem> fields) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message, fields));
    }
}
