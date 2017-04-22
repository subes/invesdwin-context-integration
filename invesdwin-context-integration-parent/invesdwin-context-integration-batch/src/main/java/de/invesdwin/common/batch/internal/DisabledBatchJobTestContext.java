package de.invesdwin.common.batch.internal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.inject.Named;

import de.invesdwin.common.batch.IDisabledBatchContext;

/**
 * To prevent tests from failing when another project references invesdwin-context-integration-batch without having the
 * test dependencies. Since the test resources somehow are still available in the depending module, the initialization
 * fails with a class not found exception of the tasklets in the jobs.
 * 
 * @author subes
 * 
 */
@Named
@Immutable
public class DisabledBatchJobTestContext implements IDisabledBatchContext {

    @Override
    public Set<String> getResourceNames() {
        return new HashSet<String>(Arrays.asList("ctx.batch.test.job.1.xml", "ctx.batch.test.job.2.xml"));
    }

}
