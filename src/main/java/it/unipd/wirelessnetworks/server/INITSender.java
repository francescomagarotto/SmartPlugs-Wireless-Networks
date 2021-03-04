package it.unipd.wirelessnetworks.server;

import com.google.common.util.concurrent.AbstractScheduledService;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class INITSender extends AbstractScheduledService {
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
    protected AbstractScheduledService.Scheduler scheduler() {
        return AbstractScheduledService.Scheduler.newFixedRateSchedule(0, 60, TimeUnit.SECONDS);
    }
}
