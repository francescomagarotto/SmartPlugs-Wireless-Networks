package it.unipd.wirelessnetworks.server;


import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServerCommands {
    private static void clientONOFF(String address, String onoff) {
        ClientData client = ClientData.getInstance();
        JSONObject data = client.getClient(address);
        int status = 0;
        if (onoff.equals("ON"))
            status = 1;
        data.put("status", status);
        client.putClient(address, data);
        // json to be sent to client
        JSONObject json = new JSONObject();
        Long timestamp = System.currentTimeMillis();
        json.put("act", onoff);
        json.put("timestamp", timestamp.toString());
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] replyBytes = json.toString().getBytes("UTF-8");
            DatagramPacket replyPacket = new DatagramPacket(replyBytes, replyBytes.length, InetAddress.getByName(address), Configurations.PORT);
            ExpectedACK e = new ExpectedACK(address, timestamp.toString(), replyPacket, 7);
            AckListSingleton.getInstance().getExpectedAckList().add(e);
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

