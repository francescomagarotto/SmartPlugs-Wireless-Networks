package it.unipd.wirelessnetworks.server;

import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.logging.Logger;

class RequestsService extends Thread /*implements Observer */{
    public static final Logger LOGGER = Logger.getLogger(RequestsService.class.getName());
    private int commandID = 0;
    private double currentWatt = 0;
    private byte[] buf = new byte[256];

    ClientData map;
    Map<String, Integer> wattsDeviceMap;

    public RequestsService() throws SocketException {
        map = ClientData.getInstance();
        wattsDeviceMap = map.getDefaultWattsDevice();
    }

    public void run() {
        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try (DatagramSocket socket = new DatagramSocket(Configurations.PORT)) {
                socket.receive(packet); // receiving packet from client
            } catch (IOException e) {
                e.printStackTrace();
            }
            String clientAddress = inetAddressToString(packet.getAddress());
            try {
                // avoid reading own packets
                String localAddress = InetAddress.getLocalHost().getHostAddress();
                if (!((clientAddress).contains(localAddress))) {
                    String received = new String(packet.getData(), 0, packet.getLength());
                    received = received.substring(1, received.length() - 1);
                    LOGGER.info("[Server] Received: " + received + " from Client: " + clientAddress);
                    JSONObject jsonObject = new JSONObject(received);
                    int statusAction = 0;
                    DatagramPacket replyPacket;
                    switch (jsonObject.getString("act")) {
                        case "INIT":
                            // if client unknown to server: add to map<address, JSON>, reply with on/off
                            // getting usage from client or from default usage map (when client isn't connected to grid and doesn't know usage)
                            LOGGER.info("[Server] Client: " + clientAddress + " is connecting for the first time");
                            double watts = jsonObject.getDouble("max_power_usage");
                            if (watts == 0d) {
                                watts = wattsDeviceMap.get(jsonObject.getString("type"));
                            }

                            // updating server's map of clients
                            JSONObject mapJson = new JSONObject();
                            mapJson.put("type", jsonObject.getString("type"));
                            mapJson.put("watts", 0);
                            mapJson.put("max_power_usage", watts);
                            mapJson.put("status", statusAction);
                            map.putClient(clientAddress, mapJson);
                            // sending ON/OFF packet to client
                            JSONObject replyJson = new JSONObject();
                            replyJson.put("act", statusAction);
                            LOGGER.info("[Server] Added client: " + clientAddress + " to internal map, with information: " + mapJson.toString());
                            LOGGER.info("[Server] updated current power usage: " + currentWatt);
                            LOGGER.info("[Server] Replying to client: " + clientAddress + " with: " + replyJson.toString());
                            // TODO: else fringe case: if client doesn't respond to INIT for long enough: set status to off
                            break;
                        case "UPDATE":
                            // if there's no room for client reply with off (should the server w8 for ACK?),
                            // else, if change is negative check if more clients can connect
                            // comparing previous wattage to new wattage

                            LOGGER.info("[Server] Received UPDATE packet from Client: " + clientAddress);
                            LOGGER.info("[Message content] " + jsonObject.toString());
                            double new_watts = jsonObject.getDouble("active_power");
                            double old_watts = map.getClient(clientAddress).getDouble("watts");
                            double max_watts;
                            String type = jsonObject.getString("type");
                            // if the device connected is of the same type, check if max power needs update, else set default for new type
                            if (map.getClient(clientAddress).getString("type").equals(type)) {
                                double old_max_watts = map.getClient(clientAddress).getDouble("max_power_usage");
                                if (new_watts > old_max_watts)
                                    max_watts = new_watts;
                                else
                                    max_watts = old_max_watts;
                            } else {
                                max_watts = wattsDeviceMap.get(jsonObject.getString("type"));
                            }

                            currentWatt -= old_watts;
                            currentWatt += new_watts;
                            if (new_watts>0) {
                                statusAction = 1;
                            }
                            // in any case, update map data for client
                            JSONObject updatedJson = map.getClient(clientAddress);
                            updatedJson.put("status", statusAction);
                            updatedJson.put("watts", new_watts);
                            updatedJson.put("max_power_usage", max_watts);
                            updatedJson.put("type", type);
                            map.putClient(clientAddress, updatedJson);
                            LOGGER.info("[Server] updated current power usage: " +currentWatt);
                            break;
                        case "ACK":
                            // if an ACK is received, remove from list of expected ACKs
                            List<ExpectedACK> expectedAcksList = AckListSingleton.getInstance().getExpectedAckList();
                            String timestamp = jsonObject.getString("timestamp");
                            LOGGER.info("[Server] Received ACK from: " + clientAddress);
                            Optional<ExpectedACK> optionalExpectedACK =
                                    expectedAcksList.stream().filter(
                                            expectedACK ->
                                                    expectedACK.clientAddress.equals(clientAddress)
                                                            && expectedACK.timestamp.equals(timestamp)).findFirst();
                            if(optionalExpectedACK.isPresent()) {
                                ExpectedACK e = optionalExpectedACK.get();
                                AckListSingleton.getInstance().getExpectedAckList().remove(e);
                                LOGGER.info("[Server] Removed " + e.toString());
                            }
                            LOGGER.info("[Server] Status " + map.getAllClients().toString());
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String inetAddressToString(InetAddress address) {
        String strAddress = address.toString();
        if (strAddress.startsWith("/"))
            return strAddress.substring(1);
        else
            return strAddress;
    }
}
