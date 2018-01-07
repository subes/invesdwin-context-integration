package de.invesdwin.context.integration.ftp;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.annotation.concurrent.NotThreadSafe;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import de.invesdwin.norva.marker.ISerializableValueObject;
import de.invesdwin.util.assertions.Assertions;
import de.invesdwin.util.lang.UUIDs;

@NotThreadSafe
public class FtpFileChannel implements Closeable, ISerializableValueObject {

    private final URI serverUri;
    private final String directory;
    private String filename;
    private transient FTPClient ftpClient;

    public FtpFileChannel(final URI serverUri, final String directory) {
        this.serverUri = serverUri;
        this.directory = directory;
    }

    public URI getServerUri() {
        return serverUri;
    }

    public String getDirectory() {
        return directory;
    }

    public void setFilename(final String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void createUniqueFilename(final String filenamePrefix, final String filenameSuffix) {
        assertConnected();
        while (true) {
            final String filename = UUIDs.newRandomUUID() + ".channel";
            setFilename(filename);
            if (!exists()) {
                write(new ByteArrayInputStream(newEmptyFileContent()));
                break;
            }
        }
    }

    protected byte[] newEmptyFileContent() {
        return "".getBytes();
    }

    public FTPClient getFtpClient() {
        assertConnected();
        return ftpClient;
    }

    public void connect() {
        try {
            if (ftpClient != null && !ftpClient.isConnected()) {
                close();
            }
            Assertions.checkNull(ftpClient, "Already connected");
            ftpClient = new FTPClient();
            ftpClient.connect(serverUri.getHost(), serverUri.getPort());
            final int reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw new IllegalStateException("FTP server refused connection.");
            }
            if (!ftpClient.login(FtpClientProperties.USERNAME, FtpClientProperties.PASSWORD)) {
                throw new IllegalStateException();
            }
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            //be a bit more firewall friendly
            ftpClient.enterLocalPassiveMode();
            changeDirectory();
            if (filename == null) {
                createUniqueFilename(FtpFileChannel.class.getSimpleName() + "_", ".channel");
            }
        } catch (final Throwable e) {
            close();
            throw new RuntimeException(e);
        }
    }

    /**
     * http://www.codejava.net/java-se/networking/ftp/creating-nested-directory-structure-on-a-ftp-server
     */
    private void changeDirectory() throws IOException {
        final String[] pathElements = directory.split("/");
        if (pathElements != null && pathElements.length > 0) {
            for (final String singleDir : pathElements) {
                final boolean existed = ftpClient.changeWorkingDirectory(singleDir);
                if (!existed) {
                    final boolean created = ftpClient.makeDirectory(singleDir);
                    if (created) {
                        ftpClient.changeWorkingDirectory(singleDir);
                    }
                }
            }
        }
    }

    public boolean isConnected() {
        return ftpClient != null && ftpClient.isConnected();
    }

    public boolean exists() {
        try (InputStream inputStream = ftpClient.retrieveFileStream(filename)) {
            final int returnCode = ftpClient.getReplyCode();
            if (inputStream == null || returnCode == FTPReply.FILE_UNAVAILABLE) {
                return false;
            }
            return true;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertConnected() {
        Assertions.checkNotNull(ftpClient, "Please call connect() first");
        Assertions.checkTrue(ftpClient.isConnected(), "Not connected yet");
    }

    public void write(final InputStream in) {
        assertConnected();
        try (InputStream autoClose = in) {
            if (!ftpClient.storeFile(filename, autoClose)) {
                throw new IllegalStateException("storeFile returned false: " + filename);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream read() {
        assertConnected();
        try {
            return ftpClient.retrieveFileStream(filename);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean delete() {
        assertConnected();
        try {
            return ftpClient.deleteFile(filename);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void close() {
        if (ftpClient != null) {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (final Throwable t) {
                    // do nothing
                }
                try {
                    ftpClient.disconnect();
                } catch (final Throwable t) {
                    // do nothing
                }
            }
            ftpClient = null;
        }
    }

    public OutputStream newOutputStream() {
        assertConnected();
        try {
            return ftpClient.storeFileStream(filename);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

}
