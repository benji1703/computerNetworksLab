import java.io.IOException;

public class Sockspy {
    public static void main(String[] args) {
        // Using ideas and guidance from http://tutorials.jenkov.com/java-nio/index.html
        try {
            new SockServer();
        } catch (IOException e) {
            System.err.println("Can't open a new Server");
        }
    }
}
