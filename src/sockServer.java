// http://tutorials.jenkov.com/java-nio/socketchannel.html

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class sockServer {

    private sockServer() throws IOException {

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.socket().setSoTimeout(500);
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true){

            int readyChannels = selector.select();
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
                        readAndCleanAllTheClients(selector, key);
                    }
                } catch (CancelledKeyException e){
                    System.err.println("CancelledKeyException");
                }
            }
        }
    }

    private void readAndCleanAllTheClients(Selector selector, SelectionKey key) throws IOException {
        Iterator<sockClient> iter = clients.iterator();
        while (iter.hasNext()) {
            sockClient sockClient = iter.next();
//            removeEmptyClients(iter, sockClient);
            if (key.channel() == sockClient.client) {
                setSelectorAndStartThread(selector, sockClient);
            } else if (key.channel() == sockClient.server) {
                printSuccessfulConnection(sockClient);
                sockClient.newRemoteData();
            }
        }
    }

    private void printSuccessfulConnection(sockClient sockClient) throws IOException {
        System.out.println("Successful connection from " +
                sockClient.server.getLocalAddress().toString().split("/")[1] +
                " to " + sockClient.server.getRemoteAddress().toString().split("/")[1]);
    }

    private void setSelectorAndStartThread(Selector selector, sockClient sockClient) {
        sockClient.selector = selector;
        new Thread(sockClient).start();
    }

    private static ArrayList<sockClient> clients = new ArrayList<>();

    private void addClient(SocketChannel socketChannel) throws IOException {
        iterOverClientAndClean();
        sockClient client;
        client = new sockClient(socketChannel);
        // No more than 20!
        if(clients.size() < 20) {
            clients.add(client);
        } else {
            System.out.println("There is no more empty Slots - Retry later.");
        }
    }

    private void iterOverClientAndClean() {
        Iterator<sockClient> iter = clients.iterator();
        while (iter.hasNext()) {
            sockClient client = iter.next();
            removeEmptyClients(iter, client);
        }
    }

    private void removeEmptyClients(Iterator<sockClient> iter, sockClient sockClient) {
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

    public static void main(String[] args) throws IOException {
        new sockServer();
    }
}
