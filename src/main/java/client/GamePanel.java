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

    // Constants for board size and cell dimensions
    private static final int SIZE = 19;
    private static final int CELL_SIZE = 30;

    // Game board data structures
    private char[][] board = new char[SIZE][SIZE];
    private char[][] previousBoard = new char[SIZE][SIZE];
    private JTextArea moveLogArea;

    // Flags and state variables for gameplay logic
    private boolean iPassed;
    private boolean opponentPassed;
    private ClientSocketHandler socketHandler;
    private boolean isMyTurn = false;
    private char myColor;
    private Point lastKoPosition = null;
    private List<Point> lastKoCaptured = new ArrayList<>();
    private int capturedByBlack = 0;
    private int capturedByWhite = 0;

    // UI-related fields
    private JDialog waitingDialog = null;
    private String serverIp;
    private int serverPort;
    private boolean initialized = false;
    private boolean isGameOver = false;
    private boolean userClosedWindow = false;

    // Constructor to initialize the game panel and UI components
    public GamePanel(String serverIp, int serverPort, ClientSocketHandler handler, String colorPayload) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.socketHandler = handler;
        this.myColor = colorPayload.equals("Black") ? 'B' : 'W';
        this.isMyTurn = (myColor == 'B');
        this.initialized = true; // Only set true when init and ready are both received

        setTitle("Go Game - Game Screen");
        //setSize(SIZE * CELL_SIZE + 250, SIZE * CELL_SIZE + 100);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Panel where the board is drawn
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
            }
        };
        // Başlık ve log alanı için panel
        JLabel logLabel = new JLabel("Move History");
        logLabel.setHorizontalAlignment(SwingConstants.CENTER);

        moveLogArea = new JTextArea();
        moveLogArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(moveLogArea);
        scrollPane.setPreferredSize(new Dimension(200, SIZE * CELL_SIZE));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(logLabel, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(SIZE * CELL_SIZE, SIZE * CELL_SIZE));

        // Handle mouse click events on the board
        panel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (!initialized) {
                    // System.out.println(">>> Click ignored: INIT not yet received.");
                    return;
                }

                if (!isMyTurn) {
                    // System.out.println(">>> Click ignored. isMyTurn: " + isMyTurn + ", color: " + myColor);
                    JOptionPane.showMessageDialog(GamePanel.this, "It's not your turn!");
                    return;
                }

                int row = e.getY() / CELL_SIZE;
                int col = e.getX() / CELL_SIZE;

                List<Point> removedStones = new ArrayList<>();
                if (placeStone(row, col, removedStones)) {
                    sendMove(row, col, myColor, removedStones);
                    isMyTurn = false;
                    panel.repaint();
                    moveLogArea.append(myColor + " placed at (" + row + "," + col + ")\n");
                }
            }
        });
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(panel, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);
        add(mainPanel);

        // Pass button to skip a turn
        JButton passButton = new JButton("Pass");
        passButton.addActionListener(e -> {
            if (!isMyTurn) {
                JOptionPane.showMessageDialog(this, "It's not your turn to pass!");
                return;
            }

            iPassed = true;

            try {
                socketHandler.sendMessage(new Message("pass", String.valueOf(myColor)));
                isMyTurn = false;
                moveLogArea.append(myColor + " passed\n");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to send pass message.");
            }
        });
        add(passButton, BorderLayout.SOUTH);

        // Move log area setup
        moveLogArea.setEditable(false);
        moveLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        moveLogArea.setMargin(new Insets(0, 0, 0, 0));
        moveLogArea.setBackground(new Color(245, 245, 245));

        //scrollPane.setPreferredSize(new Dimension(200, SIZE * CELL_SIZE));
        //add(scrollPane, BorderLayout.EAST); // placed it to right side of the board
        // Listen for incoming messages from the server
        new Thread(() -> {
            while (true) {
                try {
                    Message msg = socketHandler.readMessage();
                    // System.out.println("Message from server: " + msg.type + " - " + msg.payload);

                    switch (msg.type) {
                        case "init":
                            myColor = msg.payload.equals("Black") ? 'B' : 'W';
                            isMyTurn = (myColor == 'B');
                            initialized = true;
                            // System.out.println(">>> INIT received - Color: " + myColor + " | isMyTurn: " + isMyTurn);
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
                            handleOpponentPass();
                            break;
                        case "end":
                            // System.out.println("END message received, game closing.");
                            isGameOver = true;

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
                        // System.out.println("Unknown message type: " + msg.type);
                    }
                } catch (Exception e) {
                    // System.out.println("Message read error: " + e.getMessage());
                    if (!isGameOver && !userClosedWindow) {
                        SwingUtilities.invokeLater(()
                                -> JOptionPane.showMessageDialog(this, "Opponent disconnected.")
                        );
                    }
                    break;
                }
            }
        }).start();

        // Handle window close event to send exit message to server
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                userClosedWindow = true;
                try {
                    if (socketHandler != null) {
                        socketHandler.sendMessage(new Message("exit", ""));
                        socketHandler.close();
                        // System.out.println("Client connection closed.");
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void handleIncomingMove(String payload) {
        // Parse the move message received from the opponent
        String[] parts = payload.split(";");
        String[] moveParts = parts[0].split(",");
        int row = Integer.parseInt(moveParts[0]);
        int col = Integer.parseInt(moveParts[1]);
        char color = moveParts[2].charAt(0);
        board[row][col] = color; // Place the opponent's stone

        // If there are any removed stones included in the message
        if (parts.length > 1 && parts[1].startsWith("removed:")) {
            String[] removedParts = parts[1].substring(8).split("\\|");
            for (String pos : removedParts) {
                String[] rc = pos.split(",");
                int r = Integer.parseInt(rc[0]);
                int c = Integer.parseInt(rc[1]);
                board[r][c] = '\0'; // Clear captured stones from the board
            }
        }

        // Save board state and allow player to take their turn
        previousBoard = copyBoard(board);
        isMyTurn = true;
        iPassed = false;
        repaint(); // Redraw the board with updates
        moveLogArea.append(color + ": (" + row + "," + col + ")\n");
    }

    private void drawBoard(Graphics g) {
        // Draw grid lines for the Go board
        g.setColor(Color.BLACK);
        for (int i = 0; i < SIZE; i++) {
            g.drawLine(CELL_SIZE / 2, CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2 + (SIZE - 1) * CELL_SIZE, CELL_SIZE / 2 + i * CELL_SIZE);
            g.drawLine(CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2, CELL_SIZE / 2 + i * CELL_SIZE, CELL_SIZE / 2 + (SIZE - 1) * CELL_SIZE);
        }

        // Draw stones based on board state
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 'B') {
                    g.setColor(Color.BLACK);
                    g.fillOval(j * CELL_SIZE + 5, i * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                } else if (board[i][j] == 'W') {
                    g.setColor(Color.WHITE);
                    g.fillOval(j * CELL_SIZE + 5, i * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
                    g.setColor(Color.BLACK);
                    g.drawOval(j * CELL_SIZE + 5, i * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10); // Outline for white stones
                }
            }
        }
    }

    private void resetGame() {
        // Clear the board and reset state variables
        board = new char[SIZE][SIZE];
        previousBoard = new char[SIZE][SIZE];
        iPassed = false;
        opponentPassed = false;
        capturedByBlack = 0;
        capturedByWhite = 0;
        lastKoPosition = null;
        lastKoCaptured.clear();

        if (moveLogArea != null) {
            moveLogArea.setText("");
        }
    }

    private void showWaitingDialog(String message) {
        // Prevent multiple dialogs from appearing
        if (waitingDialog != null && waitingDialog.isVisible()) {
            return;
        }

        waitingDialog = new JDialog(this, "Waiting...", true);
        waitingDialog.setLayout(new BorderLayout());

        // Display the waiting message
        JLabel label = new JLabel("<html><center>" + message + "<br>Please wait...</center></html>", SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.PLAIN, 14));
        waitingDialog.add(label, BorderLayout.CENTER);

        // Button to return to the start panel
        JButton backButton = new JButton("Return to Main Menu");
        backButton.addActionListener(e -> {
            try {
                socketHandler.sendMessage(new Message("exit", "")); // Notify server of exit
                socketHandler.close(); // Close connection
                System.out.println("Returned to main menu, connection closed.");
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            waitingDialog.dispose();  // Close the dialog
            this.dispose();           // Close the game panel

            SwingUtilities.invokeLater(() -> new StartPanel(serverIp, serverPort).setVisible(true));
        });

        waitingDialog.add(backButton, BorderLayout.SOUTH);

        waitingDialog.setSize(300, 150);
        waitingDialog.setLocationRelativeTo(this);
        waitingDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        waitingDialog.setResizable(false);

        // Run the dialog in a separate thread
        new Thread(() -> waitingDialog.setVisible(true)).start();
    }

    /**
     * Attempts to place a stone on the board. Handles opponent capture, suicide
     * rule, and KO rule. Returns true if the stone was placed successfully,
     * false otherwise.
     */
    private boolean placeStone(int row, int col, List<Point> removed) {
        if (!isValid(row, col) || board[row][col] != '\0') {
            return false;
        }

        char currentColor = myColor;
        char opponentColor = (myColor == 'B') ? 'W' : 'B';

        // 1. Tentatively place the stone
        board[row][col] = currentColor;

        // 2. Check and capture opponent stones if they have no liberty
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

        // 3. KO rule: prevent placing a stone that immediately reverts to the previous state
        if (lastKoPosition != null && removed.size() == 1) {
            Point last = lastKoCaptured.get(0);
            if (row == lastKoPosition.x && col == lastKoPosition.y
                    && removed.get(0).x == last.x && removed.get(0).y == last.y) {
                // Undo placement and reject move
                board[row][col] = '\0';
                removed.clear();
                JOptionPane.showMessageDialog(this, "KO rule: This move is not allowed.");
                return false;
            }
        }

        // 4. Remove captured stones
        if (!removed.isEmpty()) {
            for (Point p : removed) {
                board[p.x][p.y] = '\0';
            }
        }

        // 5. Check if our own stone is suicidal (no liberties)
        boolean[][] selfVisited = new boolean[SIZE][SIZE];
        if (!hasLiberty(row, col, currentColor, selfVisited)) {
            // Undo placement and restore opponent stones
            board[row][col] = '\0';
            for (Point p : removed) {
                board[p.x][p.y] = opponentColor;
            }
            removed.clear();
            return false;
        }

        // 6. Update KO state
        if (removed.size() == 1) {
            lastKoPosition = new Point(row, col);
            lastKoCaptured = new ArrayList<>(removed);
        } else {
            lastKoPosition = null;
            lastKoCaptured.clear();
        }

        // 7. Update capture counters
        if (myColor == 'B') {
            capturedByBlack += removed.size();
        } else {
            capturedByWhite += removed.size();
        }

        return true;
    }

    /**
     * Collects the coordinates of the stones marked for removal in the visited
     * matrix.
     */
    private void collectRemoved(boolean[][] visited, List<Point> list) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (visited[i][j]) {
                    list.add(new Point(i, j));
                }
            }
        }
    }

    /**
     * Checks whether a group of stones starting from (row, col) has any
     * liberties (empty adjacent cells). Uses depth-first search to traverse
     * connected stones of the same color.
     */
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

    // Checks whether the given row and column are within the bounds of the board.
    private boolean isValid(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

// Sends a move message to the opponent, including any captured stones.
    private void sendMove(int row, int col, char color, List<Point> removed) {
        StringBuilder payload = new StringBuilder(row + "," + col + "," + color);

        // If there are captured stones, append their positions to the payload.
        if (!removed.isEmpty()) {
            payload.append(";removed:");
            for (Point p : removed) {
                payload.append(p.x).append(",").append(p.y).append("|");
            }
            payload.setLength(payload.length() - 1); // Remove the last '|'
        }

        try {
            socketHandler.sendMessage(new Message("move", payload.toString()));
            //Hamleyi kullanıcıya gösteren log alanı
            moveLogArea.append(color + ": (" + row + "," + col + ")\n");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Move could not be sent.");
        }
    }

// Handles the event when the opponent passes their turn.
    public void handleOpponentPass() {
        opponentPassed = true;

        // If the current player did not also pass, they get the next turn.
        if (!iPassed) {
            JOptionPane.showMessageDialog(this, "Opponent passed. It's your turn.");
            moveLogArea.append((myColor == 'B' ? 'W' : 'B') + " passed\n");
            isMyTurn = true;
            iPassed = false;
        }
        // If both players passed, the server will handle ending the game.
    }

// Calculates the final score for both players, including komi for White.
    private double[] countScoreWithKomi(int capturedByBlack, int capturedByWhite) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        double black = 0, white = 0;
        double komi = 6.5;

        // 1. Count the stones currently on the board.
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col] == 'B') {
                    black++;
                } else if (board[row][col] == 'W') {
                    white++;
                }
            }
        }

        // 2. Scan empty territories and add them to the score of the surrounding color.
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

        // 3. Add komi points to White's final score.
        white += komi;
        return new double[]{black, white};
    }

// Helper method that performs a flood fill from a given point,
// determining the surrounding colors and collecting the empty region.
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

                // If out of bounds, the region is not surrounded.
                if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) {
                    surrounded = false;
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

// Creates a deep copy of the current board state.
    private char[][] copyBoard(char[][] src) {
        char[][] newBoard = new char[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            System.arraycopy(src[i], 0, newBoard[i], 0, SIZE);
        }
        return newBoard;
    }
}
