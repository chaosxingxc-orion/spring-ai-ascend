package ascend.springai.service.platform.web.runs;

import ascend.springai.service.platform.web.ErrorEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Maps controller-level exceptions to the stable {@link ErrorEnvelope} shape
 * (ADR-0056 / plan §6.4). Servlet filters use {@code ErrorEnvelopeWriter}
 * directly; this advice covers the Spring MVC entry points.
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E7, #E8.
 */
@ControllerAdvice(assignableTypes = RunController.class)
public class RunHttpExceptionMapper {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(ErrorEnvelope.of("invalid_run_spec", buildMessage(ex)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> malformed(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("invalid_request",
                        "Request body could not be parsed."));
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorEnvelope> json(JsonProcessingException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("invalid_request", "Malformed JSON body."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> illegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("invalid_request", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorEnvelope> unexpected(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of("internal_error", "Unexpected server error."));
    }

    private static String buildMessage(MethodArgumentNotValidException ex) {
        if (ex.getBindingResult().getFieldErrorCount() == 0) {
            return "Request validation failed.";
        }
        return ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Request validation failed.");
    }
}
