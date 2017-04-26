package de.invesdwin.context.integration.amqp;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Named;

import org.junit.Test;

import de.invesdwin.context.test.ATest;
import de.invesdwin.context.test.TestContext;
import de.invesdwin.util.assertions.Assertions;

@ThreadSafe
public class AmqpIntegrationTest extends ATest {

    public static final String HELLO_WORLD = "Hello World";

    @Inject
    @Named("amqpIntegrationTestServiceGateway")
    private IAmqpIntegrationTestService gateway;
    @Inject
    private AmqpIntegrationTestService service;

    @Override
    public void setUpContext(final TestContext ctx) throws Exception {
        super.setUpContext(ctx);
        ctx.activate(AmqpIntegrationTestContextLocation.class);
    }

    @Test(timeout = 3000)
    public void testHelloWorld() throws InterruptedException {
        gateway.helloWorld(HELLO_WORLD);
        service.waitForProcessedRequest();
    }

    @Test(timeout = 3000)
    public void testHelloWorldWithAnswer() throws InterruptedException {
        final String response = gateway.helloWorldWithAnswer(HELLO_WORLD);
        Assertions.assertThat(response).isEqualTo(HELLO_WORLD);
        log.info(response);
        service.waitForProcessedRequest(); //in fact useless here, but still checking
    }

}