package it.unipd.wirelessnetworks.server;

import com.google.common.util.concurrent.AbstractScheduledService;
import org.json.JSONObject;

import jdk.nashorn.internal.runtime.JSONListAdapter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.*;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

class Configurations {
    public static int PORT = 4210;
    public static int TTL = 5;
}

class ExpectedAck {
    public String clientAddress;
    public DatagramPacket packetToResend;
    public int ttl;

    public ExpectedAck(String address, DatagramPacket packet, int ttl) {
        this.clientAddress = address;
        this.packetToResend = packet;
        this.ttl = ttl;
    }
}

class ClientData {
    private Map<String, JSONObject> clientsMap = Collections.synchronizedMap(new HashMap<>());
    private static ClientData instance = new ClientData();

    public static ClientData getInstance() {
        return instance;
    }

    private ClientData() {}

    public boolean containsKey(String address) {
        return clientsMap.containsKey(address);
    }

    public void putClient(String address, JSONObject data) {
        clientsMap.put(address, data);
    }

    public JSONObject getClient(String address) {
        return clientsMap.get(address);
    }

    public Set<Map.Entry<String, JSONObject>> entrySet() {
        return clientsMap.entrySet();
    }

    public JSONObject getAllClients() {
        JSONObject json = new JSONObject(clientsMap);
        return json;
    }
}

class ServerCommands {
    private static void clientONOFF(String address, String onoff) {
        JSONObject json = new JSONObject();
        json.put("act", onoff);
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] replyBytes = json.toString().getBytes("UTF-8");
            DatagramPacket replyPacket = new DatagramPacket(replyBytes, replyBytes.length, InetAddress.getByName(address), Configurations.PORT);
            socket.send(replyPacket);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void clientON(String address) {
        clientONOFF(address, "ON");
    }

    public static void clientOFF(String address) {
        clientONOFF(address, "OFF");
    }
}

class INITSender extends AbstractScheduledService {
    public static final Logger LOGGER = Logger.getLogger(INITSender.class.getName());
    @Override
    protected void runOneIteration() throws Exception {
        LOGGER.info("[Server] Sending INIT Beacon");
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        byte[] buffer = "{act: \"INIT\"}".getBytes();
        String localAddress = InetAddress.getLocalHost().getHostAddress();
        int indexOfLastPoint = localAddress.lastIndexOf(".");
        String BroadcastAddr = localAddress.substring(0, indexOfLastPoint) + ".255";
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(BroadcastAddr),
                Configurations.PORT);
        socket.send(packet);
        socket.close();
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 60, TimeUnit.SECONDS);
    }
}

class ACKResponder extends AbstractScheduledService {
    List<ExpectedAck> expectedAcksList;

    public ACKResponder(List<ExpectedAck> expectedAcksList) {
        this.expectedAcksList = expectedAcksList;
    }

    @Override
    protected void runOneIteration() throws Exception {
        for (ExpectedAck expected : expectedAcksList) {
            if (expected.ttl <= 0) {
                DatagramSocket socket = new DatagramSocket();
                socket.send(expected.packetToResend);
                socket.close();
            } else {
                expected.ttl -= 1;
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 1, TimeUnit.SECONDS);
    }
}

class EchoServer extends Thread {
    public static final Logger LOGGER = Logger.getLogger(INITSender.class.getName());
    private byte[] buf = new byte[256];
    private double availableWatts = 1510d;
    private List<ExpectedAck> expectedAcksList;
    ClientData map;
    Map<String, Integer> wattsDeviceMap;

    public EchoServer(List<ExpectedAck> expectedAcksList) throws SocketException {
        this.expectedAcksList = expectedAcksList;
        map = ClientData.getInstance();
        wattsDeviceMap = new HashMap<>();
        wattsDeviceMap.put("LOW", 100);
        wattsDeviceMap.put("MEDIUM", 500);
        wattsDeviceMap.put("HIGH", 1000);
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
                    LOGGER.info("[Server] Received: "+received+" from Client: "+clientAddress);
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
                                LOGGER.info("[Server] Client: "+clientAddress+" is connecting for the first time");
                                double watts = jsonObject.getDouble("max_power_usage");
                                if (watts == 0d) {
                                    watts = wattsDeviceMap.get(jsonObject.getString("type"));
                                }
                                
                                // if there's room: status = on, this will be used to send an ON packet to the client
                                if (watts < availableWatts) {
                                    statusAction = "ON";
                                    availableWatts -= watts;
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
                                LOGGER.info("[Server] Added client: "+clientAddress+" to internal map, with information: "+mapJson.toString());
                                LOGGER.info("[Server] Replying to client: "+clientAddress+" with: "+replyJson.toString());
                                sendToClient(clientAddress, replyJson);
                            }
                        break;
                        case "UPDATE":
                            // if there's no room for client reply with off (should the server w8 for ACK?),
                            // else, if change is negative check if more clients can connect
                            // comparing previous wattage to new wattage
                            LOGGER.info("[Server] Received UPDATE packet from Client: "+clientAddress);
                            double new_watts = jsonObject.getDouble("active_power");
                            double old_watts = map.getClient(clientAddress).getDouble("watts");
                            double old_max_watts = map.getClient(clientAddress).getDouble("max_power_usage");
                            String currentStatus = map.getClient(clientAddress).getString("status");
                            double max_watts;
                            if (new_watts > old_max_watts)
                                max_watts = new_watts;
                            else 
                                max_watts = old_max_watts;
                            // availableWatts += old_watts;
                            // now device usage is lower so: device stays connected, maybe more device can connect
                            if (new_watts < old_watts) {
                                if (currentStatus.equals("ON")) {
                                    statusAction = "ON";
                                } else if (new_watts < availableWatts+old_watts) {
                                    statusAction = "ON";
                                }
                                if (new_watts == 0.0) {
                                    statusAction = "OFF";
                                }
                                availableWatts += (old_watts-new_watts);
                                // connect more devices if possible (first come first served)
                                Set<Map.Entry<String, JSONObject>> entrySet = map.entrySet();
                                for (Map.Entry<String, JSONObject> entry : entrySet) {
                                    double entryWatts = entry.getValue().getDouble("max_power_usage");
                                    String status = entry.getValue().getString("status");
                                    // only if the client is off
                                    if (status.equals("OFF")) {
                                        // if there's room for it
                                        if (entryWatts < availableWatts) {
                                            // update availableWatts
                                            availableWatts -= entryWatts;
                                            String newClientAddress = entry.getKey();
                                            // send ON packet to this client
                                            JSONObject replyJson = new JSONObject();
                                            replyJson.put("act", "ON");
                                            LOGGER.info("[Server] UPDATE from client: "+clientAddress+" allows: "+newClientAddress+" to also turn ON, sending packet: "+replyJson.toString());
                                            sendToClient(newClientAddress, replyJson);
                                        }
                                    }
                                }
                            }
                            else {
                                // now, if the client is ON there may not be room
                                if (currentStatus.equals("ON")) {
                                    if(new_watts < availableWatts+old_watts) {
                                        // in this case, there still is enough room
                                        statusAction = "ON";
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
                            LOGGER.info("[Server] Replying to client: "+clientAddress+" with: "+replyJson.toString());
                            sendToClient(clientAddress, replyJson);
                        break;
                        case "ACK":
                            // if an ACK is received, remove from list of expected ACKs
                            LOGGER.info("[Server] Received ACK from: "+clientAddress);
                            for (ExpectedAck expected : expectedAcksList) {
                                if (expected.clientAddress.equals(clientAddress)) {
                                    expectedAcksList.remove(expected);
                                    LOGGER.info("[Server] Removing ACK from expected ACKs list for client: "+clientAddress);
                                }
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
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] replyBytes = json.toString().getBytes("UTF-8");
            DatagramPacket replyPacket = new DatagramPacket(replyBytes, replyBytes.length, InetAddress.getByName(address), Configurations.PORT);
            expectedAcksList.add(new ExpectedAck(address, replyPacket, Configurations.TTL));
            socket.send(replyPacket);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public String inetAddressToString(InetAddress address) {
        String strAddress = address.toString();
        if (strAddress.startsWith("/"))
            return strAddress.substring(1);
        else
            return strAddress;
    }
}

// AbstractScheduledService uses Scheduler to runOneIteration every 60
// TimeUnit.SECONDS
public class UDPServer {
    public static void main(String[] args) throws SocketException {
        // lista sincronizzata client-pacchetto-ttl
        List<ExpectedAck> expectedAcksList = Collections.synchronizedList(new ArrayList<>());
        INITSender initSender = new INITSender();
        ACKResponder ackResponder = new ACKResponder(expectedAcksList);
        EchoServer echoServer = new EchoServer(expectedAcksList);
        echoServer.start();
        initSender.startAsync();
        ackResponder.startAsync();
    }
}
