package de.invesdwin.common.integration.jms;

import java.util.Arrays;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

import org.springframework.core.io.ClassPathResource;

import de.invesdwin.context.beans.init.locations.IContextLocation;
import de.invesdwin.context.beans.init.locations.PositionedResource;

@ThreadSafe
public class JmsIntegrationTestContextLocation implements IContextLocation {

    @Override
    public List<PositionedResource> getContextResources() {
        return Arrays.asList(PositionedResource.of(new ClassPathResource("/META-INF/ctx.integration.jms.test.xml")));
    }

}
