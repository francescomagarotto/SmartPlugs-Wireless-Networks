package it.unipd.wirelessnetworks.server;


import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class ClientData {
    private String clientMapFile = "plugs.txt";
    private Map<String, JSONObject> clientsMap;
    private Map<String, Integer> defaultWattsDevice = new HashMap<>();
    private double availableWatts;
    private static ClientData instance;

    public static ClientData getInstance() {
        if (instance  == null) {
            instance = new ClientData();
        }
        return instance;
    }

    private ClientData() {
        String mapString = readClient();
        if (!mapString.isEmpty()) {
            JSONObject json = new JSONObject(mapString);
            clientsMap = Collections.synchronizedMap(jsonToMap(json));
        } else {
            clientsMap = Collections.synchronizedMap(new HashMap<>());
        }
        availableWatts = 3000d;

        defaultWattsDevice.put("DRYER", 3000);
        defaultWattsDevice.put("STOVE", 3000);
        defaultWattsDevice.put("OVEN", 3000);
        defaultWattsDevice.put("IRON", 2000);
        defaultWattsDevice.put("DISHWASHER", 1800);
        defaultWattsDevice.put("WASHINGMACHINE", 500);
        defaultWattsDevice.put("HUMIDIFIER", 500);
        defaultWattsDevice.put("DEHUMIDIFIER", 300);
        defaultWattsDevice.put("COFFEEMACHINE", 300);
        defaultWattsDevice.put("PC", 250);
        defaultWattsDevice.put("TV", 120);
        defaultWattsDevice.put("LAMP", 40);
        defaultWattsDevice.put("CHARGER", 5);
    }

    public Map<String, Integer> getDefaultWattsDevice() {
        return defaultWattsDevice;
    }

    public void putGetDefaultWattsDevice(String key, Integer watts) {
        defaultWattsDevice.put(key, watts);
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
        writeClient();  // saves map as string in file
    }

    public JSONObject getClient(String address) {
        return clientsMap.get(address);
    }

    public void writeClient() {
        try (PrintWriter out = new PrintWriter(clientMapFile)) {
            out.write(new JSONObject(clientsMap).toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    public String readClient() {
        StringBuilder contentBuilder = new StringBuilder();

        try (Stream<String> stream = Files.lines( Paths.get(clientMapFile), StandardCharsets.UTF_8))
        {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException e)
        {
            return "";
        }

        return contentBuilder.toString();
    }

    public static Map<String, JSONObject> jsonToMap(JSONObject json) {
        Map<String, JSONObject> map = new HashMap<>();

        Iterator<?> keys = json.keys();

        while( keys.hasNext() ){
            String key = (String) keys.next();
            JSONObject value = json.getJSONObject(key);
            map.put(key, value);
        }
        return map;
    }

    public Set<Map.Entry<String, JSONObject>> entrySet() {
        return clientsMap.entrySet();
    }

    public JSONObject getAllClients() {
        double currentConsume = 0.0;
        List<JSONObject> plugs = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : clientsMap.entrySet()) {
            JSONObject plg = new JSONObject(entry.getValue(), JSONObject.getNames(entry.getValue()));
            plg.put("address", entry.getKey());
            plugs.add(plg);
            currentConsume += plg.getDouble("watts");
        }
        JSONObject json = new JSONObject();
        json.put("availableWatts", availableWatts);
        json.put("currentConsume", currentConsume);
        json.put("plugs", plugs);
        return json;
    }
}
