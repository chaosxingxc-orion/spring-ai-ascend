package com.bank.financial.kit;

import java.util.Locale;

/**
 * Immutable LLM connection settings for a financial agent. Carried from Spring
 * configuration into the agent handler so the model wiring is declared once.
 */
public record ModelConnection(
        String provider,
        String apiKey,
        String apiBase,
        String modelName,
        boolean sslVerify) {

    /** Build from the standard BANK_LLM_* env vars (with safe local defaults). */
    public static ModelConnection fromEnv() {
        return forTier(null);
    }

    /**
     * Model tiering for cost control: a simple agent can run a cheaper model.
     * The model name comes from {@code BANK_LLM_MODEL_<TIER>} (e.g. tier "fast"
     * → {@code BANK_LLM_MODEL_FAST}); if that is unset it falls back to
     * {@code BANK_LLM_MODEL}. So with no tier env set, every agent behaves
     * exactly like {@link #fromEnv()} — tiering is opt-in, zero default change.
     */
    public static ModelConnection forTier(String tier) {
        String model = env("BANK_LLM_MODEL", "gpt-5.4-mini");
        if (tier != null && !tier.isBlank()) {
            String tiered = System.getenv("BANK_LLM_MODEL_" + tier.toUpperCase(Locale.ROOT));
            if (tiered != null && !tiered.isBlank()) {
                model = tiered;
            }
        }
        return new ModelConnection(
                env("BANK_LLM_PROVIDER", "openai"),
                env("BANK_LLM_API_KEY", "sk-local-placeholder"),
                env("BANK_LLM_API_BASE", "http://localhost:4000/v1"),
                model,
                Boolean.parseBoolean(env("BANK_LLM_SSL_VERIFY", "true")));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
