/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package shared;

import java.io.Serializable;

public class Message implements Serializable {
public String type; // "move", "connect", "end"
public String payload;

public Message(String type, String payload) {
    this.type = type;
    this.payload = payload;
}
}