package it.unipd.wirelessnetworks.client;

import org.json.JSONObject;

import java.lang.Math;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.PriorityQueue;

public class SmartPlugsClients {

    public static void main(String[] args) {

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        executorService.submit(
            new Runnable() {
                @Override
                public void run() {
                    try {
                        // preparing message to send to the server
                        JSONObject obj = new JSONObject();
                        int watt = (int)(Math.random()*2000)+1; // between 1 and 2000
                        obj.put("watt", Integer.valueOf(watt));
                        String message = obj.toString();
                        
                        // connection and sending message
                        Socket socket = new Socket(InetAddress.getLoopbackAddress(), 5056);
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
        );
    }
}
