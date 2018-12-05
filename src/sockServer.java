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
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        while (true){

            int readyChannels = selector.selectNow();
            if (readyChannels == 0) continue;
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                try {
                    if (key.isAcceptable()) {
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        socketChannel.configureBlocking(false);
                        addClient(socketChannel);
                        socketChannel.register(selector, SelectionKey.OP_READ);

                    } else if (key.isReadable()) {
                        // There is data to READ!
                        // Read all the clients
                        for (int i = 0; i < clients.size(); i++) {
                            sockClient sockClient = clients.get(i);
                            if (key.channel() == sockClient.client) {
                                sockClient.selector = selector;
                                new Thread(sockClient).start();
                            } else if (key.channel() == sockClient.server) {
                                System.out.println("Succesfull connection from " +
                                        sockClient.server.getLocalAddress().toString().split("/")[1] +
                                        " to " + sockClient.server.getRemoteAddress().toString().split("/")[1]);
                                sockClient.newRemoteData();
                            }
                        }
                    }
                } catch (CancelledKeyException e){};
            }
        }
    }

    private static ArrayList<sockClient> clients = new ArrayList<>();

    private void addClient(SocketChannel socketChannel) throws IOException {
        sockClient client;
        client = new sockClient(socketChannel);
        // No more than 20!
        if(clients.size() <= 20) {
            clients.add(client);
        } else {
            System.out.println("There is no more empty blabla");
        }
    }


    public static void main(String[] args) throws IOException {
        new sockServer();
    }
}
