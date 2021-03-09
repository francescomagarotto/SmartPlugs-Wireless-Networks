package it.unipd.wirelessnetworks.server;

import com.google.common.util.concurrent.AbstractScheduledService;

import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ACKResponder extends AbstractScheduledService {
    List<ExpectedACK> expectedAcksList;

    public ACKResponder() {
        this.expectedAcksList = AckListSingleton.getInstance().getExpectedAckList();
    }

    @Override
    protected void runOneIteration() throws Exception {
        List<ExpectedACK> toRemove = new ArrayList<>();
        for (ExpectedACK expected : expectedAcksList) {
            if (expected.ttl <= 0) {
                DatagramSocket socket = new DatagramSocket();
                socket.send(expected.packetToResend);
                socket.close();
                toRemove.add(expected);
            } else {
                expected.ttl -= 1;
            }
        }
        expectedAcksList.removeAll(toRemove);
    }

    @Override
    protected AbstractScheduledService.Scheduler scheduler() {
        return AbstractScheduledService.Scheduler.newFixedRateSchedule(0, 1, TimeUnit.SECONDS);
    }
}
