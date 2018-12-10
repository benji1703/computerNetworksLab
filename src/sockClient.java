import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class sockClient implements Runnable {
    Selector selector;
    SocketChannel client;
    SocketChannel server;
    String formatedAuthorization;
    private boolean flag;
    private boolean isFirst;

    @Override
    public void run() {
        try {
            newClientData(selector);
        } catch (IOException ignored) {
        }
    }

    sockClient(SocketChannel c) throws IOException {
        client = c;
        client.configureBlocking(false);
        client.socket().setSoTimeout(500);
        isFirst = true;
        formatedAuthorization = "";
    }

    void newRemoteData() throws IOException {
        writeData(server, client);
    }

    private void writeData(SocketChannel remote, SocketChannel client) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        // If end of data...
        if (remote.read(byteBuffer) == -1 && isFirst) {
            isFirst = false;
            System.out.println("Closing Connection from " +
                    client.getLocalAddress().toString().split("/")[1] +
                    " to " + client.getRemoteAddress().toString().split("/")[1]);
            client.close();
            remote.close();
            return;
        }
        byteBuffer.flip();
        if (isFirst) {
            ByteBuffer clonedByteBuffer = clone(byteBuffer);
            String s = StandardCharsets.UTF_8.decode(clonedByteBuffer).toString();
            if (s.contains("Authorization")) {
                this.formatedAuthorization = manipulateStringAndExtractAuthorization(s);
            }
        }
        client.write(byteBuffer);
    }

    private String manipulateStringAndExtractAuthorization(String s) {
        String authorizationEncoded = "";
        String host = "";

        int startIndexAuthorization = s.indexOf("Authorization: Basic");
        int endIndexAuthorization = s.indexOf("\r\n", startIndexAuthorization);
        if (endIndexAuthorization == -1) {
            return "";
        }

        authorizationEncoded = s.substring(startIndexAuthorization + 21, endIndexAuthorization);
        byte[] decodedBytes = Base64.getDecoder().decode(authorizationEncoded);
        String autorizationDecoded = new String(decodedBytes);

        int startIndexHost = s.indexOf("Host: ");
        int endIndexHost = s.indexOf("\r\n", startIndexHost);
        if (endIndexHost == -1) {
            return "";
        }
        host = s.substring(startIndexHost + 6, endIndexHost);

        return combineHostAndAuthorization(autorizationDecoded, host);
    }

    public String combineHostAndAuthorization (String autorizationDecoded, String host){
        return "http://" + autorizationDecoded + "@" + host +"/";
    }

    public static ByteBuffer clone (ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind(); //copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    private void newClientData(Selector selector) throws IOException {

        if (!flag) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(512);

            if (client.read(byteBuffer) < 1)
                return;
            byteBuffer.flip();

            //  https://www.openssh.com/txt/socks4.protocol

            // Read socks header

            // First Byte is VN
            int ver = byteBuffer.get();
            if (ver != 4) {
                System.err.println("Connection error: while parsing request: Unsupported SOCKS protocol" +
                        "version (got " + ver + ")");
                closeConnectionAfterErr();
                return;
            }

            // Second Byte is CD
            int cmd = byteBuffer.get();
            if (cmd != 1) {
                System.err.println("Connection error: while parsing request: CMD \n");
                closeConnectionAfterErr();
                return;
            }

            // Byte 3 and 4 are for port
            final int port = byteBuffer.getShort();

            // Fetching byte 5 - 8 (using Array of 4 bytes)
            final byte[] ip = new byte[4];
            byteBuffer.get(ip);

            InetAddress remoteAddr = InetAddress.getByAddress(ip);

            // For both CONNECT and BIND operations, the server sets a time limit
            // (2 minutes in current CSTC implementation) for the establishment of its
            // connection with the application server. If the connection is still not
            // establiched when the time limit expires, the server closes its connection
            // to the client and gives up.

            try {
                server = SocketChannel.open();
                server.socket().setSoTimeout(500);
                server.connect(new InetSocketAddress(remoteAddr, port));
            }
            catch (Exception e){
                System.err.println("Connection error: while connecting to destination: connect timed out");
                closeConnectionAfterErr();
                return;
            }

            ByteBuffer out = ByteBuffer.allocate(20);
            // VN is the version of the reply code and should be 0. CD is the result
            // code with one of the following values:
            out.put((byte) 0);
            // 90: request granted
            // 91: request rejected or failed
            out.put((byte) (server.isConnected() ? 0x5a : 0x5b));
            // DSTPORT
            out.putShort((short) port);
            // DSTIP
            out.put(remoteAddr.getAddress());
            out.flip();
            client.write(out);

            if (!server.isConnected())
                throw new IOException("connect failed");

            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_READ);

            flag = true;

        } else {
            writeData(client, server);
        }
    }

    private void closeConnectionAfterErr() throws IOException {
        System.out.println("Closing Connection from " +
                client.getRemoteAddress().toString().split("/")[1]);
        client.close();
    }
}
