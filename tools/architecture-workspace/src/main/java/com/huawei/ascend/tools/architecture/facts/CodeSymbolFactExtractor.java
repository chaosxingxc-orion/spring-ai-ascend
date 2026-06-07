package com.huawei.ascend.tools.architecture.facts;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Wave-4 extractor: emits {@code code_symbol} facts by reading compiled
 * {@code .class} files under each module's {@code target/classes} via
 * ASM 9.x.
 *
 * <p>For every public top-level type, the fact carries: FQN, kind
 * (interface / class / record / enum / annotation), modifiers, declared
 * annotations (visible runtime + class-retention), public method
 * signatures, and (for record types) the component-name list. SPI
 * package memberships are inferred from package prefixes declared in
 * each {@code <module>/module-metadata.yaml#spi_packages}.
 *
 * <p>Bytecode is the authoritative surface here because annotation-
 * processed types (Lombok, MapStruct, springdoc-openapi) only appear
 * after javac runs. JavaParser source-AST overlay (Javadoc, source
 * spans) is a follow-up extension; the W4 cut is ASM-only.
 *
 * <p>Authority: ADR-0154; Rule G-15 sub-clauses .a/.b.
 */
public final class CodeSymbolFactExtractor {

    static final String EXTRACTOR_ID = "tools/architecture-workspace#CodeSymbolFactExtractor";

    private static final List<String> SCAN_MODULES = List.of(
            "agent-service",
            "agent-bus",
            "agent-runtime");

    private CodeSymbolFactExtractor() {
    }

    public static void extract(ExtractorContext ctx, Path outputFile) throws IOException {
        extract(ctx, outputFile, false);
    }

    public static void extract(ExtractorContext ctx, Path outputFile, boolean allowMissingClasses) throws IOException {
        List<Map<String, Object>> facts = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String module : SCAN_MODULES) {
            Path classesDir = ctx.repoRoot().resolve(module).resolve("target/classes");
            if (!Files.isDirectory(classesDir)) {
                missing.add(module);
                continue;
            }
            scanModule(ctx, module, classesDir, facts);
        }
        if (!missing.isEmpty() && !allowMissingClasses) {
            // Round-2 Wave B (2026-05-28 P2-3): silent skip masks partial
            // extraction. Active modules without compiled classes fail
            // closed unless --allow-missing-classes was explicitly set
            // (fixture / bootstrap mode).
            throw new IOException(
                    "CodeSymbolFactExtractor: missing target/classes for module(s) "
                            + String.join(", ", missing)
                            + "; run ./mvnw verify first OR pass --allow-missing-classes -- Rule G-15.b / P2-3");
        }
        // Sort by fact_id for determinism.
        facts.sort(Comparator.comparing(f -> String.valueOf(f.get("fact_id"))));
        FactWriter.write(outputFile, EXTRACTOR_ID, ctx.extractorVersion(), ctx.repoCommit(), facts);
    }

    private static void scanModule(ExtractorContext ctx, String module, Path classesDir, List<Map<String, Object>> out)
            throws IOException {
        try (Stream<Path> stream = Files.walk(classesDir)) {
            List<Path> classFiles = new ArrayList<>();
            stream.forEach(p -> {
                if (Files.isRegularFile(p) && p.toString().endsWith(".class")
                        && !p.getFileName().toString().equals("module-info.class")) {
                    classFiles.add(p);
                }
            });
            classFiles.sort(Comparator.comparing(Path::toString));
            for (Path classFile : classFiles) {
                try (InputStream in = Files.newInputStream(classFile)) {
                    ClassReader reader = new ClassReader(in);
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
                    if ((node.access & Opcodes.ACC_PUBLIC) == 0) {
                        // Skip non-public top-level types — fact layer cares about extension surfaces.
                        if (!isPublicInnerOrAnonymous(node.name)) {
                            continue;
                        }
                    }
                    Map<String, Object> observed = buildObservedValue(module, node);
                    String fqn = node.name.replace('/', '.');
                    out.add(FactWriter.entry(
                            "code-symbol/" + slug(fqn),
                            "code_symbol",
                            "code",
                            module + "/target/classes/" + node.name + ".class",
                            fqn,
                            EXTRACTOR_ID,
                            ctx.extractorVersion(),
                            ctx.repoCommit(),
                            observed));
                }
            }
        }
    }

    private static boolean isPublicInnerOrAnonymous(String internalName) {
        // Inner classes have '$' in their internal name; we still emit them only
        // if the simple-name is not anonymous (digits-only after the last '$').
        int dollar = internalName.lastIndexOf('$');
        if (dollar < 0) {
            return false;
        }
        String tail = internalName.substring(dollar + 1);
        return !tail.isEmpty() && !tail.chars().allMatch(Character::isDigit);
    }

    private static Map<String, Object> buildObservedValue(String module, ClassNode node) {
        Map<String, Object> observed = new LinkedHashMap<>();
        observed.put("module", module);
        observed.put("fqn", node.name.replace('/', '.'));
        observed.put("kind", classifyKind(node));
        observed.put("modifiers", describeModifiers(node.access));
        observed.put("package", packageOf(node.name));
        observed.put("super", node.superName != null ? node.superName.replace('/', '.') : null);
        observed.put("interfaces", interfaceList(node));
        observed.put("annotations", collectAnnotations(node));
        observed.put("public_methods", collectPublicMethods(node));
        if ((node.access & Opcodes.ACC_RECORD) != 0 && node.recordComponents != null) {
            List<String> components = new ArrayList<>();
            node.recordComponents.forEach(rc -> components.add(rc.name + ": " + rc.descriptor));
            observed.put("record_components", components);
        }
        return observed;
    }

    private static String classifyKind(ClassNode node) {
        int a = node.access;
        if ((a & Opcodes.ACC_INTERFACE) != 0 && (a & Opcodes.ACC_ANNOTATION) != 0) {
            return "annotation";
        }
        if ((a & Opcodes.ACC_INTERFACE) != 0) {
            return "interface";
        }
        if ((a & Opcodes.ACC_ENUM) != 0) {
            return "enum";
        }
        if ((a & Opcodes.ACC_RECORD) != 0) {
            return "record";
        }
        if ((a & Opcodes.ACC_ABSTRACT) != 0) {
            return "abstract_class";
        }
        return "class";
    }

    private static List<String> describeModifiers(int access) {
        List<String> mods = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) mods.add("public");
        if ((access & Opcodes.ACC_FINAL) != 0) mods.add("final");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) mods.add("abstract");
        if ((access & Opcodes.ACC_STATIC) != 0) mods.add("static");
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) mods.add("synthetic");
        return mods;
    }

    private static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.');
    }

    private static List<String> interfaceList(ClassNode node) {
        List<String> out = new ArrayList<>();
        if (node.interfaces != null) {
            for (String iface : node.interfaces) {
                out.add(iface.replace('/', '.'));
            }
        }
        return out;
    }

    private static List<String> collectAnnotations(ClassNode node) {
        List<String> out = new ArrayList<>();
        if (node.visibleAnnotations != null) {
            for (AnnotationNode an : node.visibleAnnotations) {
                out.add(stripL(an.desc));
            }
        }
        if (node.invisibleAnnotations != null) {
            for (AnnotationNode an : node.invisibleAnnotations) {
                out.add(stripL(an.desc));
            }
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static List<String> collectPublicMethods(ClassNode node) {
        List<String> out = new ArrayList<>();
        if (node.methods == null) {
            return out;
        }
        for (MethodNode m : node.methods) {
            if ((m.access & Opcodes.ACC_PUBLIC) == 0) continue;
            if ((m.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
            if ((m.access & Opcodes.ACC_BRIDGE) != 0) continue;
            if ("<clinit>".equals(m.name)) continue;
            out.add(m.name + m.desc);
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private static String stripL(String desc) {
        if (desc == null) return "";
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1).replace('/', '.');
        }
        return desc.replace('/', '.');
    }

    private static String slug(String fqn) {
        // Inner classes carry '$' separators; the fact-id schema accepts only
        // [a-z0-9/-]. Map both '.' and '$' to '-' so inner-class slugs match.
        return fqn.toLowerCase().replace('.', '-').replace('$', '-');
    }
}
