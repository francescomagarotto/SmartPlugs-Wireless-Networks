package it.unipd.wirelessnetworks.server;

import com.google.common.util.concurrent.AbstractScheduledService;
import org.json.JSONObject;

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

class INITSender extends AbstractScheduledService {
    private static final Logger LOGGER = Logger.getLogger(INITSender.class.getName());

    @Override
    protected void runOneIteration() throws Exception {
        LOGGER.info("Executed");
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        byte[] buffer = "{act: \"INIT\"}".getBytes();
        String localAddress = InetAddress.getLocalHost().getHostAddress();
        LOGGER.info(localAddress);
        int indexOfLastPoint = localAddress.lastIndexOf(".");
        String BroadcastAddr = localAddress.substring(0, indexOfLastPoint) + ".255";
        LOGGER.info(BroadcastAddr);
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
                DatagramSocket socket = new DatagramSocket(Configurations.PORT,
                        InetAddress.getByName(expected.clientAddress));
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
    private DatagramSocket socket;
    private byte[] buf = new byte[256];
    private int availableWatts = 3000;
    private List<ExpectedAck> expectedAcksList;
    Map<String, JSONObject> map;
    Map<String, Integer> wattsDeviceMap;

    public EchoServer(List<ExpectedAck> expectedAcksList) throws SocketException {
        this.expectedAcksList = expectedAcksList;
        socket = new DatagramSocket(Configurations.PORT);
        map = Collections.synchronizedMap(new HashMap<>());
        wattsDeviceMap = new HashMap<>();
        wattsDeviceMap.put("LOW", 100);
        wattsDeviceMap.put("MEDIUM", 500);
        wattsDeviceMap.put("HIGH", 1000);
    }

    public void run() {
        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet); // receiving packet from client
            } catch (IOException e) {
                e.printStackTrace();
            }
            // lista: client-pacchetto-ttl
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            packet = new DatagramPacket(buf, buf.length, address, port);

            String received = new String(packet.getData(), 0, packet.getLength());
            JSONObject jsonObject = new JSONObject(received);

            String statusAction = "off";
            int defaultClientWattage = 0;

            DatagramPacket replyPacket;
            switch (jsonObject.getString("act")) {
                case "INIT":
                    if (map.containsKey(address.toString())) {
                        // TODO: SE CLIENT NON RISPONDE AD INIT PER TOT VOLTE DI FILA: CLIENT IS OUT
                        // contatore = 3, contatore -1 in sto caso, if contatore = 0 tolgo da map
                        // if i already have it, this message lets me know the host is still connected
                        // so if this case doesn't happen for a given host i should remove it from the
                        // map
                    } else {
                        // if absent add to map<address, JSON>, reply with on/off based on wether or not
                        // there's room for it
                        double watts = jsonObject.getDouble("max_power_usage");
                        if (watts == 0d) {
                            defaultClientWattage = wattsDeviceMap.get(jsonObject.getString("type"));
                        }

                        if (watts < availableWatts) {
                            statusAction = "on";
                            availableWatts -= watts;
                        }
                        // this json is for the server's internal map of clients
                        JSONObject mapJson = new JSONObject();
                        mapJson.put("type", jsonObject.getString("type"));
                        mapJson.put("watts", jsonObject.getInt("max_power_usage"));
                        mapJson.put("status", statusAction);
                        map.put(address.toString(), mapJson);
                    }
                    break;
                case "UPDATE":
                    // if there's no room for client reply with off (should the server w8 for ACK?),
                    // else if change is negative, if now there's
                    // room for another client, send on to that client
                    int new_watts = jsonObject.getInt("active_power");
                    int old_watts = map.get(address.toString()).getInt("max_power_usage");
                    availableWatts += old_watts;
                    if (new_watts < availableWatts) {
                        statusAction = "on";
                        availableWatts -= new_watts;
                        if (old_watts > new_watts) {
                            Set<Map.Entry<String, JSONObject>> entrySet = map.entrySet();
                            for (Map.Entry<String, JSONObject> entry : entrySet) {
                                if ((int) entry.getValue().get("max_power_usage") < availableWatts) {
                                    availableWatts -= new_watts;
                                    String newClientAddress = entry.getKey();
                                    // should send ON packet to this host too
                                    try {
                                        // this json is for the reply the server sends to the client
                                        JSONObject replyJson = new JSONObject();
                                        replyJson.put("act", statusAction);
                                        byte[] replyBytes = replyJson.toString().getBytes("UTF-8");
                                        replyPacket = new DatagramPacket(replyBytes, replyBytes.length);
                                        try (DatagramSocket socket = new DatagramSocket(Configurations.PORT,
                                                InetAddress.getByName(address.toString()))) {
                                            expectedAcksList.add(
                                                    new ExpectedAck(newClientAddress, replyPacket, Configurations.TTL));
                                            socket.send(replyPacket);
                                        }
                                        // start thread that w8s for response and resends packet if not
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    } else {
                        statusAction = "off";
                    }
                    JSONObject updatedJson = map.get(address.toString());
                    updatedJson.put("status", statusAction);
                    updatedJson.put("max_power_usage", new_watts);
                    map.put(address.toString(), updatedJson);
                    break;
                case "ACK":
                    for (ExpectedAck expected : expectedAcksList) {
                        if (expected.clientAddress.equals(address.toString())) {
                            expectedAcksList.remove(expected);
                        }
                    }
                    break;
            }
            try {
                try (DatagramSocket socket = new DatagramSocket(Configurations.PORT,
                        InetAddress.getByName(address.toString()))) {
                    JSONObject replyJson = new JSONObject();
                    replyJson.put("act", statusAction);
                    if (defaultClientWattage != 0) {
                        replyJson.put("max_power_usage", defaultClientWattage);
                    }
                    byte[] replyBytes = replyJson.toString().getBytes("UTF-8");
                    replyPacket = new DatagramPacket(replyBytes, replyBytes.length);
                    expectedAcksList.add(new ExpectedAck(address.toString(), replyPacket, Configurations.TTL));
                    socket.send(replyPacket);
                }
                // start thread that w8s for response and resends packet if not
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
