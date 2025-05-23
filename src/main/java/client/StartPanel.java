package client;

import shared.Message;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class StartPanel extends JFrame {

    String serverIp;
    int serverPort;

    public StartPanel(String serverIp, int serverPort) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;

        setTitle("Go Oyunu - Başlangıç");
        setSize(300, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JButton startButton = new JButton("Oyuna Başla");

        startButton.addActionListener(e -> {
            startButton.setEnabled(false);

            JDialog waitingDialog = new JDialog(this, "Bekleniyor...", true);
            JLabel waitLabel = new JLabel("Rakip oyuncu bekleniyor, lütfen bekleyin...");
            waitLabel.setHorizontalAlignment(SwingConstants.CENTER);
            waitingDialog.add(waitLabel);
            waitingDialog.setSize(300, 100);
            waitingDialog.setLocationRelativeTo(this);

            new Thread(() -> {
                ClientSocketHandler tempConnection = null;
                try {
                    tempConnection = new ClientSocketHandler(serverIp, serverPort);

                    Message initMsg = null;
                    Message readyMsg = null;

                    // init ve ready bekleniyor
                    while (true) {
                        Message msg = tempConnection.readMessage();
                        if (msg == null) {
                            continue;
                        }

                        if (msg.type.equals("init")) {
                            System.out.println("StartPanel içinde INIT geldi: " + msg.payload);
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
                        dispose(); // StartPanel'i kapat
                        GamePanel gm = new GamePanel(serverIp, serverPort, finalConnection, finalInit.payload);
                        gm.setVisible(true);
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        waitingDialog.dispose();
                        JOptionPane.showMessageDialog(this, "Bağlantı hatası.");
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
