import java.io.IOException;

public class Sockspy {
    public static void main(String[] args) {
        try {
            new sockServer();
        } catch (IOException e) {
            System.err.println("Can't open a new Server");
        }
    }
}
