package it.unipd.wirelessnetworks.server;


import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class ClientData {
    private Map<String, JSONObject> clientsMap;
    private double availableWatts;
    private static ClientData instance;

    public static ClientData getInstance() {
        if (instance  == null) {
            instance = new ClientData();
        }
        return instance;
    }

    private ClientData() {
        clientsMap = Collections.synchronizedMap(new HashMap<>());
        availableWatts = 3000d;
    }

    public double getAvailableWatts() {
        return availableWatts;
    }

    public void setAvailableWatts(double aW) {
        availableWatts = aW;
    }

    public boolean containsKey(String address) {
        return clientsMap.containsKey(address);
    }

    public void putClient(String address, JSONObject data) {
        clientsMap.put(address, data);
    }

    public JSONObject getClient(String address) {
        return clientsMap.get(address);
    }

    public Set<Map.Entry<String, JSONObject>> entrySet() {
        return clientsMap.entrySet();
    }

    public JSONObject getAllClients() {
        JSONObject json = new JSONObject(clientsMap);
        return json;
    }
}
