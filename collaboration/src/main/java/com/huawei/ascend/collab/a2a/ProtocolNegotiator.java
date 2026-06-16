package com.huawei.ascend.collab.a2a;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;

/**
 * Chooses which of a remote agent's advertised interfaces to speak — so a fleet of
 * mixed-version A2A agents interoperates safely.
 *
 * <p>The SDK's default selection blindly takes {@code supportedInterfaces.get(0)},
 * which breaks two ways in a mixed fleet: (a) the first interface may be a transport
 * this client does not speak (e.g. GRPC), and (b) it ignores the protocol version, so
 * a peer that has upgraded to a newer, incompatible major would be mis-spoken-to rather
 * than rejected. This negotiator instead:
 *
 * <ol>
 *   <li>keeps only interfaces whose {@code protocolBinding} this client speaks (JSONRPC);</li>
 *   <li>keeps only those whose {@code protocolVersion} major this client supports (a blank/absent
 *       version is treated as compatible — an older agent that omitted it);</li>
 *   <li>prefers the card's {@code preferredTransport} when it is among the survivors, else the
 *       highest compatible version;</li>
 *   <li>throws {@link IncompatibleException} — naming what the remote offered vs. what we support —
 *       when nothing is compatible, so the caller fails fast instead of silently mis-speaking.</li>
 * </ol>
 */
public final class ProtocolNegotiator {

    private ProtocolNegotiator() {
    }

    /** No interface the remote advertised is compatible with what this client speaks. */
    public static final class IncompatibleException extends RuntimeException {
        public IncompatibleException(String message) {
            super(message);
        }
    }

    public static AgentInterface select(AgentCard card, Set<String> spokenBindings, Set<Integer> supportedMajors) {
        List<AgentInterface> ifaces = card.supportedInterfaces();
        if (ifaces == null || ifaces.isEmpty()) {
            throw new IncompatibleException("remote agent advertises no interfaces");
        }

        AgentInterface best = null;
        int bestMajor = Integer.MIN_VALUE;
        Set<String> offered = new LinkedHashSet<>();
        for (AgentInterface i : ifaces) {
            offered.add(i.protocolBinding() + "@" + (i.protocolVersion() == null ? "?" : i.protocolVersion()));
            if (!spokenBindings.contains(i.protocolBinding())) {
                continue;
            }
            int major = majorOf(i.protocolVersion());
            boolean compatible = major < 0 || supportedMajors.contains(major); // major<0 = unspecified
            if (!compatible) {
                continue;
            }
            boolean preferred = i.protocolBinding().equals(card.preferredTransport());
            // Prefer the card's preferredTransport; otherwise the highest compatible major.
            if (best == null || preferred || major > bestMajor) {
                best = i;
                bestMajor = major;
                if (preferred) {
                    return best;
                }
            }
        }
        if (best == null) {
            throw new IncompatibleException(
                    "no compatible A2A interface: remote offers " + offered
                            + ", this client speaks " + spokenBindings + " at major version(s) " + supportedMajors);
        }
        return best;
    }

    /** Major version from a string like {@code "1.0"} / {@code "2"} / {@code "1.0.3"}; -1 if absent/unparseable. */
    static int majorOf(String protocolVersion) {
        if (protocolVersion == null || protocolVersion.isBlank()) {
            return -1;
        }
        StringBuilder digits = new StringBuilder();
        for (int idx = 0; idx < protocolVersion.length(); idx++) {
            char c = protocolVersion.charAt(idx);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break; // stop at the first separator after the leading number
            }
        }
        if (digits.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
