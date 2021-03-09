package it.unipd.wirelessnetworks.server;

import java.net.DatagramPacket;

public class ExpectedACK {
    public String clientAddress;
    public String timestamp;
    public DatagramPacket packetToResend;
    public int ttl;

    public ExpectedACK(String address, String timestamp, DatagramPacket packet, int ttl) {
        this.clientAddress = address;
        this.timestamp = timestamp;
        this.packetToResend = packet;
        this.ttl = ttl;
    }
}
