package server;

import shared.Message;
import java.io.*;
import java.net.*;
import java.util.*;

public class ServerSocketHandler {

    private static final int PORT = 12345;
    private static final String[] COLORS = {"Black", "White"};
    private static List<GameRoom> rooms = new ArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Sunucu başlatıldı...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Yeni client bağlandı: " + clientSocket.getInetAddress());

                // Yeni oyuncuyu uygun bir oyun odasına yerleştir
                assignClientToRoom(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Yeni gelen client’ı uygun bir odaya ata
    private static void assignClientToRoom(Socket socket) throws IOException {
        for (GameRoom room : rooms) {
            if (room.hasSpace()) {
                room.addPlayer(socket);
                return;
            }
        }

        // Eğer boş oda yoksa yeni oda oluştur
        GameRoom newRoom = new GameRoom();
        newRoom.addPlayer(socket);
        rooms.add(newRoom);
    }

    // İç sınıf: GameRoom (her biri 2 oyuncu barındırır)
    static class GameRoom {

        private List<ClientHandler> players = new ArrayList<>();

        public boolean hasSpace() {
            return players.size() < 2;
        }

        public void addPlayer(Socket socket) throws IOException {
            if (players.size() >= 2) {
                return;
            }

            ClientHandler handler = new ClientHandler(socket, this);
            players.add(handler);

            new Thread(handler).start();

            if (players.size() == 2) {
                // 🔁 Renkleri sıfırdan ata
                players.get(0).setColor(COLORS[0]); // BLACK
                players.get(0).setPlayerIndex(0);
                players.get(1).setColor(COLORS[1]); // WHITE
                players.get(1).setPlayerIndex(1);

                // Herkese yeni rengi gönder
                for (ClientHandler p : players) {
                    p.setPassed(false);
                    System.out.println(">>> Sunucu INIT gönderiyor: " + p.getColor());
                    p.sendMessage(new Message("init", p.getColor()));
                }

                broadcast(new Message("ready", "Game is ready!"));
            }
        }

        public void removePlayer(ClientHandler handler) {
            players.remove(handler);
            System.out.println("Oyuncu odadan ayrıldı.");

            if (players.isEmpty()) {
                ServerSocketHandler.rooms.remove(this); // ❗ Oda boşsa temizle
                System.out.println("Oda boş kaldı, silindi.");
            } else {
                players.get(0).sendMessage(new Message("waiting", "Rakip bağlantıyı kaybetti. Yeni oyuncu bekleniyor..."));
            }
        }

        public void broadcast(Message msg) {
            for (ClientHandler ch : players) {
                ch.sendMessage(msg);
            }
        }

        public void checkEndCondition() {
            if (players.size() == 2) {
                boolean p0 = players.get(0).hasPassed();
                boolean p1 = players.get(1).hasPassed();
                System.out.println(">>> Oda kontrolü. Passed: " + p0 + ", " + p1);
                if (p0 && p1) {
                    broadcast(new Message("end", ""));
                }
            }
        }

        public ClientHandler getOpponent(ClientHandler me) {
            for (ClientHandler p : players) {
                if (p != me) {
                    return p;
                }
            }
            return null;
        }
    }

    // ClientHandler artık bağlı olduğu oda bilgisine sahip
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
                        System.out.println("Client mesajı: " + msg.type + " - " + msg.payload);

                        if (msg.type.equals("pass")) {
                            this.setPassed(true);
                            ClientHandler opponent = room.getOpponent(this);
                            if (opponent != null) {
                                opponent.sendMessage(msg); // rakibe bildir
                            }
                            room.checkEndCondition();
                            continue;
                        }
                        if (msg.type.equals("exit")) {
                            System.out.println("Oyuncu oyundan ayrıldı (exit mesajı).");
                            break;
                        }

                        // Diğer mesajları rakibe gönder
                        ClientHandler opponent = room.getOpponent(this);
                        if (opponent != null) {
                            opponent.sendMessage(msg);
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Bir client bağlantısı kesildi.");
            } finally {
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
