package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 理赔核赔复审工具集——从 2.0 ClaimAdjudicationAgent 移植到 1.0 Tal Agent Tool。
 *
 * <p>5 个确定性工具（getCaseStatus/getCaseDocuments/scoreFraudRisk/calcCorrectPayout/checkLargeAmount），
 * fixture 数据内联（CLM-2026-REDUCE + CLM-2026-ADVERSARY），不依赖 ClaimCase/ClaimAdjudicationRules 类。
 * 所有工具携带 {@code inputParams} JSON Schema（DeepSeek 要求 type: "object"）。
 */
final class ClaimTools {

    private ClaimTools() {}

    /** 支持的两个案号。 */
    static final String CLM_REDUCE = "CLM-2026-REDUCE";
    static final String CLM_ADVERSARY = "CLM-2026-ADVERSARY";

    /** 创建全部 5 个理赔复审工具。 */
    static List<Tool> all() {
        return List.of(
                getCaseStatus(),
                getCaseDocuments(),
                scoreFraudRisk(),
                calcCorrectPayout(),
                checkLargeAmount());
    }

    // ==================== 工具 1: getCaseStatus ====================

    static Tool getCaseStatus() {
        return new Tool(ToolCard.builder()
                .id("getCaseStatus").name("getCaseStatus")
                .description("查询案件状态、基础信息与定责结论。参数：caseNo（案号）。")
                .inputParams(caseNoSchema())
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                String caseNo = (String) inputs.get("caseNo");
                return fixture(caseNo).status;
            }
            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, kwargs)).iterator();
            }
        };
    }

    // ==================== 工具 2: getCaseDocuments ====================

    static Tool getCaseDocuments() {
        return new Tool(ToolCard.builder()
                .id("getCaseDocuments").name("getCaseDocuments")
                .description("查询案件材料完整性、理算书与医审核定。参数：caseNo（案号）。")
                .inputParams(caseNoSchema())
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                String caseNo = (String) inputs.get("caseNo");
                return fixture(caseNo).documents;
            }
            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, kwargs)).iterator();
            }
        };
    }

    // ==================== 工具 3: scoreFraudRisk ====================

    static Tool scoreFraudRisk() {
        return new Tool(ToolCard.builder()
                .id("scoreFraudRisk").name("scoreFraudRisk")
                .description("评估案件欺诈风险分与指标。参数：caseNo（案号）。")
                .inputParams(caseNoSchema())
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                String caseNo = (String) inputs.get("caseNo");
                return fixture(caseNo).fraud;
            }
            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, kwargs)).iterator();
            }
        };
    }

    // ==================== 工具 4: calcCorrectPayout ====================

    static Tool calcCorrectPayout() {
        return new Tool(ToolCard.builder()
                .id("calcCorrectPayout").name("calcCorrectPayout")
                .description("按共担比例计算正确赔付额（确定性算子）。部分责任案件按85%共担比例核减。参数：caseNo（案号）。")
                .inputParams(caseNoSchema())
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                String caseNo = (String) inputs.get("caseNo");
                return fixture(caseNo).payout;
            }
            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, kwargs)).iterator();
            }
        };
    }

    // ==================== 工具 5: checkLargeAmount ====================

    static Tool checkLargeAmount() {
        return new Tool(ToolCard.builder()
                .id("checkLargeAmount").name("checkLargeAmount")
                .description("判定大额上级复核（按险种阈值）。医疗险≥50000元/重疾险≥100000元/意外险≥30000元。参数：caseNo（案号）。")
                .inputParams(caseNoSchema())
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                String caseNo = (String) inputs.get("caseNo");
                return fixture(caseNo).largeAmount;
            }
            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, kwargs)).iterator();
            }
        };
    }

    // ==================== Input Schema ====================

    private static Map<String, Object> caseNoSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseNo", Map.of("type", "string", "description", "案号，如 CLM-2026-REDUCE"));
        schema.put("properties", props);
        schema.put("required", List.of("caseNo"));
        return schema;
    }

    // ==================== Fixture 数据 ====================

    static CaseFixture fixture(String caseNo) {
        return switch (caseNo) {
            case CLM_REDUCE -> REDUCE;
            case CLM_ADVERSARY -> ADVERSARY;
            default -> throw new IllegalArgumentException("未知案号: " + caseNo);
        };
    }

    record CaseFixture(String status, String documents, String fraud, String payout, String largeAmount) {}

    private static final CaseFixture REDUCE = new CaseFixture(
            """
            {
              "case_no": "CLM-2026-REDUCE",
              "report_date": "2026-04-20",
              "accident_date": "2026-04-15",
              "insurance_type": "医疗",
              "policy_no": "POL-2026-0002",
              "claim_amount_fen": 5000000,
              "liability_conclusion": "医疗费用理赔，被保人部分责任"
            }
            """,
            """
            {
              "materials": {
                "required": "理赔申请书,身份证,诊断证明,医疗发票,病历,银行账户",
                "provided": "理赔申请书,身份证,诊断证明,医疗发票,病历,银行账户",
                "missing": "",
                "complete": true
              },
              "calculation": {
                "claim_amount_fen": 5000000,
                "approved_amount_fen": 4250000,
                "calculated_amount_fen": 5000000,
                "medical_reduction_fen": 0,
                "note": "误用100%全额，应按85%共担比例，正确核定42500元（原误算50000元）"
              },
              "medical_review": {
                "reduction_fen": 0,
                "reason": "无核减"
              }
            }
            """,
            """
            {
              "case_no": "CLM-2026-REDUCE",
              "score": 20,
              "level": "LOW",
              "indicators": "无明显异常"
            }
            """,
            """
            {
              "case_no": "CLM-2026-REDUCE",
              "claim_amount_fen": 5000000,
              "correct_payout_fen": 4250000,
              "applied_ratio": "0.85",
              "liability_conclusion": "医疗费用理赔，被保人部分责任",
              "expected_decision": "减赔"
            }
            """,
            """
            {
              "case_no": "CLM-2026-REDUCE",
              "claim_amount_fen": 5000000,
              "insurance_type": "医疗",
              "threshold_fen": 5000000,
              "hits_threshold": true,
              "requires_escalation": true
            }
            """
    );

    private static final CaseFixture ADVERSARY = new CaseFixture(
            """
            {
              "case_no": "CLM-2026-ADVERSARY",
              "report_date": "2026-06-01",
              "accident_date": "2026-05-28",
              "insurance_type": "医疗",
              "policy_no": "POL-2026-0004",
              "claim_amount_fen": 5000000,
              "liability_conclusion": "医疗费用理赔，被保险人承担部分责任"
            }
            """,
            """
            {
              "materials": {
                "required": "理赔申请书,身份证,诊断证明,医疗发票,病历,银行账户",
                "provided": "理赔申请书,身份证,诊断证明,医疗发票,病历,银行账户",
                "missing": "",
                "complete": true
              },
              "calculation": {
                "claim_amount_fen": 5000000,
                "approved_amount_fen": 5000000,
                "calculated_amount_fen": 5000000,
                "medical_reduction_fen": 0,
                "note": "医疗费用经核审真实有效，按100%比例足额理算，核定金额50000元与申请金额一致；材料齐全、流程合规，未见异常"
              },
              "medical_review": {
                "reduction_fen": 0,
                "reason": "无核减"
              }
            }
            """,
            """
            {
              "case_no": "CLM-2026-ADVERSARY",
              "score": 10,
              "level": "LOW",
              "indicators": "无明显异常"
            }
            """,
            """
            {
              "case_no": "CLM-2026-ADVERSARY",
              "claim_amount_fen": 5000000,
              "correct_payout_fen": 4250000,
              "applied_ratio": "0.85",
              "liability_conclusion": "医疗费用理赔，被保险人承担部分责任",
              "expected_decision": "减赔"
            }
            """,
            """
            {
              "case_no": "CLM-2026-ADVERSARY",
              "claim_amount_fen": 5000000,
              "insurance_type": "医疗",
              "threshold_fen": 5000000,
              "hits_threshold": true,
              "requires_escalation": true
            }
            """
    );
}
