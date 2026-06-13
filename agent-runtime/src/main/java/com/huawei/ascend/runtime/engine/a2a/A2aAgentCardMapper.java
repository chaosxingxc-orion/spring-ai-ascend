package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentCardDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentInterfaceDescriptor;
import com.huawei.ascend.runtime.engine.spi.AgentSkillDescriptor;
import com.huawei.ascend.runtime.engine.spi.SecuritySchemeDescriptor;
import com.huawei.ascend.runtime.engine.spi.SignatureDescriptor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.a2aproject.sdk.spec.APIKeySecurityScheme;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCardSignature;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.MutualTLSSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.a2aproject.sdk.spec.SecurityScheme;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Single A2A projection point: converts an {@link AgentCardDescriptor} to a
 * wire-ready {@link AgentCard}. This is the only class in the runtime that
 * constructs A2A wire types from descriptor values.
 *
 * <p>Default-card logic (blank-to-default field coalescing) lives here rather
 * than in the descriptor, so the descriptor stays a plain value object.
 */
public final class A2aAgentCardMapper {

    private A2aAgentCardMapper() {
    }

    /**
     * Maps a neutral {@link AgentCardDescriptor} to a wire-ready {@link AgentCard}.
     *
     * <p>Blank/null fields draw fail-safe defaults: streaming=false, push=false,
     * outputModes=["text"]. The boot configuration populates these from the
     * registered handler's declared metadata before calling this method.
     */
    public static AgentCard toAgentCard(AgentCardDescriptor d) {
        String version = blankToDefault(d.version(), "0.1.0");
        String endpoint = blankToDefault(d.endpoint(), "/a2a");
        String providerOrg = blankToDefault(d.providerOrganization(), "spring-ai-ascend");
        String providerUrl = d.providerUrl() != null ? d.providerUrl() : "";

        AgentCapabilities capabilities = toCapabilities(d);

        List<String> inputModes = d.defaultInputModes().isEmpty() ? List.of("text") : d.defaultInputModes();
        List<String> outputModes = d.defaultOutputModes().isEmpty() ? List.of("text") : d.defaultOutputModes();

        List<AgentSkill> skills = d.skills().stream()
                .map(A2aAgentCardMapper::toSkill)
                .collect(Collectors.toList());

        Map<String, SecurityScheme> securitySchemes = d.securitySchemes().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toSecurityScheme(e.getValue())));

        List<SecurityRequirement> securityRequirements = d.securityRequirements().stream()
                .map(SecurityRequirement::new)
                .collect(Collectors.toList());

        // The primary supported interface is always JSONRPC at the endpoint path.
        // Additional interfaces from the descriptor are appended after.
        List<AgentInterface> supportedInterfaces = buildInterfaces(endpoint, d.additionalInterfaces());

        List<AgentCardSignature> signatures = d.signatures().stream()
                .map(A2aAgentCardMapper::toSignature)
                .collect(Collectors.toList());

        AgentCard.Builder builder = AgentCard.builder()
                .name(d.name())
                .description(d.description())
                .url(endpoint)
                .version(version)
                .provider(new AgentProvider(providerOrg, providerUrl))
                .capabilities(capabilities)
                .defaultInputModes(inputModes)
                .defaultOutputModes(outputModes)
                .skills(skills)
                .supportedInterfaces(supportedInterfaces)
                .preferredTransport(TransportProtocol.JSONRPC.asString());

        if (!securitySchemes.isEmpty()) {
            builder.securitySchemes(securitySchemes);
        }
        if (!securityRequirements.isEmpty()) {
            builder.securityRequirements(securityRequirements);
        }
        if (d.documentationUrl() != null) {
            builder.documentationUrl(d.documentationUrl());
        }
        if (d.iconUrl() != null) {
            builder.iconUrl(d.iconUrl());
        }
        if (!signatures.isEmpty()) {
            builder.signatures(signatures);
        }

        return builder.build();
    }

    private static AgentCapabilities toCapabilities(AgentCardDescriptor d) {
        if (d.capabilities() == null) {
            // Fail-safe: no capabilities declared means none advertised.
            return AgentCapabilities.builder()
                    .streaming(false)
                    .pushNotifications(false)
                    .extendedAgentCard(false)
                    .build();
        }
        return AgentCapabilities.builder()
                .streaming(d.capabilities().streaming())
                .pushNotifications(d.capabilities().pushNotifications())
                .extendedAgentCard(d.capabilities().extendedAgentCard())
                .build();
    }

    private static AgentSkill toSkill(AgentSkillDescriptor s) {
        return AgentSkill.builder()
                .id(s.id())
                .name(s.name())
                .description(s.description())
                .tags(s.tags())
                .examples(s.examples())
                .inputModes(s.inputModes())
                .outputModes(s.outputModes())
                .build();
    }

    private static SecurityScheme toSecurityScheme(SecuritySchemeDescriptor s) {
        return switch (s.type()) {
            case "apiKey" -> new APIKeySecurityScheme(
                    resolveApiKeyLocation(s.location()), s.name(), s.description());
            case "http" -> new HTTPAuthSecurityScheme(null, s.scheme(), s.description());
            case "mutualTLS" -> new MutualTLSSecurityScheme(s.description());
            default -> new HTTPAuthSecurityScheme(null, s.scheme(), s.description());
        };
    }

    private static APIKeySecurityScheme.Location resolveApiKeyLocation(String location) {
        if (location == null) {
            return APIKeySecurityScheme.Location.HEADER;
        }
        return switch (location.toLowerCase()) {
            case "query" -> APIKeySecurityScheme.Location.QUERY;
            case "cookie" -> APIKeySecurityScheme.Location.COOKIE;
            default -> APIKeySecurityScheme.Location.HEADER;
        };
    }

    private static List<AgentInterface> buildInterfaces(String endpoint,
            List<AgentInterfaceDescriptor> additional) {
        var interfaces = new java.util.ArrayList<AgentInterface>();
        // The 2-arg constructor sets protocolVersion = AgentInterface.CURRENT_PROTOCOL_VERSION automatically.
        interfaces.add(new AgentInterface(TransportProtocol.JSONRPC.asString(), endpoint));
        for (AgentInterfaceDescriptor extra : additional) {
            if (extra.protocolVersion() != null) {
                // 4-arg: (protocolBinding, url, tenant="", protocolVersion) — tenant required non-null.
                interfaces.add(new AgentInterface(extra.protocolBinding(), extra.path(),
                        "", extra.protocolVersion()));
            } else {
                interfaces.add(new AgentInterface(extra.protocolBinding(), extra.path()));
            }
        }
        return List.copyOf(interfaces);
    }

    private static AgentCardSignature toSignature(SignatureDescriptor s) {
        return AgentCardSignature.builder()
                .protectedHeader(s.protectedHeader())
                .signature(s.signature())
                .build();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
