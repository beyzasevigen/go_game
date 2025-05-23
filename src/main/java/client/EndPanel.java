package client;

import javax.swing.*;
import java.awt.*;

/**
 * EndPanel displays the final result of the game.
 * It shows the scores and provides a button to restart.
 */
public class EndPanel extends JFrame {

    public EndPanel(double blackScore, double whiteScore) {
        setTitle("Game Over");
        setSize(300, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Determine the winner based on scores
        String winner;
        if (blackScore > whiteScore) {
            winner = "Black wins!";
        } else if (whiteScore > blackScore) {
            winner = "White wins!";
        } else {
            winner = "It's a draw!";
        }

        // Show score and result
        JLabel resultLabel = new JLabel(
                "<html>The game has ended!<br><br>"
                + "Black: " + blackScore + " points<br>"
                + "White: " + whiteScore + " points<br><br>"
                + winner + "</html>",
                SwingConstants.CENTER
        );

        // Restart button to return to StartPanel
        JButton restartButton = new JButton("Restart");
        restartButton.addActionListener(e -> {
            dispose(); // Close this window
            SwingUtilities.invokeLater(() -> {
                new StartPanel("16.171.6.36", 12345).setVisible(true);
            });
        });

        add(resultLabel, BorderLayout.CENTER);
        add(restartButton, BorderLayout.SOUTH);
        setVisible(true);
    }
}
