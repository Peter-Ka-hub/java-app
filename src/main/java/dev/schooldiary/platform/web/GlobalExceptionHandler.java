package dev.schooldiary.platform.web;

import dev.schooldiary.shared.error.ConflictException;
import dev.schooldiary.shared.error.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleForbidden(
      AccessDeniedException ex, HttpServletRequest req) {
    return build(HttpStatus.FORBIDDEN, "Access denied", List.of(), req);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest req) {
    return build(
        HttpStatus.UNAUTHORIZED, "Invalid credentials or authentication required", List.of(), req);
  }

  @ExceptionHandler({ConflictException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<ApiErrorResponse> handleStateConflict(
      Exception ex, HttpServletRequest req) {
    return build(
        HttpStatus.CONFLICT,
        ex instanceof ConflictException
            ? ex.getMessage()
            : "Resource changed. Reload it and retry.",
        List.of(),
        req);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFound(
      NotFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of(), req);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleBadRequest(
      IllegalArgumentException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of(), req);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "Invalid parameter: " + ex.getName(), List.of(), req);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "Malformed JSON or invalid field value", List.of(), req);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<String> errors =
        ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .sorted()
            .toList();
    return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, req);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodValidation(
      HandlerMethodValidationException ex, HttpServletRequest req) {
    List<String> errors =
        ex.getAllErrors().stream().map(e -> String.valueOf(e.getDefaultMessage())).toList();
    return build(HttpStatus.BAD_REQUEST, "Validation failed", errors, req);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(
      DataIntegrityViolationException ex, HttpServletRequest req) {
    log.warn("Data integrity violation: {}", ex.getClass().getSimpleName());
    return build(HttpStatus.CONFLICT, "Data integrity violation", List.of(), req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
    if (ex instanceof ErrorResponse errorResponse) {
      // NoResourceFoundException (404), HttpRequestMethodNotSupportedException (405) itd.
      var body =
          build(errorResponse.getStatusCode(), "Request could not be processed", List.of(), req);
      return ResponseEntity.status(body.getStatusCode())
          .headers(errorResponse.getHeaders())
          .body(body.getBody());
    }
    log.error("Unhandled exception", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", List.of(), req);
  }

  private ResponseEntity<ApiErrorResponse> build(
      HttpStatusCode status, String message, List<String> errors, HttpServletRequest req) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    String reason = resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
    return ResponseEntity.status(status)
        .body(
            new ApiErrorResponse(
                Instant.now(), status.value(), reason, message, req.getRequestURI(), errors));
  }
}
