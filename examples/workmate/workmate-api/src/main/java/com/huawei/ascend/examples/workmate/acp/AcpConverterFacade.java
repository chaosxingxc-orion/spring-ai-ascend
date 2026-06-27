package com.huawei.ascend.examples.workmate.acp;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AcpConverterFacade {

    private final AcpOutboundConverter outboundConverter = new AcpOutboundConverter();
    private final AcpInboundConverter inboundConverter = new AcpInboundConverter();

    public List<Map<String, Object>> toAcpEventLog(List<Map<String, Object>> eventLog) {
        return outboundConverter.convertEventLog(eventLog);
    }

    public RunEventDraft fromAcpUpdate(Map<String, Object> update) {
        return inboundConverter.convert(update);
    }

    /** Converts an ACP stream with chunk merging (Phase 2). */
    public List<RunEventDraft> fromAcpStream(List<Map<String, Object>> updates) {
        return new AcpMessageAccumulator().ingestAll(updates);
    }
}
