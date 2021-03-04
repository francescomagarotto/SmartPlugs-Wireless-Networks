package it.unipd.wirelessnetworks.server;

import java.net.DatagramPacket;

public class ExpectedACK {
    public String clientAddress;
    public DatagramPacket packetToResend;
    public int ttl;

    public ExpectedACK(String address, DatagramPacket packet, int ttl) {
        this.clientAddress = address;
        this.packetToResend = packet;
        this.ttl = ttl;
    }
}
