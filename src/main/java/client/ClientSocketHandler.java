/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

import shared.Message;

import java.io.*;
import java.net.*;

public class ClientSocketHandler {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientSocketHandler(String serverIp, int port) throws IOException {
        socket = new Socket(serverIp, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }


    public void sendMessage(Message msg) throws IOException {
        out.writeObject(msg);
    }

    public Message readMessage() throws IOException, ClassNotFoundException {
        return (Message) in.readObject();
    }

    public void close() throws IOException {
        out.close();
        in.close();
        socket.close();
    }
}