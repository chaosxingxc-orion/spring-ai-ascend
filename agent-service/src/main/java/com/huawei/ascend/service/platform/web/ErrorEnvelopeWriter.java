package com.huawei.ascend.service.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Writes an {@link ErrorEnvelope} JSON body to an {@link HttpServletResponse}.
 *
 * <p>Used by servlet filters (JWT cross-check, idempotency, etc.) that detect a
 * failure before reaching Spring MVC. Phase G's {@code RunHttpExceptionMapper}
 * uses the same envelope shape for controller-level errors.
 *
 * <p>The shared {@link ObjectMapper} is intentionally local and minimal —
 * filters run early in the chain and we want to avoid pulling Spring beans
 * here. The mapper is thread-safe per Jackson docs.
 */
public final class ErrorEnvelopeWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorEnvelopeWriter() {
    }

    public static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getWriter(), ErrorEnvelope.of(code, message));
    }
}
