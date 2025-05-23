package client;

import javax.swing.*;
import java.awt.*;

public class EndPanel extends JFrame {

    public EndPanel(double blackScore, double whiteScore) {
        setTitle("Oyun Bitti");
        setSize(300, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        String winner;
        if (blackScore > whiteScore) {
            winner = "Siyah kazandı!";
        } else if (whiteScore > blackScore) {
            winner = "Beyaz kazandı!";
        } else {
            winner = "Berabere!";
        }

        JLabel resultLabel = new JLabel(
                "<html>Oyun sona erdi!<br><br>"
                + "Siyah: " + blackScore + " puan<br>"
                + "Beyaz: " + whiteScore + " puan<br><br>"
                + winner + "</html>",
                SwingConstants.CENTER
        );

        JButton restartButton = new JButton("Yeniden Başlat");
        restartButton.addActionListener(e -> {
            dispose(); // bu pencereyi kapat
            SwingUtilities.invokeLater(() -> {
                new StartPanel("localhost", 12345).setVisible(true);  // direkt localhost'a bağlan
            });

        });

        add(resultLabel, BorderLayout.CENTER);
        add(restartButton, BorderLayout.SOUTH);
        setVisible(true);
    }
}
