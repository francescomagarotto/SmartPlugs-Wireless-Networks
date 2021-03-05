package it.unipd.wirelessnetworks.server;

import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.logging.Logger;

class RequestsService extends Thread {
    public static final Logger LOGGER = Logger.getLogger(INITSender.class.getName());
    private int commandID = 0;
    private double currentWatt = 0;
    private byte[] buf = new byte[256];
    private List<ExpectedACK> expectedAcksList;
    ClientData map;
    Map<String, Integer> wattsDeviceMap;

    public RequestsService(List<ExpectedACK> expectedAcksList) throws SocketException {
        this.expectedAcksList = expectedAcksList;
        map = ClientData.getInstance();
        wattsDeviceMap = new HashMap<>();
        wattsDeviceMap.put("DRYER", 3000);
        wattsDeviceMap.put("STOVE", 3000);
        wattsDeviceMap.put("OVEN", 3000);
        wattsDeviceMap.put("IRON", 2000);
        wattsDeviceMap.put("DISHWASHER", 1800);
        wattsDeviceMap.put("WASHINGMACHINE", 500);
        wattsDeviceMap.put("HUMIDIFIER", 500);
        wattsDeviceMap.put("DEHUMIDIFIER", 300);
        wattsDeviceMap.put("COFFEEMACHINE", 300);
        wattsDeviceMap.put("PC", 250);
        wattsDeviceMap.put("TV", 120);
        wattsDeviceMap.put("LAMP", 40);
        wattsDeviceMap.put("CHARGER", 5);
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
                    String statusAction = "OFF";
                    DatagramPacket replyPacket;
                    switch (jsonObject.getString("act")) {
                        case "INIT":
                            if (map.containsKey(clientAddress)) {
                                // TODO: fringe case: if client doesn't respond to INIT for long enough: set status to off
                            } else {
                                // if client unknown to server: add to map<address, JSON>, reply with on/off
                                // getting usage from client or from default usage map (when client isn't connected to grid and doesn't know usage)
                                LOGGER.info("[Server] Client: " + clientAddress + " is connecting for the first time");
                                double watts = jsonObject.getDouble("max_power_usage");
                                if (watts == 0d) {
                                    watts = wattsDeviceMap.get(jsonObject.getString("type"));
                                }

                                // if there's room: status = on, this will be used to send an ON packet to the client
                                if (currentWatt + watts < map.getAvailableWatts()) {
                                    statusAction = "ON";
                                    currentWatt+=watts;
                                }
                                // updating server's map of clients
                                JSONObject mapJson = new JSONObject();
                                mapJson.put("type", jsonObject.getString("type"));
                                mapJson.put("watts", watts);
                                mapJson.put("max_power_usage", watts);
                                mapJson.put("status", statusAction);
                                map.putClient(clientAddress, mapJson);
                                // sending ON/OFF packet to client
                                JSONObject replyJson = new JSONObject();
                                replyJson.put("act", statusAction);
                                LOGGER.info("[Server] Added client: " + clientAddress + " to internal map, with information: " + mapJson.toString());
                                LOGGER.info("[Server] updated current power usage: " +currentWatt);
                                LOGGER.info("[Server] Replying to client: " + clientAddress + " with: " + replyJson.toString());
                                sendToClient(clientAddress, replyJson);
                            }
                            break;
                        case "UPDATE":
                            // if there's no room for client reply with off (should the server w8 for ACK?),
                            // else, if change is negative check if more clients can connect
                            // comparing previous wattage to new wattage
                            LOGGER.info("[Server] Received UPDATE packet from Client: " + clientAddress);
                            LOGGER.info("[Message content] " + jsonObject.toString());
                            double new_watts = jsonObject.getDouble("active_power");
                            double old_watts = map.getClient(clientAddress).getDouble("watts");
                            double old_max_watts = map.getClient(clientAddress).getDouble("max_power_usage");
                            String currentStatus = map.getClient(clientAddress).getString("status");
                            double max_watts;
                            if (new_watts > old_max_watts)
                                max_watts = new_watts;
                            else
                                max_watts = old_max_watts;
                            // now device usage is lower so: device stays connected, maybe more device can connect
                            if (new_watts < old_watts) {
                                if (currentStatus.equals("ON")) {
                                    statusAction = "ON";
                                } else if ((currentWatt - old_watts) + new_watts < map.getAvailableWatts()) {
                                    statusAction = "ON";
                                }
                                if (new_watts == 0.0) {
                                    statusAction = "OFF";
                                }
                                currentWatt -= old_watts;
                                currentWatt += new_watts;
                                // connect more devices if possible (first come first served)
                                Set<Map.Entry<String, JSONObject>> entrySet = map.entrySet();
                                for (Map.Entry<String, JSONObject> entry : entrySet) {
                                    double entryWatts = entry.getValue().getDouble("max_power_usage");
                                    String status = entry.getValue().getString("status");
                                    // only if the client is off
                                    if (status.equals("OFF")) {
                                        // if there's room for it
                                        if (currentWatt + entryWatts < map.getAvailableWatts()) {
                                            // update availableWatts
                                            currentWatt += entryWatts;
                                            String newClientAddress = entry.getKey();
                                            // send ON packet to this client
                                            JSONObject replyJson = new JSONObject();
                                            replyJson.put("act", "ON");
                                            LOGGER.info("[Server] UPDATE from client: " + clientAddress + " allows: " + newClientAddress + " to also turn ON, sending packet: " + replyJson.toString());
                                            sendToClient(newClientAddress, replyJson);
                                        }
                                    }
                                }
                            } else {
                                // now, if the client is ON there may not be room
                                if (currentStatus.equals("ON")) {
                                    if ((currentWatt - old_watts) + new_watts < map.getAvailableWatts()) {
                                        // in this case, there still is enough room
                                        statusAction = "ON";
                                        currentWatt -= old_watts;
                                        currentWatt += new_watts;
                                    }
                                }
                            }

                            // in any case, update map data for client
                            JSONObject updatedJson = map.getClient(clientAddress);
                            updatedJson.put("status", statusAction);
                            updatedJson.put("watts", new_watts);
                            updatedJson.put("max_power_usage", max_watts);
                            map.putClient(clientAddress, updatedJson);
                            // sending ON/OFF packet to client
                            JSONObject replyJson = new JSONObject();
                            replyJson.put("act", statusAction);
                            LOGGER.info("[Server] updated current power usage: " +currentWatt);
                            LOGGER.info("[Server] Replying to client: " + clientAddress + " with: " + replyJson.toString());
                            sendToClient(clientAddress, replyJson);
                            break;
                        case "ACK":
                            // if an ACK is received, remove from list of expected ACKs
                            int listLen = expectedAcksList.size();
                            LOGGER.info("[Server] Received ACK from: " + clientAddress);
                            Optional<ExpectedACK> optionalExpectedACK =
                                    expectedAcksList.stream().filter(expectedACK -> expectedACK.clientAddress.equals(clientAddress) && (expectedACK.commandID == jsonObject.getInt("id"))).findFirst();
                            optionalExpectedACK.ifPresent((e) -> expectedAcksList.remove(e));
                            if(listLen == expectedAcksList.size()+1) {
                                LOGGER.info("[Server] Removed ACK with ID "+jsonObject.getInt("id")+" from expected ACKs list for client: " + clientAddress);
                            }
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // this method sends a JSON to a given client and adds an entry to the expectedACKs list
    public void sendToClient(String address, JSONObject json) {
        json.put("id", commandID);
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] replyBytes = json.toString().getBytes("UTF-8");
            DatagramPacket replyPacket = new DatagramPacket(replyBytes, replyBytes.length, InetAddress.getByName(address), Configurations.PORT);
            expectedAcksList.add(new ExpectedACK(address, commandID, replyPacket, Configurations.TTL));
            socket.send(replyPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
        commandID += 1;
        if(commandID == Integer.MAX_VALUE) {
            commandID = 0;
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
