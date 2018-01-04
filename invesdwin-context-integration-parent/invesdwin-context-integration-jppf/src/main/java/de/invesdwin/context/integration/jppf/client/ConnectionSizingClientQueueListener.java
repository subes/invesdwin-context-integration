package de.invesdwin.context.integration.jppf.client;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import org.jppf.client.JPPFConnectionPool;
import org.jppf.client.event.ClientQueueEvent;
import org.jppf.client.event.ClientQueueListener;

import de.invesdwin.context.integration.jppf.JPPFClientProperties;
import de.invesdwin.util.concurrent.Executors;
import de.invesdwin.util.concurrent.WrappedScheduledExecutorService;
import de.invesdwin.util.time.duration.Duration;
import de.invesdwin.util.time.fdate.FTimeUnit;

/**
 * http://www.jppf.org/doc/5.2/index.php?title=Notifications_of_client_job_queue_events
 */
@ThreadSafe
public class ConnectionSizingClientQueueListener implements ClientQueueListener {

    private static final WrappedScheduledExecutorService SCHEDULER = Executors
            .newScheduledThreadPool(ConnectionSizingClientQueueListener.class.getSimpleName() + "_REAPER", 1);
    //reap connections only when they are not used after a while
    private static final Duration REAPER_DELAY = Duration.ONE_MINUTE;

    @GuardedBy("this")
    private int activeJobsCount = 0;

    @Override
    public synchronized void jobAdded(final ClientQueueEvent event) {
        activeJobsCount++;
        final int newPoolSize = Math.max(JPPFClientProperties.IO_CHANNELS, activeJobsCount);
        // grow the connection pools by one
        if (newPoolSize > event.getClient().getAllConnectionsCount()) {
            for (final JPPFConnectionPool pool : event.getClient().getConnectionPools()) {
                pool.setSize(newPoolSize);
            }
        }
    }

    @Override
    public synchronized void jobRemoved(final ClientQueueEvent event) {
        activeJobsCount--;
        final int newPoolSize = Math.max(JPPFClientProperties.IO_CHANNELS, activeJobsCount);
        SCHEDULER.schedule(new Runnable() {
            @Override
            public void run() {
                // shrink the connection pools by one
                if (newPoolSize < event.getClient().getAllConnectionsCount()) {
                    for (final JPPFConnectionPool pool : event.getClient().getConnectionPools()) {
                        pool.setSize(newPoolSize);
                    }
                }
            }
        }, REAPER_DELAY.longValue(FTimeUnit.MILLISECONDS), FTimeUnit.MILLISECONDS.timeUnitValue());
    }

}