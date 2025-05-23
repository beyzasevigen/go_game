/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

public class ClientMain {

    public static void main(String[] args) {
        // Ana metodda oyunu başlatıyoruz (client tarafı)
        String serverIp = "localhost"; // Sunucu IP'si
        int serverPort = 12345; // Sunucu portu
        StartPanel sp = new StartPanel(serverIp, serverPort);
        sp.setVisible(true);
    }
}