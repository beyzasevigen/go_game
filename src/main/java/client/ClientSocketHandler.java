package client;

import shared.Message;

import java.io.*;
import java.net.*;

/**
 * Handles the connection between the client and the game server.
 * Provides methods to send and receive messages over the socket.
 */
public class ClientSocketHandler {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Tries to connect to the server with retry logic (max 5 times)
    public ClientSocketHandler(String serverIp, int port) throws IOException {
        int attempts = 0;
        while (attempts < 5) {
            try {
                socket = new Socket(serverIp, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                return;
            } catch (IOException e) {
                attempts++;
                System.out.println("Connection attempt failed. Retrying...");
                try {
                    Thread.sleep(1000); // wait 1 second before trying again
                } catch (InterruptedException ignored) {
                }
            }
        }
        throw new IOException("Unable to connect to server.");
    }

    // Sends a message to the server
    public void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
    }

    // Reads a message from the server
    public Message readMessage() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    // Closes all streams and the socket connection
    public void close() throws IOException {
        out.close();
        in.close();
        socket.close();
    }
}
