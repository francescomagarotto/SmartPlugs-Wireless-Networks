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
			
			DatagramPacket replyPacket;
            switch (jsonObject.getString("act")) {
                case "ACK":
					if(map.containsKey(address.toString())) {
						// if i already have it, this message lets me know the host is still connected
						// so if this case doesn't happen for a given host i should remove it from the map
					} else {
						//if absent, add to map<address, JSON>, reply with on/off based on wether or not there's room for it
						int watts = jsonObject.getInt("last_watt");
						if (watts == 0) {
							watts = wattsDeviceMap[jsonObject.getString("device_type")];
						}
						String statusAction = "off";
						if (watts < availableWatts) {
							statusAction = "on";
						// this json is for the server's internal map of clients
						JSONObject mapJson = Json.createObjectBuilder()
							.add("name", jsonObject.getString("name"))
							.add("device_type", jsonObject.getString("device_type"))
							.add("status", statusAction)
						.build();
						// this json is for the reply the server sends to the client
						JSONObject replyJson = Json.createObjectBuilder()
							.add("act", statusAction)
						.build();
						map.put(address.toString(), mapJson);
						byte[] replyBytes = replyJson.toString().getBytes("UTF-8");
						replyPacket = new DatagramPacket(replyBytes, replyBytes.length);
					}
                    break;
				case "CHANGE":
					//if there's no room for client reply with off (should the server w8 for ACK?), else if change is negative, if now there's 
					//room for another client, send on to that client
                    break;
            }

            try {
                socket.send(replyPacket);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}

/*
server broadcast sort of beacon
INIT
ON
OFF

ogni tot minuti inviare INIT

i thread mi servono per non bloccare e inviare richieste durante i periodi di w8 for ack


SERVER PARTE, INVIA INIT A TUTTI

TUTTI INVIANO ACK CON HOSTNAME E INDICANO ULTIMO MAX CONSUMO, STATO

SE ULTIMO = 0 SERVER USA VALORE DI RIF. IN BASE AL DISPOSITIVO
TIENE TRACCIA DI TUTTI DEVO RICORDARE SE ON O OFF. CHE SONO I COMANDI CHE INVIO AL CLIENT IN BASE A SE CI STA O NO.

OGNI TOT IL SERVER INIT

TTL ai dispositivi

*/
