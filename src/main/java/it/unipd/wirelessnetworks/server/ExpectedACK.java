package it.unipd.wirelessnetworks.server;

import java.net.DatagramPacket;

public class ExpectedACK {
    public String clientAddress;
    public int commandID;
    public DatagramPacket packetToResend;
    public int ttl;

    public ExpectedACK(String address, int commandID, DatagramPacket packet, int ttl) {
        this.clientAddress = address;
        this.commandID = commandID;
        this.packetToResend = packet;
        this.ttl = ttl;
    }
}
