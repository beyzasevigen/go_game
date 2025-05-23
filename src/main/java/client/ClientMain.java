package client;

/**
 * Entry point of the client application.
 * It opens the start panel and connects to the server.
 */
public class ClientMain {

    public static void main(String[] args) {
        String serverIp = "localhost"; // Server IP address
        int serverPort = 12345;        // Server port number

        StartPanel sp = new StartPanel(serverIp, serverPort);
        sp.setVisible(true);           // Show the game start panel
    }
}
