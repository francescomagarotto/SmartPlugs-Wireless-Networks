package it.unipd.wirelessnetworks.server;

import java.util.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class SmartPlugsServer extends WebSocketServer {

    private static int MAX_WATT_COUNTER = 3000;
    private static AtomicInteger wattCounter = new AtomicInteger(0);
    private Hashtable<String, ArrayList<String>> clients;

    public SmartPlugsServer(InetSocketAddress address) {
        super(address);
        clients = new Hashtable<String, ArrayList<String>>();
    }

    private void connectClientToGrid(WebSocket connection, String clientID, String clientName, String clientWatts) {
        // updating count of used watts
        wattCounter.addAndGet(Integer.parseInt(clientWatts));

        // saving clients info
        ArrayList<String> clientInfo = new ArrayList<String>();
        clientInfo.add(clientName);
        clientInfo.add(clientWatts);
        clients.put(clientID, clientInfo);

        // telling client it's connected
        connection.send("{'type' : 'OK', 'next': 60}");
    }

    private void disconnectClientFromGrid(String clientID) {
        broadcast("{'type': 'DISCONNECT', id: " + clientID + "}");
        int clientWatts = Integer.parseInt(clients.get(clientID).get(1));
        wattCounter.addAndGet(-clientWatts);
        clients.remove(clientID);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        System.out.println("[SERVER] Client " + connection.getRemoteSocketAddress() + " connected to the server");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[SERVER] Closed connection with" + conn.getRemoteSocketAddress() + " with exit code " + code
                + " additional info: " + reason);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        JSONObject obj = new JSONObject(message);
        String clientID = obj.getString("id");
        String clientName = obj.getString("name");
        String clientWatts = obj.getString("watts");

        if (clients.keySet().contains(clientID)) {
            // we are here the message that raised onMessage came from a client which IS
            // connected to the power grid
            disconnectClientFromGrid(clientID);
        } else {
            // we are here the message that raised onMessage came from a client which ISN'T
            // connected to the power grid
            // if there's enough wattage to connect client
            if (wattCounter.get() + Integer.parseInt(clientWatts) <= MAX_WATT_COUNTER) {
                connectClientToGrid(connection, clientID, clientName, clientWatts);
            } else {
                System.out.println("[SERVER]:");
                System.out
                        .println(clientName + " has tried to connect but there's not enough wattage to accomodate it");
                System.out
                        .println("Type the IDs separated by spaces of the devices to disconnect in order to accomodate "
                                + clientName + " or Type -1 to leave " + clientName + "disconnected");
                // listing connected clients for the user
                for (String key : clients.keySet()) {
                    System.out.println("Id: " + key + " device: " + clients.get(key).get(0) + " wattage: "
                            + clients.get(key).get(1));
                }

                // getting enough clients to disconnect in order to connect new client
                // if user doesn't want to disconnect other clients, telling new client it can't
                // connect

                int sumWattage = -1; // starting from -1 in order to avoid printing to the user that the clients
                                     // chosen aren't enough
                ArrayList<String> idsToDisconnect = new ArrayList<>();

                // while the user doesn't choose enough clients to disconnect
                while (sumWattage < Integer.parseInt(clientWatts)) {

                    // this will be displayed only if the user tried to disconnect a few clients but
                    // weren't enough to accomodate the new client
                    if (sumWattage == 0) {
                        System.out.println("The clients you chose to disconnect aren't enough.");
                        System.out.println(
                                "Type the IDs separated by spaces of the devices to disconnect in order to accomodate "
                                        + clientName + " or Type -1 to leave " + clientName + "disconnected");
                    }

                    sumWattage = 0;
                    idsToDisconnect = new ArrayList<>();

                    // user input
                    try (Scanner scanner = new Scanner(System.in)) {
                        String inputString = scanner.nextLine();

                        if (inputString.equals("-1")) {
                            // if we are here, the user doesn't want to disconnect other devices
                            connection.send("{'type': 'EXCEEDED', id: " + clientID + "}");
                        } else {
                            String[] ids = inputString.split(" ");
                            for (int i = 0; i < ids.length; i++) {
                                if (!clients.keySet().contains(ids[i])) {
                                    System.out.println(ids[i] + "is not a valid id for a device, retry id insertion: ");
                                    sumWattage = 0; // makes sure the user has to input the ids again
                                    break;
                                } else {
                                    sumWattage += Integer.parseInt(clients.get(ids[i]).get(1));
                                    idsToDisconnect.add(ids[i]);
                                }
                            }
                        }
                    }
                }

                // if we are here, the user gave a correct list of devices to disconnect to
                // accomodate the new device
                // disconnecting devices:
                for (int i = 0; i < idsToDisconnect.size(); i++) {
                    disconnectClientFromGrid(idsToDisconnect.get(i));
                }

                // connecting client
                connectClientToGrid(connection, clientID, clientName, clientWatts);
            }

        }

    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer message) {
        System.out.println(
                "[SERVER] Received message in an unsupported format from " + connection.getRemoteSocketAddress());
        // handle messages from clients (will there even be?)
    }

    @Override
    public void onError(WebSocket connection, Exception e) {
        System.err.println("[SERVER] An error occurred on connection " + connection.getRemoteSocketAddress() + ":" + e);
    }

    @Override
    public void onStart() {
        System.out.println("[SERVER] Started successfully");
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8887;

        WebSocketServer server = new SmartPlugsServer(new InetSocketAddress(host, port));
        server.run();
    }
}
