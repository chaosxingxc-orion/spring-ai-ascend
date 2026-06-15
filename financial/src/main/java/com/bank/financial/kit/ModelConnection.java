package com.bank.financial.kit;

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
        return new ModelConnection(
                env("BANK_LLM_PROVIDER", "openai"),
                env("BANK_LLM_API_KEY", "sk-local-placeholder"),
                env("BANK_LLM_API_BASE", "http://localhost:4000/v1"),
                env("BANK_LLM_MODEL", "gpt-5.4-mini"),
                Boolean.parseBoolean(env("BANK_LLM_SSL_VERIFY", "true")));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
