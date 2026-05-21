package com.huawei.ascend.service.platform.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Servlet request wrapper that exposes a fully-read, replayable body.
 *
 * <p>{@link org.springframework.web.util.ContentCachingRequestWrapper} is unsuitable
 * for the idempotency filter use-case: its {@code getInputStream()} delegates
 * straight to the underlying {@link HttpServletRequest#getInputStream()} and
 * caches bytes <em>only as a side-effect of the caller reading the wrapper's
 * stream</em>. Once that delegate stream is exhausted, a second
 * {@code getInputStream()} call (e.g. by Jackson's {@code @RequestBody}
 * deserialiser further down the chain) returns an empty stream — the cached
 * bytes are accessible solely via {@code getContentAsByteArray()} and never
 * replayed. The platform observed this as authenticated {@code POST /v1/runs}
 * receiving 400 with a {@code @RequestBody} of all-null fields.
 *
 * <p>This wrapper instead reads the underlying body fully in its constructor
 * and serves every {@link #getInputStream()} / {@link #getReader()} call from
 * the cached byte array, so downstream consumers always see a fresh, complete
 * stream.
 *
 * <p>Memory safety: callers MUST upper-bound the body size before constructing
 * this wrapper (see {@link IdempotencyHeaderFilter}'s explicit limit).
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
        super(request);
        this.cachedBody = cachedBody;
    }

    byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        String enc = getCharacterEncoding();
        Charset charset = (enc == null || enc.isBlank()) ? StandardCharsets.UTF_8 : Charset.forName(enc);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException(
                    "Async ReadListener not supported on cached-body wrapper");
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public int available() {
            return delegate.available();
        }
    }
}
