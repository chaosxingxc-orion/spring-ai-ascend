package com.example.finance.loanreview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * v1.0 reference financial agent entry point.
 *
 * <p>Authority: PC-003 + PC-001; serves Persona-F (Enterprise Compliance /
 * Risk Officer) via the {@code COMPLIANCE-REVIEW.md} worked example.
 *
 * <p>The agent composes three {@code Skill} SPI implementations (ADR-0127):
 * <ul>
 *   <li>{@code cifSkill}              — Customer Information File lookup (stub)</li>
 *   <li>{@code transactionHistorySkill} — last-90-day cash-flow (stub)</li>
 *   <li>{@code creditBureauSkill}     — bureau score + adverse flags (stub)</li>
 * </ul>
 *
 * <p>Sandbox: runs under {@code financial_default} from
 * {@code docs/governance/sandbox-policies.yaml}. Outbound internet denied
 * by default; FS writes confined to {@code /var/agent-data/scratch};
 * CPU 2 vCPU; memory 2 GiB; wall-clock 60 s per invocation.
 *
 * <p>This sample is intentionally stateless: no Flyway migrations, no
 * persistence layer. Any deployment that adds state MUST also add the
 * RLS migration (Rule R-J.a) and re-run Persona-F's checklist item #1.
 */
@SpringBootApplication
public class LoanReviewAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanReviewAgentApplication.class, args);
    }
}
