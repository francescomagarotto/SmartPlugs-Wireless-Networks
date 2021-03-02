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
import java.util.Map;
import java.util.concurrent.TimeUnit;

class Configurations {
    public static int PORT = 44505;
}

// AbstractScheduledService uses Scheduler to runOneIteration every 60 TimeUnit.SECONDS
public class UDPServer extends AbstractScheduledService {

    @Override
    protected void runOneIteration() throws Exception {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        byte[] buffer = "{act: \"INIT\"}".getBytes();
        DatagramPacket packet
                = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), Configurations.PORT);
        socket.send(packet);
        socket.close();
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 60, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws SocketException {
        EchoServer echoServer = new EchoServer();
        echoServer.start();
    }
}

class EchoServer extends Thread {
    private DatagramSocket socket;
    private byte[] buf = new byte[256];
	private int availableWatts = 3000;
    Map<String, JSONObject> map;

    public EchoServer() throws SocketException {
        socket = new DatagramSocket(Configurations.PORT);
        map = Collections.synchronizedMap(new HashMap<>());
    }

    public void run() {
        while (true) {
            DatagramPacket packet
                    = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);	// receiving packet from client: can be either ACK to INIT, or INCREASE/DECREASE Update
            } catch (IOException e) {
                e.printStackTrace();
            }
			
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            packet = new DatagramPacket(buf, buf.length, address, port);
			
            String received
                    = new String(packet.getData(), 0, packet.getLength());
            JSONObject jsonObject = new JSONObject(received);
			
            String statusAction = "off";
            int defaultClientWattage = 0;

			DatagramPacket replyPacket;
            switch (jsonObject.getString("act")) {
                case "INIT":
					if(map.containsKey(address.toString())) {
                        // TODO: SE CLIENT NON RISPONDE AD INIT PER TOT VOLTE DI FILA: CLIENT IS OUT
						// if i already have it, this message lets me know the host is still connected
						// so if this case doesn't happen for a given host i should remove it from the map
					} else {
						// if absent, add to map<address, JSON>, reply with on/off based on wether or not there's room for it
						int watts = jsonObject.getInt("max_power_usage");
						if (watts == 0) {
                            defaultClientWattage = wattsDeviceMap[jsonObject.getString("type")];
						}

						if (watts < availableWatts) {
                            statusAction = "on";
                            availableWatts -= watts;
                        }
						// this json is for the server's internal map of clients
						JSONObject mapJson = Json.createObjectBuilder()
							.add("type", jsonObject.getString("type"))
                            .add("watts", jsonObject.getInt("max_power_usage"))
							.add("status", statusAction)
						.build();
						map.put(address.toString(), mapJson);
					}
                    break;
				case "UPDATE":
					// if there's no room for client reply with off (should the server w8 for ACK?), else if change is negative, if now there's 
					// room for another client, send on to that client
                    int new_watts = jsonObject.getInt("active_power");
                    int old_watts = map[jsonObject.getAddress().toString()].getInt("max_power_usage");
                    availableWatts += old_watts;
                    if (new_watts < availableWatts) {
                        statusAction = "on";
                        availableWatts -= new_watts;
                        if (old_watts > new_watts) {
                            Set<Entry<String, JSONObject>> entrySet = map.entrySet();
                            for(Entry<String, JSONObject> entry : entrySet) {
                                if(entry.getValue()["max_power_usage"] < availableWatts) {
                                    availableWatts -= new_watts;
                                    String newClientAddress = entry.getKey();
                                    // should send ON packet to this host too
                                    // TODO: MANDARE PACCHETTI AD ALTRI CLIENT SE SI LIBERA POSTO (QUANDO CLIENT SI DISCONETTONO O QUANDO CLIENT CONSUMANO MENO)
                                }
                            }
                        }
                    } 
                    else
                        statusAction = "off";
                    map[jsonObject.getAddress().toString()].setString("status", statusAction);
                    map[jsonObject.getAddress().toString()].setInt("max_power_usage", new_watts);
                    break;
            }
            try {
                // this json is for the reply the server sends to the client
                // TODO: ASPETTARE PACCHETTI DI ACK DA PARTE DEI CLIENT E REINVIARE SE NON ARRIVANO (THREAD)
                JSONObject replyJson = Json.createObjectBuilder()
                    .add("act", statusAction)
                .build();
                if (defaultClientWattage != 0) {
                    replyJson.setInt("max_power_usage", defaultClientWattage);
                }
                byte[] replyBytes = replyJson.toString().getBytes("UTF-8");
                replyPacket = new DatagramPacket(replyBytes, replyBytes.length); 
                socket.send(replyPacket);
                // start thread that w8s for response and resends packet if not
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}

/*
MANCA:

- SE CLIENT NON RISPONDE AD INIT PER TOT VOLTE DI FILA: CLIENT IS OUT
- MANDARE PACCHETTI AD ALTRI CLIENT SE SI LIBERA POSTO (QUANDO CLIENT SI DISCONETTONO O QUANDO CLIENT CONSUMANO MENO)
- ASPETTARE PACCHETTI DI ACK DA PARTE DEI CLIENT E REINVIARE SE NON ARRIVANO (THREAD)

*/
