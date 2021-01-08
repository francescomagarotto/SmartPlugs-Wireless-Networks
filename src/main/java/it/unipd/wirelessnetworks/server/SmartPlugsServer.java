package it.unipd.wirelessnetworks.server;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartPlugsServer {

    public static int MAX_WATT_COUNTER = 3000;


    public static void main(String[] args) throws IOException {
        AtomicInteger wattCounter = new AtomicInteger(0);
        PriorityQueue<String> clientsQueue;
        ServerSocket serverSocket = new ServerSocket(5056);
        while(true) {
            Socket socket;
            try {
               socket = serverSocket.accept();
               DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
               DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
               (new ClientRequestDelegate(socket, dataInputStream, dataOutputStream, wattCounter)).start();
            }
            catch (Exception ignored) {

            }
        }

    }

}

class ClientRequestDelegate extends Thread {
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private AtomicInteger wattCounter;
    public ClientRequestDelegate(Socket socket, DataInputStream inputStream, DataOutputStream dataOutputStream, AtomicInteger wattCounter) {
        this.socket = socket;
        this.inputStream = inputStream;
        this.outputStream = dataOutputStream;
        this.wattCounter = wattCounter;
    }

    @Override
    public void run() {
        try {
            String request = inputStream.readUTF();
            JSONObject requestJSON = new JSONObject(request);
            String action = requestJSON.getString("type");
            int value = requestJSON.getInt("watt");
            switch(action) {
                case "STARTUP":
                    if(wattCounter.get() + value <= SmartPlugsServer.MAX_WATT_COUNTER) {
                        wattCounter.addAndGet(value);
                        outputStream.writeUTF("{'type' : 'OK', 'next': 60}");
                    }
                    else {
                        outputStream.writeUTF("{'type': 'EXCEEDED");
                    }
                    break;
                case "REMOVE":
                    wattCounter.set(wattCounter.get() - value);
                    break;
            }
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}