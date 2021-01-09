package it.unipd.wirelessnetworks.client;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.PriorityQueue;

public class SmartPlugsClients {
    private int watt = 15;
    private Socket socket; 

    public static void main(String[] args) {
        try {
            // preparing message to send to the server
            JSONObject obj = new JSONObject();
            obj.put("watt", new Integer(watt));
            String message = obj.toString();
            
            // connection and sending message
            socket = new Socket(5056);
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            outputStream.writeBytes(message);

            // receving response and corresponding behavior
            String response = inputStream.readUTF();
            JSONObject responseJSON = new JSONObject(response);
            String action = responseJSON.getString("type");
            switch(action) {
                case "OK":
                    // connects to power grid
                    int value = responseJSON.getInt("next");
                    break;
                case "EXCEEDED":
                    // hold on, notify user
                    break;
            }
        } catch (IOException e) { 
            e.printStackTrace(); 
        }
    }
}
