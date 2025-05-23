package server;

import shared.Message;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * ServerSocketHandler is the main server class responsible for
 * accepting client connections and managing game rooms.
 */
public class ServerSocketHandler {

    private static final int PORT = 12345;
    private static final String[] COLORS = {"Black", "White"};
    private static List<GameRoom> rooms = new ArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                // Assign the new player to an available room
                assignClientToRoom(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Assigns a newly connected client to an available game room
    private static void assignClientToRoom(Socket socket) throws IOException {
        for (GameRoom room : rooms) {
            if (room.hasSpace()) {
                room.addPlayer(socket);
                return;
            }
        }

        // If no room has space, create a new one
        GameRoom newRoom = new GameRoom();
        newRoom.addPlayer(socket);
        rooms.add(newRoom);
    }

    // Inner class representing a game room (holds up to 2 players)
    static class GameRoom {

        private List<ClientHandler> players = new ArrayList<>();

        public boolean hasSpace() {
            return players.size() < 2;
        }

        public void addPlayer(Socket socket) {
            if (players.size() >= 2) {
                return;
            }

            try {
                ClientHandler handler = new ClientHandler(socket, this);
                players.add(handler);
                new Thread(handler).start();
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("❌ Failed to create ClientHandler, player not added.");
                return;
            }

            if (players.size() == 2) {
                ClientHandler p1 = players.get(0);
                ClientHandler p2 = players.get(1);

                if (p1 != null && p2 != null && p1.isAlive() && p2.isAlive()) {
                    p1.setColor(COLORS[0]); // BLACK
                    p1.setPlayerIndex(0);
                    p2.setColor(COLORS[1]); // WHITE
                    p2.setPlayerIndex(1);

                    for (ClientHandler p : players) {
                        p.setPassed(false);
                        System.out.println(">>> Server sending INIT: " + p.getColor());
                        p.sendMessage(new Message("init", p.getColor()));
                    }

                    broadcast(new Message("ready", "Game is ready!"));
                } else {
                    System.out.println("❗ Invalid players or connection lost. Game cannot start.");
                    players.clear(); // Clear the room if it’s broken
                }
            }
        }

        public void removePlayer(ClientHandler handler) {
            players.remove(handler);
            System.out.println("Player removed from room.");

            if (players.isEmpty()) {
                ServerSocketHandler.rooms.remove(this);
                System.out.println("Room is empty. Deleted.");
            } else if (players.size() == 1) {
                ClientHandler remaining = players.get(0);

                // Check if remaining player's connection is alive
                if (remaining != null && remaining.isAlive()) {
                    remaining.sendMessage(new Message("waiting", "Opponent disconnected. Waiting for a new player..."));
                } else {
                    System.out.println("❗ Remaining player's connection is lost. Cleaning up room.");
                    players.clear();
                    ServerSocketHandler.rooms.remove(this);
                }
            } else {
                // Should never happen, but clean up anyway
                System.out.println("⚠ Warning: Unexpected number of players in room.");
                players.clear();
                ServerSocketHandler.rooms.remove(this);
            }
        }

        // Sends a message to all players in the room
        public void broadcast(Message msg) {
            for (ClientHandler ch : players) {
                ch.sendMessage(msg);
            }
        }

        // Checks if both players passed their turn (end condition)
        public void checkEndCondition() {
            if (players.size() == 2) {
                boolean p0 = players.get(0).hasPassed();
                boolean p1 = players.get(1).hasPassed();
                System.out.println(">>> Room check. Passed: " + p0 + ", " + p1);
                if (p0 && p1) {
                    broadcast(new Message("end", ""));
                }
            }
        }

        // Returns the opponent of the given player
        public ClientHandler getOpponent(ClientHandler me) {
            for (ClientHandler p : players) {
                if (p != me) {
                    return p;
                }
            }
            return null;
        }
    }

    // Represents a connected client and manages their messages
    static class ClientHandler implements Runnable {

        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private GameRoom room;
        private String color;
        private int playerIndex;
        private boolean passed = false;

        public ClientHandler(Socket socket, GameRoom room) throws IOException {
            this.socket = socket;
            this.room = room;
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());
        }

        public boolean isAlive() {
            return socket != null && !socket.isClosed() && socket.isConnected();
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getColor() {
            return color;
        }

        public boolean hasPassed() {
            return passed;
        }

        public void setPassed(boolean p) {
            passed = p;
        }

        public void setPlayerIndex(int index) {
            this.playerIndex = index;
        }

        // Sends a message to this client
        public void sendMessage(Message msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                Object obj;
                while ((obj = in.readObject()) != null) {
                    if (obj instanceof Message msg) {
                        System.out.println("Client message: " + msg.type + " - " + msg.payload);

                        if (msg.type.equals("pass")) {
                            this.setPassed(true);
                            ClientHandler opponent = room.getOpponent(this);
                            if (opponent != null) {
                                opponent.sendMessage(msg); // Inform opponent
                            }
                            room.checkEndCondition();
                            continue;
                        }

                        if (msg.type.equals("exit")) {
                            System.out.println("Player exited the game (exit message).");
                            break;
                        }

                        // Forward other messages to opponent
                        ClientHandler opponent = room.getOpponent(this);
                        if (opponent != null) {
                            opponent.sendMessage(msg);
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("A client connection was lost.");
            } finally {
                try {
                    Thread.sleep(500); // short delay before cleanup
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }

                try {
                    socket.close();
                    room.removePlayer(this);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
