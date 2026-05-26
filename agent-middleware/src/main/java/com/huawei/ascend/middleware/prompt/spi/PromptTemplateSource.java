package com.huawei.ascend.middleware.prompt.spi;

import java.util.Objects;

/**
 * Sealed discriminator for the origin of a prompt-template body.
 *
 * <p>Authority: ADR-0131, schema at
 * {@code docs/contracts/prompt-template.v1.yaml}.
 *
 * <p>Adding a new permitted source is an explicit ADR-changing
 * event — the sealed permits list keeps the loader surface
 * exhaustive.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
public sealed interface PromptTemplateSource
        permits PromptTemplateSource.InlineString,
                PromptTemplateSource.ClasspathResource {

    /** Variable placeholder format declared by the source. */
    PlaceholderSyntax syntax();

    /**
     * Inline template body carried as a literal string.
     *
     * @param body   raw template text; MUST be non-blank.
     * @param syntax placeholder syntax used inside {@code body}.
     */
    record InlineString(String body, PlaceholderSyntax syntax) implements PromptTemplateSource {
        public InlineString {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(syntax, "syntax");
            if (body.isBlank()) {
                throw new IllegalArgumentException("body must be non-blank");
            }
        }
    }

    /**
     * Template loaded from a classpath resource at render time.
     *
     * @param path   classpath resource path (e.g.
     *               {@code "prompts/support-agent.system.txt"});
     *               MUST be non-blank.
     * @param syntax placeholder syntax used inside the resource
     *               body.
     */
    record ClasspathResource(String path, PlaceholderSyntax syntax) implements PromptTemplateSource {
        public ClasspathResource {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(syntax, "syntax");
            if (path.isBlank()) {
                throw new IllegalArgumentException("path must be non-blank");
            }
        }
    }

    /** Placeholder syntax discriminator. */
    enum PlaceholderSyntax {
        /** {variable} style. */ MUSTACHE_SINGLE_BRACE,
        /** {{variable}} style. */ MUSTACHE_DOUBLE_BRACE,
        /** ${variable} style. */ DOLLAR_BRACE
    }
}
