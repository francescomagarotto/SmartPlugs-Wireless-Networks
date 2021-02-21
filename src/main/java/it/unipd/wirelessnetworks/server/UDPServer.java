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
    Map<String, String> map;

    public EchoServer() throws SocketException {
        socket = new DatagramSocket(Configurations.PORT);
        map = Collections.synchronizedMap(new HashMap<>());
    }

    public void run() {
        while (true) {
            DatagramPacket packet
                    = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            packet = new DatagramPacket(buf, buf.length, address, port);
            String received
                    = new String(packet.getData(), 0, packet.getLength());
            JSONObject jsonObject = new JSONObject(received);
            switch (jsonObject.getString("act")) {
                case "INFO":
                    map.putIfAbsent(address.toString(), jsonObject.getString("name"));
                    break;
                case "ON":

            }

            try {
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
