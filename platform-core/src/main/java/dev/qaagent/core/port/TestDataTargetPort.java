package dev.qaagent.core.port;

import dev.qaagent.core.testdata.ProvisionResult;
import dev.qaagent.core.testdata.TestDataSet;

public interface TestDataTargetPort {

    ProvisionResult provision(TestDataSet set);

    void release(String dataSetId);
}
