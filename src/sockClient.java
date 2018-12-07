import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class sockClient implements Runnable {
    Selector selector;
    SocketChannel client;
    SocketChannel server;
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
    }

    void newRemoteData() throws IOException {
        writeData(server, client);
    }

    private void closeConnectionAfterErr(SocketChannel client) throws IOException {
        System.out.println("Closing Connection from " +
                client.getRemoteAddress().toString().split("/")[1]);
        client.close();
    }

    private void writeData(SocketChannel remote, SocketChannel client) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        // If end of data...
        if (remote.read(buf) == -1 && isFirst) {
            isFirst = false;
            System.out.println("Closing Connection from " +
                    client.getLocalAddress().toString().split("/")[1] +
                    " to " + client.getRemoteAddress().toString().split("/")[1]);
            client.close();
            remote.close();
            return;
        }
        buf.flip();
        client.write(buf);
    }

    private void newClientData(Selector selector) throws IOException {

        if (!flag) {
            ByteBuffer inbuf = ByteBuffer.allocate(512);

            if (client.read(inbuf) < 1)
                return;
            inbuf.flip();

            //  https://www.openssh.com/txt/socks4.protocol

            // Read socks header

            // First Byte is VN
            int ver = inbuf.get();
            if (ver != 4) {
                System.err.println("Connection error: while parsing request: Unsupported SOCKS protocol" +
                        "version (got " + ver + ")");
                closeConnectionAfterErr(client);
                return;
            }

            // Second Byte is CD
            int cmd = inbuf.get();
            if (cmd != 1) {
                System.err.println("Connection error: while parsing request: CMD \n");
                return;
            }

            // Byte 3 and 4 are for port
            final int port = inbuf.getShort();

            // Fetching byte 5 - 8 (using Array of 4 bytes)
            final byte[] ip = new byte[4];
            inbuf.get(ip);

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
                closeConnectionAfterTimeout();
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

    private void closeConnectionAfterTimeout() throws IOException {
        System.out.println("Closing Connection from " +
                client.getRemoteAddress().toString().split("/")[1]);
        client.close();
    }
}
