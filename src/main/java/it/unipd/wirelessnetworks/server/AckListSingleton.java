package it.unipd.wirelessnetworks.server;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AckListSingleton {

    private List<ExpectedACK> expectedAckList;
    private static AckListSingleton ackListSingleton;
    private  AckListSingleton() {
        expectedAckList = Collections.synchronizedList(new ArrayList<>());
    }
    public static AckListSingleton getInstance() {
        if(ackListSingleton == null) {
            ackListSingleton = new AckListSingleton();
        }
        return ackListSingleton;
    }

    public List<ExpectedACK> getExpectedAckList() {
        return expectedAckList;
    }
}
