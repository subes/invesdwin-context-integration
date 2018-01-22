package de.invesdwin.context.integration.jppf.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.annotation.concurrent.Immutable;

import org.jppf.comm.interceptor.AbstractNetworkConnectionInterceptor;

import de.invesdwin.context.ContextProperties;
import de.invesdwin.context.integration.jppf.JPPFClientProperties;

/**
 * This network connection inteceptor checks the user name token to be correct for accepting a jppf client. This method
 * is not secure against Man-In-The-Middle attacks but at least provides protection against simple bots that connect to
 * open jppf instances (if there are some out there).
 * 
 * @author subes
 *
 */
@Immutable
public class BasicAuthenticationNetworkConnectionInterceptor extends AbstractNetworkConnectionInterceptor {

    private static final String OK = "OK";

    @Override
    public boolean onAccept(final Socket acceptedSocket) {
        if (acceptedSocket.getLocalPort() == JPPFClientProperties.RECOVERY_SERVER_PORT) {
            return true;
        }
        Integer prevTimeout = null;
        try {
            // set a timeout on read operations and store the previous setting, if any
            prevTimeout = acceptedSocket.getSoTimeout();
            acceptedSocket.setSoTimeout(ContextProperties.DEFAULT_NETWORK_TIMEOUT_MILLIS);

            final InputStream is = acceptedSocket.getInputStream();
            final OutputStream os = acceptedSocket.getOutputStream();
            final String userName = read(is);
            if (!userName.equals(JPPFClientProperties.USERNAMETOKEN_PASSWORD)) {
                // send invalid user response
                write("invalid user name", os);
                return false;
            } else {
                // send ok response
                write(OK, os);
                return true;
            }
        } catch (final EOFException e) {
            //ignore eof, since checks for port availability will lead to this
            return true;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (prevTimeout != null) {
                try {
                    // restore the initial SO_TIMEOUT setting
                    acceptedSocket.setSoTimeout(prevTimeout);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public boolean onConnect(final Socket connectedSocket) {
        if (connectedSocket.getPort() == JPPFClientProperties.RECOVERY_SERVER_PORT) {
            return true;
        }
        Integer prevTimeout = null;
        try {
            // set a timeout on read operations and store the previous setting, if any
            prevTimeout = connectedSocket.getSoTimeout();
            connectedSocket.setSoTimeout(ContextProperties.DEFAULT_NETWORK_TIMEOUT_MILLIS);

            final InputStream is = connectedSocket.getInputStream();
            final OutputStream os = connectedSocket.getOutputStream();
            // send the user name to the server
            write(JPPFClientProperties.USERNAMETOKEN_PASSWORD, os);
            // read the server reponse
            final String response = read(is);
            if (!OK.equals(response)) {
                throw new IllegalStateException("Invalid response from server: " + response);
            } else {
                return true;
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (prevTimeout != null) {
                try {
                    // restore the initial SO_TIMEOUT setting
                    connectedSocket.setSoTimeout(prevTimeout);
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void write(final String message, final OutputStream destination) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream cos = new DataOutputStream(baos)) {
            cos.writeUTF(message);
        }
        final DataOutputStream dos = new DataOutputStream(destination);
        final byte[] bytes = baos.toByteArray();
        dos.writeInt(bytes.length);
        dos.write(bytes);
        dos.flush();
    }

    private String read(final InputStream source) throws Exception {
        final DataInputStream dis = new DataInputStream(source);
        final int len = dis.readInt();
        final byte[] bytes = new byte[len];
        dis.read(bytes);
        try (DataInputStream cis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            final String message = cis.readUTF();
            return message;
        }
    }

}
