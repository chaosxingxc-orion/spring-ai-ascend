/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Hand-test entry point. Reads {@code sample-prompts.txt} from the test classpath and runs
 * each non-blank, non-comment line through {@link HotelPlanningAgent#chat(String)}. Print
 * the markdown to stdout so you can eyeball the model's behavior.
 *
 * <p>This is intentionally <em>not</em> a JUnit test — it makes real LLM calls and is gated
 * behind running the main method manually. CI should not invoke it.
 *
 * <p>Run from the module root:
 * <pre>
 * mvn -pl examples/travel/agent-hotel \
 *     -DskipTests \
 *     -Dexec.mainClass=com.huawei.ascend.examples.hotel.SampleMain \
 *     -Dexec.classpathScope=test \
 *     exec:java
 * </pre>
 *
 * <p>Make sure {@code LLM_API_KEY} / {@code LLM_API_BASE} / {@code LLM_MODEL} are set in
 * your env first.
 */
public final class SampleMain {

    private static final String PROMPTS_RESOURCE = "sample-prompts.txt";

    private SampleMain() {
    }

    public static void main(String[] args) throws Exception {
        // Windows consoles default to GBK; force UTF-8 so Chinese prompts/responses
        // do not get mojibake'd on stdout/stderr.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        LlmConfig llm = LlmConfig.fromEnv();
        System.out.println("[SampleMain] using model=" + llm.modelName()
                + " at " + llm.apiBase());

        try (HotelPlanningAgent agent = new HotelPlanningAgent(llm)) {
            System.out.println("[SampleMain] inventory size = " + agent.inventorySize());

            try (InputStream in = SampleMain.class.getClassLoader()
                    .getResourceAsStream(PROMPTS_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing classpath resource: " + PROMPTS_RESOURCE);
                }
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    int idx = 0;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        idx++;
                        printSeparator(idx, trimmed);
                        try {
                            System.out.println(agent.chat(trimmed));
                        } catch (RuntimeException e) {
                            System.err.println("[SampleMain] prompt " + idx + " failed: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private static void printSeparator(int idx, String prompt) {
        System.out.println();
        System.out.println("================ prompt " + idx + " ================");
        System.out.println(">>> " + prompt);
        System.out.println("---");
    }
}
