package com.huawei.ascend.collab.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.junit.jupiter.api.Test;

/**
 * Mixed-version fleet negotiation: pick an interface we actually speak at a compatible
 * protocol major, prefer the card's preferred transport, and reject a newer major
 * clearly instead of mis-speaking.
 */
class ProtocolNegotiatorTest {

    private static final Set<String> JSONRPC = Set.of("JSONRPC");
    private static final Set<Integer> V1 = Set.of(1);

    private static AgentCard card(String preferredTransport, AgentInterface... ifaces) {
        return AgentCard.builder()
                .name("a").description("d").version("0.1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text")).defaultOutputModes(List.of("text"))
                .skills(List.of())
                .preferredTransport(preferredTransport)
                .supportedInterfaces(List.of(ifaces))
                .build();
    }

    // AgentInterface record components are (protocolBinding, url, tenant, protocolVersion).
    private static AgentInterface iface(String binding, String url, String version) {
        return new AgentInterface(binding, url, null, version);
    }

    @Test
    void picksTheBindingWeSpeakNotJustTheFirst() {
        // First interface is GRPC (we don't speak it) — must skip to JSONRPC.
        AgentInterface chosen = ProtocolNegotiator.select(
                card(null,
                        iface("GRPC", "http://h/grpc", "1.0"),
                        iface("JSONRPC", "http://h/a2a", "1.0")),
                JSONRPC, V1);
        assertEquals("JSONRPC", chosen.protocolBinding());
        assertEquals("http://h/a2a", chosen.url());
    }

    @Test
    void prefersTheCardsPreferredTransportWhenCompatible() {
        AgentInterface chosen = ProtocolNegotiator.select(
                card("JSONRPC",
                        iface("JSONRPC", "http://h/a2a-v1", "1.0"),
                        iface("JSONRPC", "http://h/a2a-legacy", "1.0")),
                JSONRPC, V1);
        assertEquals("http://h/a2a-v1", chosen.url(), "first compatible preferred-transport interface wins");
    }

    @Test
    void rejectsANewerMajorWithAClearMessage() {
        // A peer that upgraded to protocol 2.0 — we speak major 1 only.
        ProtocolNegotiator.IncompatibleException ex = assertThrows(
                ProtocolNegotiator.IncompatibleException.class,
                () -> ProtocolNegotiator.select(
                        card(null, iface("JSONRPC", "http://h/a2a", "2.0")),
                        JSONRPC, V1));
        assertTrue(ex.getMessage().contains("JSONRPC@2.0"), "names what the peer offered");
        assertTrue(ex.getMessage().contains("[1]"), "names what we support");
    }

    @Test
    void absentVersionIsTreatedAsCompatible() {
        AgentInterface chosen = ProtocolNegotiator.select(
                card(null, new AgentInterface("JSONRPC", "http://h/a2a")), // no version
                JSONRPC, V1);
        assertEquals("http://h/a2a", chosen.url());
    }

    @Test
    void throwsWhenNoInterfaceIsSpoken() {
        assertThrows(ProtocolNegotiator.IncompatibleException.class,
                () -> ProtocolNegotiator.select(
                        card(null, new AgentInterface("GRPC", "http://h/grpc", "1.0")),
                        JSONRPC, V1));
    }

    @Test
    void majorParsing() {
        assertEquals(1, ProtocolNegotiator.majorOf("1.0"));
        assertEquals(2, ProtocolNegotiator.majorOf("2"));
        assertEquals(10, ProtocolNegotiator.majorOf("10.3.1"));
        assertEquals(-1, ProtocolNegotiator.majorOf(null));
        assertEquals(-1, ProtocolNegotiator.majorOf(""));
        assertEquals(-1, ProtocolNegotiator.majorOf("vX"));
    }
}
