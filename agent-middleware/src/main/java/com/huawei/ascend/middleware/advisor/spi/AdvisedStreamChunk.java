package com.huawei.ascend.middleware.advisor.spi;

import java.util.Objects;

/**
 * Same-package streaming advisor chunk vocabulary.
 *
 * <p>Authority: ADR-0132.
 */
public sealed interface AdvisedStreamChunk
        permits AdvisedStreamChunk.ContentDelta,
                AdvisedStreamChunk.ToolCallDelta,
                AdvisedStreamChunk.Complete {

    record ContentDelta(String deltaText) implements AdvisedStreamChunk {
        public ContentDelta {
            Objects.requireNonNull(deltaText, "deltaText");
        }
    }

    record ToolCallDelta(String callId, String skillKey, String argumentsDelta)
            implements AdvisedStreamChunk {
        public ToolCallDelta {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(skillKey, "skillKey");
            Objects.requireNonNull(argumentsDelta, "argumentsDelta");
        }
    }

    record Complete(AdvisedResponse finalResponse) implements AdvisedStreamChunk {
        public Complete {
            Objects.requireNonNull(finalResponse, "finalResponse");
        }
    }
}
