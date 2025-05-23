package client;

import shared.Message;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * StartPanel displays the first screen where the user can start the game.
 * It connects to the server and waits for an opponent.
 */
public class StartPanel extends JFrame {

    String serverIp;
    int serverPort;

    public StartPanel(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;

        setTitle("Go Game - Start");
        setSize(300, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JButton startButton = new JButton("Start Game");

        // When the user clicks the button, try to connect to the server
        startButton.addActionListener(e -> {
            startButton.setEnabled(false);

            // Waiting dialog shown while waiting for opponent
            JDialog waitingDialog = new JDialog(this, "Waiting...", true);
            JLabel waitLabel = new JLabel("Waiting for opponent, please wait...");
            waitLabel.setHorizontalAlignment(SwingConstants.CENTER);
            waitingDialog.add(waitLabel);
            waitingDialog.setSize(300, 100);
            waitingDialog.setLocationRelativeTo(this);
            
            SwingUtilities.invokeLater(() -> waitingDialog.setVisible(true));

            new Thread(() -> {
                ClientSocketHandler tempConnection = null;
                try {
                    tempConnection = new ClientSocketHandler(serverIp, serverPort);

                    Message initMsg = null;
                    Message readyMsg = null;

                    // Wait for both 'init' and 'ready' messages from server
                    while (true) {
                        Message msg = tempConnection.readMessage();
                        if (msg == null) {
                            continue;
                        }

                        if (msg.type.equals("init")) {
                            System.out.println("INIT received in StartPanel: " + msg.payload);
                            initMsg = msg;
                        } else if (msg.type.equals("ready")) {
                            readyMsg = msg;
                        }

                        if (initMsg != null && readyMsg != null) {
                            break;
                        }
                    }

                    ClientSocketHandler finalConnection = tempConnection;
                    Message finalInit = initMsg;

                    SwingUtilities.invokeLater(() -> {
                        waitingDialog.dispose();
                        dispose(); // Close StartPanel
                        GamePanel gm = new GamePanel(serverIp, serverPort, finalConnection, finalInit.payload);
                        gm.setVisible(true);
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        waitingDialog.dispose();
                        JOptionPane.showMessageDialog(this, "Connection error.");
                        startButton.setEnabled(true);
                    });
                    if (tempConnection != null) {
                        try {
                            tempConnection.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }).start();

        });

        add(startButton, BorderLayout.CENTER);
    }
}
