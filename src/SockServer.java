import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

class SockServer {

    SockServer() throws IOException {

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.socket().setSoTimeout(500);
        serverSocketChannel.configureBlocking(false);
        // Why Use a Selector?
        // The advantage of using just a single thread to handle multiple channels is that you need less threads to
        // handle the channels. Actually, you can use just one thread to handle all of your channels. Switching
        // between threads is expensive for an operating system, and each thread takes up some resources (memory)
        // in the operating system too. Therefore, the less threads you use, the better.
        Selector selector = Selector.open();
        // Register the Channel to "ACCEPT"
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true){
            // selectNow() doesn't block at all. It returns immediately with whatever channels are ready.
            int readyChannels = selector.selectNow();
            if (readyChannels == 0) continue;
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                try {
                    if (!key.isValid()){
                        continue;
                    } else if (key.isAcceptable()) {
                        this.accept(serverSocketChannel, selector);
                    } else if (key.isReadable()) {
                        readAllTheClients(selector, key);
                    }
                } catch (CancelledKeyException e){
                    System.err.println("CancelledKeyException");
                }
            }
        }
    }

    private void readAllTheClients(Selector selector, SelectionKey key) throws IOException {
        Iterator<SockClient> iter = clients.iterator();
        while (iter.hasNext()) {
            SockClient sockClient = iter.next();
            if (key.channel() == sockClient.client) {
                setSelectorAndStartThread(selector, sockClient);
            } else if (key.channel() == sockClient.server) {
                sockClient.reverseWriteData();
            }
        }
    }

    private void setSelectorAndStartThread(Selector selector, SockClient sockClient) {
        sockClient.selector = selector;
        new Thread(sockClient).start();
    }

    private static ArrayList<SockClient> clients = new ArrayList<>();

    private void addClient(SocketChannel socketChannel) throws IOException {
        iterOverClientAndClean();
        SockClient client;
        client = new SockClient(socketChannel);
        // No more than 20!
        if (clients.size() < 20) {
            clients.add(client);
        } else {
            System.out.println("There is no more empty Slots - Retry later.");
        }
    }

    private void iterOverClientAndClean() {
        Iterator<SockClient> iter = clients.iterator();
        while (iter.hasNext()) {
            SockClient client = iter.next();
            removeEmptyClients(iter, client);
        }
    }

    private void removeEmptyClients(Iterator<SockClient> iter, SockClient sockClient) {
        if (sockClient.server != null && sockClient.client != null) {
            if (!sockClient.server.isConnected() && !sockClient.client.isConnected()) {
                iter.remove();
            }
        }
    }

    private void accept(ServerSocketChannel serverSocketChannel, Selector selector) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        addClient(socketChannel);
        socketChannel.register(selector, SelectionKey.OP_READ);
    }

}
