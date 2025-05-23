package client;

import shared.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class GamePanel extends JFrame {

    private static final int SIZE = 19;
    private static final int CELL_SIZE = 30;
    private char[][] board = new char[SIZE][SIZE];
    private char[][] previousBoard = new char[SIZE][SIZE];
    private boolean iPassed;
    private boolean opponentPassed;
    private ClientSocketHandler socketHandler;
    private boolean isMyTurn = false;
    private char myColor;
    private Point lastKoPosition = null;
    private List<Point> lastKoCaptured = new ArrayList<>();
    private int capturedByBlack = 0; // Siyahın aldığı beyaz taş sayısı
    private int capturedByWhite = 0; // Beyazın aldığı siyah taş sayısı
    private JDialog waitingDialog = null;
    private String serverIp;
    private int serverPort;
    private boolean initialized = false;
    private boolean isGameOver = false;
    private boolean userClosedWindow = false;

    public GamePanel(String serverIp, int serverPort, ClientSocketHandler handler, String colorPayload) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.socketHandler = handler;
        this.myColor = colorPayload.equals("Black") ? 'B' : 'W';
        this.isMyTurn = (myColor == 'B');
        this.initialized = true;

        setTitle("Go Game - Game Screen");
        setSize(SIZE * CELL_SIZE + 50, SIZE * CELL_SIZE + 70);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
            }
        };
        panel.setPreferredSize(new Dimension(SIZE * CELL_SIZE, SIZE * CELL_SIZE));
        panel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!initialized) {
                    System.out.println(">>> Tıklama reddedildi: INIT daha gelmedi.");
                    return;
                }

                if (!isMyTurn) {
                    System.out.println(">>> Tıklama reddedildi. isMyTurn: " + isMyTurn + ", renk: " + myColor);
                    JOptionPane.showMessageDialog(GamePanel.this, "Sıra sende değil!");
                    return;
                }

                int row = e.getY() / CELL_SIZE;
                int col = e.getX() / CELL_SIZE;

                List<Point> removedStones = new ArrayList<>();
                if (placeStone(row, col, removedStones)) {
                    sendMove(row, col, myColor, removedStones);
                    isMyTurn = false;
                    panel.repaint();
                }
            }
        });
        add(panel);

        JButton passButton = new JButton("Pas Geç");
        passButton.addActionListener(e -> {
            if (!isMyTurn) {
                JOptionPane.showMessageDialog(this, "Sıra sende değil, pas geçemezsin!");
                return;
            }

            iPassed = true;

            try {
                socketHandler.sendMessage(new Message("pass", String.valueOf(myColor)));
                isMyTurn = false;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Pas bilgisi gönderilemedi.");

            }
        });
        add(passButton, BorderLayout.SOUTH);

        new Thread(() -> {
            while (true) {
                try {
                    Message msg = socketHandler.readMessage();
                    System.out.println("Sunucudan gelen mesaj: " + msg.type + " - " + msg.payload);

                    switch (msg.type) {
                        case "init":
                            myColor = msg.payload.equals("Black") ? 'B' : 'W';
                            isMyTurn = (myColor == 'B');
                            initialized = true;
                            System.out.println(">>> INIT geldi - Renk: " + myColor + " | isMyTurn: " + isMyTurn);
                            resetGame();
                            break;
                        case "waiting":
                            SwingUtilities.invokeLater(() -> {
                                showWaitingDialog(msg.payload);
                            });
                            break;
                        case "ready":
                            SwingUtilities.invokeLater(() -> {
                                if (waitingDialog != null) {
                                    waitingDialog.dispose();
                                    waitingDialog = null;
                                }
                                resetGame();
                                repaint();
                            });
                            break;
                        case "move":
                            handleIncomingMove(msg.payload);
                            break;
                        case "pass":
                            handleOpponentPass();  // sadece rakibin pas geçişini işler
                            break;
                        case "end":
                            System.out.println("END mesajı geldi, oyun kapanıyor.");

                            isGameOver = true; // ✅ Artık bağlantı koparsa uyarı gösterme

                            double[] scores = countScoreWithKomi(capturedByBlack, capturedByWhite);
                            double blackScore = scores[0];
                            double whiteScore = scores[1];

                            SwingUtilities.invokeLater(() -> {
                                new EndPanel(blackScore, whiteScore);
                                dispose();
                            });

                            new Thread(() -> {
                                try {
                                    Thread.sleep(1000);
                                    socketHandler.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();
                            break;

                        default:
                            System.out.println("Bilinmeyen mesaj tipi: " + msg.type);
                    }
                } catch (Exception e) {
                    System.out.println("Mesaj okuma hatası: " + e.getMessage());

                    if (!isGameOver && !userClosedWindow) {
                        SwingUtilities.invokeLater(()
                                -> JOptionPane.showMessageDialog(this, "Rakibin bağlantısı kesildi.")
                        );
                    } else {
                        System.out.println("Oyun bitti veya pencereyi kullanıcı kapattı, uyarı gösterilmiyor.");
                    }

                    break;
                }

            }
        }).start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                userClosedWindow = true; // ✅ pencereyi kullanıcı kapattıysa
                try {
                    if (socketHandler != null) {
                        socketHandler.sendMessage(new Message("exit", ""));
                        socketHandler.close();
                        System.out.println("Client bağlantısı kapatıldı.");
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

    }

    private void handleIncomingMove(String payload) {
        String[] parts = payload.split(";");
        String[] moveParts = parts[0].split(",");
        int row = Integer.parseInt(moveParts[0]);
        int col = Integer.parseInt(moveParts[1]);
        char color = moveParts[2].charAt(0);
        board[row][col] = color;

        if (parts.length > 1 && parts[1].startsWith("removed:")) {
            String[] removedParts = parts[1].substring(8).split("\\|");
            for (String pos : removedParts) {
                String[] rc = pos.split(",");
                int r = Integer.parseInt(rc[0]);
                int c = Integer.parseInt(rc[1]);
                board[r][c] = '\0';
            }
        }

        previousBoard = copyBoard(board);
        isMyTurn = true;
        iPassed = false;
        repaint();
    }

    private void drawBoard(Graphics g) {
        g.setColor(Color.BLACK);
        for (int i = 0; i < SIZE; i++) {
            g.drawLine(CELL_SIZE / 2, CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2 + (SIZE - 1) * CELL_SIZE, CELL_SIZE / 2 + i * CELL_SIZE);
            g.drawLine(CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2, CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2 + (SIZE - 1) * CELL_SIZE);
        }

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 'B') {
                    g.setColor(Color.BLACK);
                    g.fillOval(j * CELL_SIZE + 5, i * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                } else if (board[i][j] == 'W') {
                    g.setColor(Color.WHITE);
                    g.fillOval(j * CELL_SIZE + 5, i * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                    g.setColor(Color.BLACK);
                    g.drawOval(j * CELL_SIZE + 5, i * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                }
            }
        }
    }

    private void resetGame() {
        board = new char[SIZE][SIZE];
        previousBoard = new char[SIZE][SIZE];
        iPassed = false;
        opponentPassed = false;
        // isMyTurn = (myColor == 'B'); // burada sıra sıfırlanır
        capturedByBlack = 0;
        capturedByWhite = 0;
        lastKoPosition = null;
        lastKoCaptured.clear();
        //repaint();
    }

    private void showWaitingDialog(String message) {
        if (waitingDialog != null && waitingDialog.isVisible()) {
            return;
        }

        waitingDialog = new JDialog(this, "Bekleniyor...", true);
        waitingDialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("<html><center>" + message + "<br>Lütfen bekleyin...</center></html>", SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.PLAIN, 14));
        waitingDialog.add(label, BorderLayout.CENTER);

        // Ana Menüye Dön butonu
        JButton backButton = new JButton("Ana Menüye Dön");
        backButton.addActionListener(e -> {
            try {
                socketHandler.sendMessage(new Message("exit", "")); // Sunucuya çıkış bildir
                socketHandler.close(); // Bağlantıyı kapat
                System.out.println("Ana Menüye dönüldü, bağlantı kapatıldı.");
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            waitingDialog.dispose();  // Bekleme penceresini kapat
            this.dispose();           // GamePanel'i kapat

            SwingUtilities.invokeLater(() -> new StartPanel(serverIp, serverPort).setVisible(true));
        });

        waitingDialog.add(backButton, BorderLayout.SOUTH);

        waitingDialog.setSize(300, 150);
        waitingDialog.setLocationRelativeTo(this);
        waitingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        waitingDialog.setResizable(false);

        // Arkaplanda göster
        new Thread(() -> waitingDialog.setVisible(true)).start();
    }

    private boolean placeStone(int row, int col, List<Point> removed) {
        if (!isValid(row, col) || board[row][col] != '\0') {
            return false;
        }

        char currentColor = myColor;
        char opponentColor = (myColor == 'B') ? 'W' : 'B';

        // 1. Taş yerleştir (geçici olarak)
        board[row][col] = currentColor;

        // 2. Rakip taşlarını kontrol et
        for (int[] dir : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            if (isValid(newRow, newCol) && board[newRow][newCol] == opponentColor) {
                boolean[][] visited = new boolean[SIZE][SIZE];
                if (!hasLiberty(newRow, newCol, opponentColor, visited)) {
                    collectRemoved(visited, removed);
                }
            }
        }

        // KO kuralı kontrolü: sadece karşıdan alınan taş 1 ise kontrol et
        if (lastKoPosition != null && removed.size() == 1) {
            Point last = lastKoCaptured.get(0);
            if (row == lastKoPosition.x && col == lastKoPosition.y
                    && removed.get(0).x == last.x && removed.get(0).y == last.y) {
                // Taş geri alınmalı
                board[row][col] = '\0';
                removed.clear();
                JOptionPane.showMessageDialog(this, "Ko kuralı: Bu hamleye izin verilmiyor.");
                return false;
            }
        }

        // 3. Eğer rakip taşları kaldırılacaksa uygula
        if (!removed.isEmpty()) {
            for (Point p : removed) {
                board[p.x][p.y] = '\0';
            }
        }

        // 4. Kendi taşının özgürlüğü var mı kontrol et
        boolean[][] selfVisited = new boolean[SIZE][SIZE];
        if (!hasLiberty(row, col, currentColor, selfVisited)) {
            board[row][col] = '\0';
            for (Point p : removed) {
                board[p.x][p.y] = opponentColor; // geri yükle
            }
            removed.clear();
            return false;
        }

        // 5. KO durumu güncelle
        if (removed.size() == 1) {
            lastKoPosition = new Point(row, col);
            lastKoCaptured = new ArrayList<>(removed);
        } else {
            lastKoPosition = null;
            lastKoCaptured.clear();
        }

        // 6. Sayacı güncelle
        if (myColor == 'B') {
            capturedByBlack += removed.size();
        } else {
            capturedByWhite += removed.size();
        }

        return true;
    }

    private void collectRemoved(boolean[][] visited, List<Point> list) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (visited[i][j]) {
                    list.add(new Point(i, j));
                }
            }
        }
    }

//    private void removeStones(boolean[][] visited) {
//        for (int i = 0; i < SIZE; i++) {
//            for (int j = 0; j < SIZE; j++) {
//                if (visited[i][j]) {
//                    board[i][j] = '\0';
//                }
//            }
//        }
//    }
    private boolean hasLiberty(int row, int col, char color, boolean[][] visited) {
        if (!isValid(row, col) || visited[row][col] || board[row][col] != color) {
            return false;
        }

        visited[row][col] = true;

        for (int[] dir : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            if (isValid(newRow, newCol)) {
                if (board[newRow][newCol] == '\0') {
                    return true;
                }
                if (board[newRow][newCol] == color && !visited[newRow][newCol]) {
                    if (hasLiberty(newRow, newCol, color, visited)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isValid(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private void sendMove(int row, int col, char color, List<Point> removed) {
        StringBuilder payload = new StringBuilder(row + "," + col + "," + color);
        if (!removed.isEmpty()) {
            payload.append(";removed:");
            for (Point p : removed) {
                payload.append(p.x).append(",").append(p.y).append("|");
            }
            payload.setLength(payload.length() - 1); // sondaki |
        }
        try {
            socketHandler.sendMessage(new Message("move", payload.toString()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Hamle gönderilemedi.");
        }
    }

    public void handleOpponentPass() {
        opponentPassed = true;

        if (!iPassed) {
            JOptionPane.showMessageDialog(this, "Rakip pas geçti. Sıra sende.");
            isMyTurn = true;
            iPassed = false;
        }
        // Eğer iPassed == true ise zaten "end" mesajı birazdan gelecek, hiçbir şey yapma
    }

    private double[] countScoreWithKomi(int capturedByBlack, int capturedByWhite) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        double black = 0, white = 0;
        double komi = 6.5;

        // 1. Tahtadaki mevcut taşları say
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] == 'B') {
                    black++;
                } else if (board[row][col] == 'W') {
                    white++;
                }
            }
        }

        // 2. Boş bölgeleri tara ve çevreleyen renge göre puan ekle
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] == '\0' && !visited[row][col]) {
                    Set<Character> surrounding = new HashSet<>();
                    List<Point> region = new ArrayList<>();
                    boolean isSurrounded = floodFillRegion(row, col, visited, region, surrounding);

                    if (isSurrounded && surrounding.size() == 1) {
                        char owner = surrounding.iterator().next();
                        if (owner == 'B') {
                            black += region.size();
                        } else if (owner == 'W') {
                            white += region.size();
                        }
                    }
                }
            }
        }

        // 3. Komi ekle
        white += komi;
        return new double[]{black, white};
    }
// floodFill metodu: boş bölgeyi bul ve etrafındaki taş renklerini topla

    private boolean floodFillRegion(int r, int c, boolean[][] visited, List<Point> region, Set<Character> surrounding) {
        boolean surrounded = true;
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(r, c));
        visited[r][c] = true;

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            region.add(p);

            int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] d : dirs) {
                int nr = p.x + d[0];
                int nc = p.y + d[1];

                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) {
                    surrounded = false; // Tahta dışına çıkıyorsa çevrili değildir
                    continue;
                }

                if (visited[nr][nc]) {
                    continue;
                }

                if (board[nr][nc] == '\0') {
                    visited[nr][nc] = true;
                    queue.add(new Point(nr, nc));
                } else {
                    surrounding.add(board[nr][nc]);
                }
            }
        }
        return surrounded;
    }

    private char[][] copyBoard(char[][] src) {
        char[][] newBoard = new char[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(src[i], 0, newBoard[i], 0, SIZE);
        }
        return newBoard;
    }

//    private boolean isBoardEqual(char[][] a, char[][] b) {
//        for (int i = 0; i < SIZE; i++) {
//            for (int j = 0; j < SIZE; j++) {
//                if (a[i][j] != b[i][j]) {
//                    return false;
//                }
//            }
//        }
//        return true;
//    }
}
