// http://tutorials.jenkov.com/java-nio/socketchannel.html

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class sockServer {

    private sockServer() throws IOException {

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while(true){

            int readyChannels = selector.select();
            if (readyChannels == 0) continue;
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while(keyIterator.hasNext()) {

                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    addClient(socketChannel);
                    if (socketChannel != null) {
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    }
                } else if (key.isReadable()) {
                    // There is data to READ!
                    // Read all the clients
                    for (int i = 0; i < clients.size(); i++) {
                        sockClient sockClient = clients.get(i);
                        if (key.channel() == sockClient.client) {
                            sockClient.newClientData(selector);
                        } else if (key.channel() == sockClient.server) {
                            System.out.println("Succesfull connection from " +
                                    sockClient.server.getLocalAddress().toString().split("/")[1] +
                                    " to " + sockClient.server.getRemoteAddress().toString().split("/")[1]);
                            sockClient.newRemoteData();
                        }
                    }
                } else if (key.isWritable()) {
                    // a channel is ready for writing
                    System.out.println("4");
                }
                keyIterator.remove();
            }
        }

        // We need to kill on timeout
    }

    private static ArrayList<sockClient> clients = new ArrayList<>();

    private void addClient(SocketChannel socketChannel) throws IOException {
        sockClient client;
        client = new sockClient(socketChannel);
        // No more than 20!
        if(clients.size() <= 20) {
            clients.add(client);
        } else {
            // do nothing
        }
    }


    public static void main(String[] args) throws IOException {
        new sockServer();
    }
}
