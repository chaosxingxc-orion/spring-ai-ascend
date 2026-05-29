package com.huawei.ascend.tools.architecture;

import com.structurizr.Workspace;
import com.structurizr.dsl.StructurizrDslParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProfileValidatorTest {

    @Test
    void validWorkspacePassesProfile() throws Exception {
        Workspace ws = parse("src/test/resources/valid-workspace.dsl");
        List<ProfileViolation> violations = new ProfileValidator().validate(ws);
        if (!violations.isEmpty()) {
            fail("Expected zero violations, got: " + violations);
        }
    }

    @Test
    void capabilityMissingRequiredPropertiesIsFlagged() throws Exception {
        Workspace ws = parse("src/test/resources/invalid-missing-properties.dsl");
        List<ProfileViolation> violations = new ProfileValidator().validate(ws);
        assertTrue(violations.stream().anyMatch(v -> v.itemId().equals("CAP-BROKEN")
                        && v.message().contains("saa.owner")),
                "CAP-BROKEN must produce saa.owner violation; got: " + violations);
        assertTrue(violations.stream().anyMatch(v -> v.itemId().equals("CAP-BROKEN")
                        && v.message().contains("saa.sourceAdr")),
                "CAP-BROKEN must produce saa.sourceAdr violation; got: " + violations);
    }

    @Test
    void functionPointMissingAllPropertiesProducesMultipleViolations() throws Exception {
        Workspace ws = parse("src/test/resources/invalid-missing-properties.dsl");
        List<ProfileViolation> violations = new ProfileValidator().validate(ws);
        long fpViolations = violations.stream()
                .filter(v -> v.message().startsWith("missing required property"))
                // The function point has no properties block — identityOf falls back to canonical name.
                .count();
        // Expected violations across the fixture:
        //   CAP-BROKEN  — 2 (saa.owner, saa.sourceAdr)
        //   fpBroken    — 7 (saa.id, saa.kind, saa.level, saa.view, saa.status, saa.owner, saa.sourceAdr)
        //   CAP-BADREL  — 0 (fully populated)
        // Total missing-property violations expected: 9
        assertEquals(9, fpViolations,
                "expected exactly 9 missing-property violations across CAP-BROKEN + fpBroken; got: " + violations);
    }

    @Test
    void shippedFrameMissingPrimaryPackageIsFlagged() throws Exception {
        // ADR-0161 §2: saa.primaryPackage is required when an EngineeringFrame is
        // shipped, optional when design_only. The fixture declares three frames.
        Workspace ws = parse("src/test/resources/engineering-frame-status-conditional.dsl");
        List<ProfileViolation> violations = new ProfileValidator().validate(ws);

        // The shipped frame that omits saa.primaryPackage MUST be flagged for it.
        assertTrue(violations.stream().anyMatch(v -> v.itemId().equals("EF-SHIPPED-NO-PKG")
                        && v.message().contains("saa.primaryPackage")),
                "EF-SHIPPED-NO-PKG must produce a saa.primaryPackage violation; got: " + violations);

        // The design_only frame omitting saa.primaryPackage MUST NOT be flagged.
        assertTrue(violations.stream().noneMatch(v -> v.itemId().equals("EF-DESIGN-ONLY")),
                "EF-DESIGN-ONLY (design_only) must produce no violation; got: " + violations);

        // The fully-populated shipped frame MUST NOT be flagged.
        assertTrue(violations.stream().noneMatch(v -> v.itemId().equals("EF-SHIPPED-OK")),
                "EF-SHIPPED-OK (fully populated) must produce no violation; got: " + violations);

        // saa.primaryPackage must be the ONLY violation across the fixture (every
        // frame carries saa.cardPath + all common/tag-specific props).
        assertEquals(1, violations.size(),
                "expected exactly one violation (EF-SHIPPED-NO-PKG saa.primaryPackage); got: " + violations);
    }

    @Test
    void illegalRelationshipTypeFlagged() throws Exception {
        Workspace ws = parse("src/test/resources/invalid-missing-properties.dsl");
        List<ProfileViolation> violations = new ProfileValidator().validate(ws);
        assertTrue(violations.stream().anyMatch(v -> v.message().contains("illegal saa.rel value")),
                "illegal saa.rel value must be flagged; got: " + violations);
    }

    @Test
    void customElementsReachable() throws Exception {
        // Regression guard for empirical-finding-1 (ADR-0148): in structurizr-dsl 6.2.1
        // Model.getElements() includes CustomElements. Walking it exactly once must produce
        // findings on the custom-element-backed CAP-BROKEN fixture.
        Workspace ws = parse("src/test/resources/invalid-missing-properties.dsl");
        assertTrue(ws.getModel().getCustomElements().size() >= 2,
                "fixture must declare ≥2 custom elements for this regression check to be meaningful");
        List<ProfileViolation> violations = new ProfileValidator().validate(ws);
        assertTrue(violations.size() > 0,
                "validator must reach CustomElements — got 0 violations on broken fixture");
    }

    private Workspace parse(String relativePath) throws Exception {
        StructurizrDslParser parser = new StructurizrDslParser();
        parser.parse(new File(relativePath));
        return parser.getWorkspace();
    }
}
