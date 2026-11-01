package dev.qaagent.core.testdata;

import java.time.Instant;

public record ProvisionResult(String dataSetId, Instant leaseExpiresAt) {
}
