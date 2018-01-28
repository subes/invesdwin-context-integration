package de.invesdwin.context.integration.ftp;

import java.io.File;

import javax.annotation.concurrent.NotThreadSafe;

import de.invesdwin.context.integration.retry.ARetryingRunnable;
import de.invesdwin.context.integration.retry.RetryDisabledRuntimeException;
import de.invesdwin.context.integration.retry.RetryLaterRuntimeException;
import de.invesdwin.context.integration.retry.RetryOriginator;
import de.invesdwin.util.assertions.Assertions;
import de.invesdwin.util.concurrent.Executors;
import de.invesdwin.util.concurrent.WrappedExecutorService;

@NotThreadSafe
public class AsyncFtpFileUpload implements Runnable {

    public static final String FINISHED_FILENAME_SUFFIX = ".finished";
    public static final int MAX_TRIES = 3;
    private static final int MAX_PARALLEL_UPLOADS = 2;

    private static final WrappedExecutorService EXECUTOR = Executors
            .newFixedThreadPool(AsyncFtpFileUpload.class.getSimpleName(), MAX_PARALLEL_UPLOADS);

    private final FtpFileChannel channel;
    private final String channelFileName;
    private final File localTempFile;
    private int tries = 0;

    public AsyncFtpFileUpload(final FtpFileChannel channel, final File localTempFile) {
        Assertions.checkNotNull(channel);
        this.channel = channel;
        this.localTempFile = localTempFile;
        this.channelFileName = channel.getFilename();
        Assertions.checkNotNull(channelFileName);
    }

    @Override
    public void run() {
        final Runnable retry = new ARetryingRunnable(
                new RetryOriginator(AsyncFtpFileUpload.class, "run", channel, localTempFile)) {
            @Override
            protected void runRetryable() throws Exception {
                try {
                    cleanupForUpload();
                    EXECUTOR.awaitPendingCount(MAX_PARALLEL_UPLOADS);
                    uploadAsync();
                } catch (final InterruptedException e) {
                    channel.close();
                    throw new RuntimeException(e);
                } catch (final Throwable t) {
                    throw handleRetry(t);
                }
            }

        };
        retry.run();
    }

    private void cleanupForUpload() {
        if (!channel.isConnected()) {
            channel.connect();
        }
        channel.setFilename(channelFileName + FINISHED_FILENAME_SUFFIX);
        channel.delete();
        channel.setFilename(channelFileName);
        channel.delete();
    }

    private void uploadAsync() {
        EXECUTOR.execute(
                new ARetryingRunnable(new RetryOriginator(AsyncFtpFileUpload.class, "upload", channel, localTempFile)) {
                    @Override
                    protected void runRetryable() throws Exception {
                        try {
                            cleanupForUpload();
                            upload();
                            deleteInputFileAutomatically();
                            closeChannelAutomatically();
                        } catch (final Throwable t) {
                            throw handleRetry(t);
                        }
                    }

                });
    }

    protected void upload() {
        channel.setFilename(channelFileName);
        channel.upload(localTempFile);
        channel.setFilename(channelFileName + FINISHED_FILENAME_SUFFIX);
        channel.upload(channel.getEmptyFileContent());
        channel.setFilename(channelFileName);
    }

    private RuntimeException handleRetry(final Throwable t) {
        channel.close();
        tries++;
        if (tries >= MAX_TRIES) {
            return new RetryDisabledRuntimeException(
                    "Aborting upload retry after " + tries + " tries because: " + t.toString(), t);
        } else {
            return new RetryLaterRuntimeException(t);
        }
    }

    protected void deleteInputFileAutomatically() {
        localTempFile.delete();
    }

    protected void closeChannelAutomatically() {
        channel.close();
    }

}
