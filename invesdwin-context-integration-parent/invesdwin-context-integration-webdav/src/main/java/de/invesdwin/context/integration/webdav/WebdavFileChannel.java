package de.invesdwin.context.integration.webdav;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineException;

import de.invesdwin.context.integration.filechannel.IFileChannel;
import de.invesdwin.util.assertions.Assertions;
import de.invesdwin.util.lang.Objects;
import de.invesdwin.util.lang.Strings;
import de.invesdwin.util.lang.UUIDs;
import de.invesdwin.util.lang.uri.URIs;
import de.invesdwin.util.math.Bytes;
import de.invesdwin.util.streams.ADelegateOutputStream;
import de.invesdwin.util.time.fdate.FDate;

@ThreadSafe
public class WebdavFileChannel implements IFileChannel<DavResource> {

    private final String serverUrl;
    private final String directory;
    @GuardedBy("this")
    private String filename;
    @GuardedBy("this")
    private byte[] emptyFileContent = Bytes.EMPTY_ARRAY;
    @GuardedBy("this")
    private transient Sardine webdavClient;

    public WebdavFileChannel(final URI serverUri, final String directory) {
        if (serverUri == null) {
            throw new NullPointerException("serverUri should not be null");
        }
        this.serverUrl = Strings.removeEnd(serverUri.toString(), "/");
        this.directory = Strings.eventuallyAddSuffix(
                Strings.eventuallyAddPrefix(directory.replace("\\", "/").replaceAll("[/]+", "/"), "/"), "/");
    }

    public URI getServerUri() {
        return URIs.asUri(serverUrl);
    }

    @Override
    public String getDirectory() {
        return directory;
    }

    public String getDirectoryUrl() {
        return serverUrl + directory;
    }

    public String getFileUrl() {
        return serverUrl + directory + filename;
    }

    @Override
    public synchronized void setFilename(final String filename) {
        this.filename = filename;
    }

    @Override
    public synchronized String getFilename() {
        if (filename == null) {
            throw new NullPointerException("please call setFilename(...) first");
        }
        return filename;
    }

    @Override
    public synchronized byte[] getEmptyFileContent() {
        return emptyFileContent;
    }

    @Override
    public synchronized void setEmptyFileContent(final byte[] emptyFileContent) {
        this.emptyFileContent = emptyFileContent;
    }

    @Override
    public synchronized void createUniqueFile() {
        createUniqueFile(WebdavFileChannel.class.getSimpleName() + "_", ".channel");
    }

    @Override
    public synchronized void createUniqueFile(final String filenamePrefix, final String filenameSuffix) {
        assertConnected();
        while (true) {
            final String filename = filenamePrefix + UUIDs.newPseudorandomUUID() + filenameSuffix;
            setFilename(filename);
            if (!exists()) {
                upload(new ByteArrayInputStream(getEmptyFileContent()));
                Assertions.checkTrue(exists());
                break;
            }
        }
    }

    public synchronized Sardine getWebdavClient() {
        assertConnected();
        return webdavClient;
    }

    @Override
    public synchronized void connect() {
        try {
            Assertions.checkNull(webdavClient, "Already connected");
            webdavClient = login();
            webdavClient.enablePreemptiveAuthentication(URIs.asUrl(serverUrl));
        } catch (final Throwable e) {
            close();
            throw new RuntimeException(e);
        }
    }

    protected Sardine login() {
        return SardineFactory.begin(WebdavClientProperties.USERNAME, WebdavClientProperties.PASSWORD);
    }

    @Override
    public synchronized boolean isConnected() {
        return webdavClient != null;
    }

    @Override
    public synchronized boolean exists() {
        assertConnected();
        try {
            return webdavClient.exists(getFileUrl());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized long size() {
        final DavResource info = info();
        if (info == null) {
            return -1;
        }
        final Long length = info.getContentLength();
        if (length == null) {
            return -1;
        }
        return length;
    }

    @Override
    public synchronized FDate modified() {
        final DavResource info = info();
        if (info == null) {
            return null;
        }
        final Date modified = info.getModified();
        if (modified == null) {
            return null;
        }
        return new FDate(modified);
    }

    @Override
    public synchronized DavResource info() {
        assertConnected();
        try {
            final List<DavResource> listFiles = webdavClient.list(getFileUrl());
            if (listFiles.size() == 0) {
                return null;
            } else if (listFiles.size() == 1) {
                return listFiles.get(0);
            } else {
                throw new IllegalStateException("More than one result: " + listFiles.size());
            }
        } catch (final SardineException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return null;
            } else {
                throw new RuntimeException(e);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized List<DavResource> list() {
        assertConnected();
        try {
            return webdavClient.list(getDirectoryUrl());
        } catch (final SardineException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return null;
            } else {
                throw new RuntimeException(e);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized List<DavResource> listFiles() {
        final List<DavResource> list = list();
        final List<DavResource> files = new ArrayList<>();
        for (final DavResource file : list) {
            if (!file.isDirectory()) {
                files.add(file);
            }
        }
        return files;
    }

    @Override
    public synchronized List<DavResource> listDirectories() {
        final List<DavResource> list = list();
        final List<DavResource> directories = new ArrayList<>();
        for (final DavResource directory : list) {
            if (directory.isDirectory()) {
                directories.add(directory);
            }
        }
        return directories;
    }

    private synchronized void assertConnected() {
        Assertions.checkNotNull(webdavClient, "Please call connect() first");
    }

    @Override
    public synchronized void upload(final File file) {
        assertConnected();
        try {
            webdavClient.put(getFileUrl(), file, null);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void upload(final byte[] bytes) {
        upload(new ByteArrayInputStream(bytes));
    }

    @Override
    public synchronized void upload(final InputStream input) {
        assertConnected();
        try {
            webdavClient.put(getFileUrl(), input);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized byte[] download() {
        try {
            try (InputStream in = downloadInputStream()) {
                if (in == null) {
                    return null;
                } else {
                    final byte[] bytes = IOUtils.toByteArray(in);
                    return bytes;
                }
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public synchronized void delete() {
        assertConnected();
        try {
            webdavClient.delete(getFileUrl());
        } catch (final SardineException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return;
            } else {
                throw new RuntimeException(e);
            }
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
    public synchronized void close() {
        if (webdavClient != null) {
            try {
                webdavClient.shutdown();
            } catch (final IOException e) {
                //ignore
            }
            webdavClient = null;
        }
    }

    @Override
    public synchronized OutputStream uploadOutputStream() {
        assertConnected();
        return new ADelegateOutputStream() {

            private final File file = getLocalTempFile();

            @Override
            protected OutputStream newDelegate() {
                try {
                    return new BufferedOutputStream(new FileOutputStream(file));
                } catch (final FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                    if (!file.exists()) {
                        //write an empty file
                        FileUtils.write(file, "", Charset.defaultCharset());
                    }
                    upload(file);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    file.delete();
                }
            }
        };
    }

    @Override
    public synchronized File getLocalTempFile() {
        final File directory = new File(WebdavClientProperties.TEMP_DIRECTORY, getDirectory());
        try {
            FileUtils.forceMkdir(directory);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        final File file = new File(directory, getFilename());
        FileUtils.deleteQuietly(file);
        return file;
    }

    @Override
    public synchronized void reconnect() {
        assertConnected();
        close();
        connect();
    }

    @Override
    public synchronized InputStream downloadInputStream() {
        assertConnected();
        try {
            return webdavClient.get(getFileUrl());
        } catch (final SardineException e) {
            if (e.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return null;
            } else {
                throw new RuntimeException(e);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("serverUrl", serverUrl)
                .add("directory", directory)
                .add("filename", filename)
                .toString();
    }

}