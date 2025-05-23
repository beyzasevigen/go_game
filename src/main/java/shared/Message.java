package shared;

import java.io.Serializable;

// Message is used to send data between client and server
public class Message implements Serializable {

    // Type of the message (e.g., move, connect, end)
    public String type;

    // Actual data of the message
    public String payload;

    // Creates a new message with a given type and payload
    public Message(String type, String payload) {
        this.type = type;
        this.payload = payload;
    }
}
