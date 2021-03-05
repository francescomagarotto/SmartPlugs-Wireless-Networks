package it.unipd.wirelessnetworks.server;


import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServerCommands {
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

