# to-be sequence delta — two-hop governance relay

The two-hop governance relay (closes the slice-3 single-hop bypass). Client → GW (controller, `++`) → produce `T_invocation_req` (hop1) → **[++] `EventBusRelayWorker`** polls it → **GOVERNANCE** (inbox dedup + tenant + correlation + audit) → produce `T_invocation_deliver` (hop2, `++`, was unused) → runtime (in-repo `TestAgentRuntime` double / external) consumes → produces `T_invocation_resp_in` (resp hop1) → **[++] relay** polls → **governance** → produce `T_invocation_resp_out` (resp hop2, `++`) → gateway response consumer polls → classify → `IngressResponse`.

The boxed governance acts are now **exercised end-to-end** — the as-is zero-coverage gap (handoff §3.3 gap #2) is closed. `++` added · `~` modified · `--` legacy.

**Ties to decision tree:** Q1a (relay consume→govern→re-produce), Q4a (in-repo double so the two-hop IT runs without the external repo). The `T_invocation_deliver` / `resp_in` / `resp_out` topics — provisioned but unused in as-is — are now the relay's actual transport.
