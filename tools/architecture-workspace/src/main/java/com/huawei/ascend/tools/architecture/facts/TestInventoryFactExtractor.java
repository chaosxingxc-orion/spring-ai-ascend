package com.huawei.ascend.tools.architecture.facts;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Wave-4 extractor: emits one {@code test} fact per JUnit-bearing class
 * found under each module's {@code target/test-classes}.
 *
 * <p>JUnit detection is bytecode-only: ASM annotation visit detects
 * {@code @Test}, {@code @ParameterizedTest}, {@code @RepeatedTest},
 * {@code @ArchTest} (ArchUnit). At least one method-level annotation
 * elevates the class to a test class.
 *
 * <p>Surefire/Failsafe XML report parsing (to attach a
 * {@code recent_run_present} flag or run-result evidence) is a future
 * enhancement outside the current Round-3 corrective scope. The
 * pre-2026-05-28 Javadoc claimed XML parsing as a shipped feature;
 * this overclaim was caught in the second-correction review (R5) and
 * trimmed. The current extractor does NOT inspect any XML report.
 *
 * <p>Authority: ADR-0154; Rule G-15 sub-clauses .a/.b.
 */
public final class TestInventoryFactExtractor {

    static final String EXTRACTOR_ID = "tools/architecture-workspace#TestInventoryFactExtractor";

    private static final Set<String> TEST_METHOD_ANNOTATIONS = Set.of(
            "Lorg/junit/jupiter/api/Test;",
            "Lorg/junit/jupiter/params/ParameterizedTest;",
            "Lorg/junit/jupiter/api/RepeatedTest;",
            "Lorg/junit/Test;",
            "Lcom/tngtech/archunit/junit/ArchTest;");

    private static final List<String> SCAN_MODULES = List.of(
            "agent-service",
            "agent-bus",
            "agent-runtime");

    private TestInventoryFactExtractor() {
    }

    public static void extract(ExtractorContext ctx, Path outputFile) throws IOException {
        extract(ctx, outputFile, false);
    }

    public static void extract(ExtractorContext ctx, Path outputFile, boolean allowMissingClasses) throws IOException {
        List<Map<String, Object>> facts = new ArrayList<>();
        List<String> unbuilt = new ArrayList<>();
        for (String module : SCAN_MODULES) {
            Path moduleDir = ctx.repoRoot().resolve(module);
            Path target = moduleDir.resolve("target");
            Path testClasses = moduleDir.resolve("target/test-classes");
            if (!Files.isDirectory(testClasses)) {
                // A module with no test-classes directory might be either
                // (a) not yet built (target/ also absent) — fail closed,
                //     OR
                // (b) built but legitimately has no tests (target/
                //     exists but test-classes doesn't) — silently skip.
                if (!Files.isDirectory(target)) {
                    unbuilt.add(module);
                }
                continue;
            }
            scanModule(ctx, module, testClasses, facts);
        }
        if (!unbuilt.isEmpty() && !allowMissingClasses) {
            throw new IOException(
                    "TestInventoryFactExtractor: module(s) not built — no target/ directory for "
                            + String.join(", ", unbuilt)
                            + "; run ./mvnw verify first OR pass --allow-missing-classes -- Rule G-15.b / P2-3");
        }
        facts.sort(Comparator.comparing(f -> String.valueOf(f.get("fact_id"))));
        FactWriter.write(outputFile, EXTRACTOR_ID, ctx.extractorVersion(), ctx.repoCommit(), facts);
    }

    private static void scanModule(ExtractorContext ctx, String module, Path testClasses, List<Map<String, Object>> out)
            throws IOException {
        try (Stream<Path> stream = Files.walk(testClasses)) {
            List<Path> classFiles = new ArrayList<>();
            stream.forEach(p -> {
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")) {
                    classFiles.add(p);
                }
            });
            classFiles.sort(Comparator.comparing(Path::toString));
            for (Path classFile : classFiles) {
                TestClassSnapshot snapshot;
                try (InputStream in = Files.newInputStream(classFile)) {
                    snapshot = readSnapshot(in);
                }
                if (snapshot.testMethods.isEmpty()) {
                    continue;
                }
                Map<String, Object> observed = new LinkedHashMap<>();
                observed.put("module", module);
                observed.put("fqn", snapshot.fqn);
                observed.put("kind", snapshot.isInterface ? "test_interface" : "test_class");
                List<String> sortedMethods = new ArrayList<>(snapshot.testMethods);
                sortedMethods.sort(Comparator.naturalOrder());
                observed.put("test_methods", sortedMethods);
                observed.put("source_file_guess",
                        module + "/src/test/java/" + snapshot.fqn.replace('.', '/') + ".java");
                out.add(FactWriter.entry(
                        "test/" + slug(snapshot.fqn),
                        "test",
                        "test",
                        module + "/target/test-classes/" + snapshot.fqn.replace('.', '/') + ".class",
                        snapshot.fqn,
                        EXTRACTOR_ID,
                        ctx.extractorVersion(),
                        ctx.repoCommit(),
                        observed));
            }
        }
    }

    private static TestClassSnapshot readSnapshot(InputStream in) throws IOException {
        TestClassSnapshot snap = new TestClassSnapshot();
        ClassReader reader = new ClassReader(in);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                              String[] interfaces) {
                snap.fqn = name.replace('/', '.');
                snap.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                             String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (TEST_METHOD_ANNOTATIONS.contains(desc)) {
                            snap.testMethods.add(name + descriptor);
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        return snap;
    }

    private static String slug(String fqn) {
        return fqn.toLowerCase().replace('.', '-').replace('$', '-');
    }

    private static final class TestClassSnapshot {
        String fqn = "";
        boolean isInterface = false;
        final Set<String> testMethods = new TreeSet<>();
    }
}
