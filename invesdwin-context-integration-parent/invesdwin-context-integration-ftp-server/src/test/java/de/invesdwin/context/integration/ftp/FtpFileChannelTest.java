package de.invesdwin.context.integration.ftp;

import java.net.URI;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import de.invesdwin.context.integration.ftp.server.test.FtpServerTest;
import de.invesdwin.context.test.ATest;
import de.invesdwin.util.assertions.Assertions;
import de.invesdwin.util.error.UnknownArgumentException;

@FtpServerTest
@NotThreadSafe
public class FtpFileChannelTest extends ATest {

    @Inject
    private FtpServerDestinationProvider destinationProvider;

    @Test
    public void test() {
        final URI destination = destinationProvider.getDestination();
        final FtpFileChannel channel = new FtpFileChannel(destination, FtpFileChannelTest.class.getSimpleName());
        channel.setFilename("noexisting");
        channel.connect();
        Assertions.checkNull(channel.read());
        Assertions.checkFalse(channel.exists());
        Assertions.assertThat(channel.size()).isEqualTo(-1);
        channel.createUniqueFile();
        Assertions.checkTrue(channel.exists());
        Assertions.assertThat(channel.size()).isEqualTo(0);
        final String writeStr = "hello world";
        final byte[] write = writeStr.getBytes();
        channel.write(write);
        Assertions.checkTrue(channel.exists());
        Assertions.assertThat(channel.size()).isEqualTo(write.length);
        final byte[] read = channel.read();
        final String readStr = new String(read);
        Assertions.assertThat(readStr).isEqualTo(writeStr);
        channel.delete();
        Assertions.checkNull(channel.read());
        Assertions.checkFalse(channel.exists());
        Assertions.assertThat(channel.size()).isEqualTo(-1);
        channel.write(write);
        Assertions.checkTrue(channel.exists());
        Assertions.assertThat(channel.size()).isEqualTo(write.length);
        final byte[] read2 = channel.read();
        final String readStr2 = new String(read2);
        Assertions.assertThat(readStr2).isEqualTo(writeStr);
        channel.delete();
        channel.close();
    }

    @Test
    public void testRandom() {
        final URI destination = destinationProvider.getDestination();
        final FtpFileChannel channel = new FtpFileChannel(destination, FtpFileChannelTest.class.getSimpleName());
        channel.connect();
        final String writeStr = "hello world";
        final byte[] write = writeStr.getBytes();

        for (int i = 0; i < 20; i++) {
            final int random = RandomUtils.nextInt(0, 6);
            switch (random) {
            case 0:
                log.info("read");
                channel.read();
                break;
            case 1:
                log.info("exists");
                channel.exists();
                break;
            case 2:
                log.info("size");
                channel.size();
                break;
            case 3:
                log.info("createUniqueFile");
                channel.createUniqueFile();
                break;
            case 4:
                log.info("write");
                channel.write(write);
                break;
            case 5:
                log.info("delete");
                channel.delete();
                break;
            default:
                throw UnknownArgumentException.newInstance(int.class, random);
            }
        }

        channel.close();
    }

}
