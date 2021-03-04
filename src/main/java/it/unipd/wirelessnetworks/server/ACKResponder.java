package it.unipd.wirelessnetworks.server;

import com.google.common.util.concurrent.AbstractScheduledService;

import java.net.DatagramSocket;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ACKResponder extends AbstractScheduledService {
    List<ExpectedACK> expectedAcksList;

    public ACKResponder(List<ExpectedACK> expectedAcksList) {
        this.expectedAcksList = expectedAcksList;
    }

    @Override
    protected void runOneIteration() throws Exception {
        for (ExpectedACK expected : expectedAcksList) {
            if (expected.ttl <= 0) {
                DatagramSocket socket = new DatagramSocket();
                socket.send(expected.packetToResend);
                socket.close();
                expectedAcksList.remove(expected);
            } else {
                expected.ttl -= 1;
            }
        }
    }

    @Override
    protected AbstractScheduledService.Scheduler scheduler() {
        return AbstractScheduledService.Scheduler.newFixedRateSchedule(0, 1, TimeUnit.SECONDS);
    }
}
