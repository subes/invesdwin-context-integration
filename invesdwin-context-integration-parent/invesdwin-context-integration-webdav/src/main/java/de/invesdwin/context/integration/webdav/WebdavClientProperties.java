package de.invesdwin.context.integration.webdav;

import java.io.File;

import javax.annotation.concurrent.Immutable;

import de.invesdwin.context.ContextProperties;
import de.invesdwin.context.system.properties.SystemProperties;
import de.invesdwin.util.time.duration.Duration;

@Immutable
public final class WebdavClientProperties {

    public static final File TEMP_DIRECTORY = new File(ContextProperties.TEMP_DIRECTORY,
            WebdavFileChannel.class.getSimpleName());
    public static final Duration PURGE_TEMP_FILES_OLDER_THAN_DURATION = Duration.ONE_DAY;
    public static final String SERVICE_NAME = "invesdwin-context-integration-webdav-server";
    public static final String USERNAME;
    public static final String PASSWORD;

    static {
        final SystemProperties systemProperties = new SystemProperties(WebdavClientProperties.class);
        USERNAME = systemProperties.getString("USERNAME");
        PASSWORD = systemProperties.getStringWithSecurityWarning("PASSWORD", "invesdwin");
    }

    private WebdavClientProperties() {}

}
