package app.cleanframe.tv;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Just enough of a WebSocket client (RFC 6455) for Home Assistant's /api/websocket: text messages
 * both ways, binary frames out, ping answered. One thread reads; sends may come from any thread.
 */
final class HaSocket implements Closeable {
    private static final int CONNECT_MS = 5000, READ_MS = 30000;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SecureRandom random = new SecureRandom();

    HaSocket(URI base) throws IOException {
        boolean tls = "https".equalsIgnoreCase(base.getScheme());
        String host = base.getHost();
        int port = base.getPort() != -1 ? base.getPort() : tls ? 443 : 80;
        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), CONNECT_MS);
        if (tls) {
            SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, host, port, true);
            SSLParameters params = secure.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");   // SSLSocket checks no hostname by itself
            secure.setSSLParameters(params);
            secure.startHandshake();
            raw = secure;
        }
        socket = raw;
        socket.setSoTimeout(READ_MS);
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());

        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String request = "GET /api/websocket HTTP/1.1\r\nHost: " + host + (base.getPort() != -1 ? ":" + port : "")
                + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: "
                + Base64.encodeToString(nonce, Base64.NO_WRAP) + "\r\n\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String status = line();
        if (!status.startsWith("HTTP/1.1 101")) throw new IOException("Home Assistant answered " + status);
        while (!line().isEmpty()) { }   // the rest of the handshake's headers
    }

    void sendText(String text) throws IOException { send(0x1, text.getBytes(StandardCharsets.UTF_8)); }

    void sendBinary(byte[] data) throws IOException { send(0x2, data); }

    /** The next text message; blocks. */
    String receive() throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (true) {
            int b0 = read(), b1 = read();
            int opcode = b0 & 0x0f;
            long length = b1 & 0x7f;
            if (length == 126) length = (read() << 8) | read();
            else if (length == 127) { length = 0; for (int i = 0; i < 8; i++) length = (length << 8) | read(); }
            byte[] mask = null;
            if ((b1 & 0x80) != 0) { mask = new byte[4]; for (int i = 0; i < 4; i++) mask[i] = (byte) read(); }
            if (length > 1 << 24) throw new IOException("Message too large");
            byte[] payload = new byte[(int) length];
            for (int i = 0; i < payload.length; i++) payload[i] = (byte) (read() ^ (mask == null ? 0 : mask[i & 3]));
            if (opcode == 0x8) throw new EOFException("Home Assistant closed the connection");
            if (opcode == 0x9) { send(0xA, payload); continue; }
            if (opcode == 0xA) continue;
            message.write(payload, 0, payload.length);
            if ((b0 & 0x80) != 0 && opcode != 0x2) return message.toString("UTF-8");
            if ((b0 & 0x80) != 0) message.reset();   // a binary message: nothing here expects one
        }
    }

    @Override
    public void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    /** One unfragmented frame, masked as every client frame must be. */
    private synchronized void send(int opcode, byte[] payload) throws IOException {
        out.write(0x80 | opcode);
        int n = payload.length;
        if (n < 126) out.write(0x80 | n);
        else if (n <= 0xffff) { out.write(0x80 | 126); out.write(n >> 8); out.write(n); }
        else { out.write(0x80 | 127); for (int i = 7; i >= 0; i--) out.write(i >= 4 ? 0 : n >> (8 * i)); }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        out.write(mask);
        byte[] masked = new byte[n];
        for (int i = 0; i < n; i++) masked[i] = (byte) (payload[i] ^ mask[i & 3]);
        out.write(masked);
        out.flush();
    }

    private int read() throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("Home Assistant closed the connection");
        return b;
    }

    private String line() throws IOException {
        StringBuilder s = new StringBuilder();
        for (int b = read(); b != '\n'; b = read()) if (b != '\r') s.append((char) b);
        return s.toString();
    }
}
