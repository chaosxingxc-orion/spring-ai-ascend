package com.bank.financial.research;

/**
 * Silences the agent framework's own log chatter so a playground run prints only
 * the report. The framework uses a logger that ignores ordinary level changes, so
 * we detach its appenders via logback when available; if logback is absent this is
 * a no-op and the play-*.sh timestamp grep is the backstop.
 */
public final class LogQuieter {

    private LogQuieter() {
    }

    public static void quiet() {
        try {
            for (String name : new String[] {org.slf4j.Logger.ROOT_LOGGER_NAME,
                    "com.openjiuwen", "agent", "common", "trajectory", "research.engine"}) {
                ch.qos.logback.classic.Logger lg = (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(name);
                lg.detachAndStopAllAppenders();
                lg.setLevel(ch.qos.logback.classic.Level.OFF);
                lg.setAdditive(false);
            }
        } catch (Throwable ignored) {
            // not logback (or unavailable) — the play-*.sh grep filter is the backstop
        }
    }
}
