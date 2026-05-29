workspace "EngineeringFrame Status-Conditional Test" "Fixture for by_tag_status saa.primaryPackage" {
    model {
        // Shipped frame WITHOUT saa.primaryPackage -> MUST be flagged (ADR-0161 §2).
        // saa.cardPath IS present, so the only expected violation is saa.primaryPackage.
        efShippedNoPkg = element "Shipped Frame No Package" "EngineeringFrame" "Shipped, missing primaryPackage" "SAA EngineeringFrame" {
            properties {
                "saa.id" "EF-SHIPPED-NO-PKG"
                "saa.kind" "engineering_frame"
                "saa.level" "L1"
                "saa.view" "logical"
                "saa.status" "shipped"
                "saa.owner" "agent-bus"
                "saa.sourceAdr" "ADR-0161"
                "saa.cardPath" "architecture/docs/L1/frames/EF-SHIPPED-NO-PKG.md"
            }
        }

        // design_only frame WITHOUT saa.primaryPackage -> MUST NOT be flagged.
        efDesignOnly = element "Design-Only Frame" "EngineeringFrame" "design_only, primaryPackage optional" "SAA EngineeringFrame" {
            properties {
                "saa.id" "EF-DESIGN-ONLY"
                "saa.kind" "engineering_frame"
                "saa.level" "L1"
                "saa.view" "logical"
                "saa.status" "design_only"
                "saa.owner" "agent-bus"
                "saa.sourceAdr" "ADR-0161"
                "saa.cardPath" "architecture/docs/L1/frames/EF-DESIGN-ONLY.md"
            }
        }

        // Fully-populated shipped frame -> MUST NOT be flagged.
        efShippedOk = element "Shipped Frame OK" "EngineeringFrame" "Shipped with all required props" "SAA EngineeringFrame" {
            properties {
                "saa.id" "EF-SHIPPED-OK"
                "saa.kind" "engineering_frame"
                "saa.level" "L1"
                "saa.view" "logical"
                "saa.status" "shipped"
                "saa.owner" "agent-bus"
                "saa.sourceAdr" "ADR-0161"
                "saa.cardPath" "architecture/docs/L1/frames/EF-SHIPPED-OK.md"
                "saa.primaryPackage" "com.huawei.ascend.bus.spi.ingress"
            }
        }
    }
}
