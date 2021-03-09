package it.unipd.wirelessnetworks.server;

import org.json.JSONObject;

import java.util.*;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

public class ClientData {
    private String xmlFilePath = "plugs.xml";
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
        File file = new File(xmlFilePath);
        clientsMap = Collections.synchronizedMap(new HashMap<>());
        if (file.length()!=0) {
            readClient();   // sets a few fields in clientsMap
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
        writeClient(address, data);  // saves map as string in file
    }

    public JSONObject getClient(String address) {
        return clientsMap.get(address);
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

    public void writeClient(String address, JSONObject data) {
        String type = data.getString("type");
        double maxPower = data.getDouble("max_power_usage");

        try {
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();
            Document document;

            File file = new File(xmlFilePath);

            if (file.length()!=0) {
                document = documentBuilder.parse(file);

                NodeList nodeList = document.getElementsByTagName("ip");

                // UPDATES IF ALREADY IS SAVED ----------------------------------------------------
                for (int itr = 0; itr < nodeList.getLength(); itr++)
                {
                    String addr = nodeList.item(itr).getTextContent();
                    if (addr.equals(address)) {
                        Node plugNode = nodeList.item(itr).getParentNode();
                        NodeList fields = plugNode.getChildNodes();
                        fields.item(1).setTextContent(Double.toString(maxPower));
                        fields.item(2).setTextContent(type);

                        TransformerFactory transformerFactory = TransformerFactory.newInstance();
                        Transformer transformer = transformerFactory.newTransformer();
                        DOMSource domSource = new DOMSource(document);
                        StreamResult streamResult = new StreamResult(new File(xmlFilePath));

                        transformer.transform(domSource, streamResult);

                        return;
                    }
                }
                // --------------------------------------------------------------------------------
                Node root = document.getFirstChild();

                Element plugEl = document.createElement("plug");
                root.appendChild(plugEl);

                Element addrEl = document.createElement("ip");
                addrEl.appendChild(document.createTextNode(address));
                plugEl.appendChild(addrEl);

                Element maxEl = document.createElement("maxpower");
                maxEl.appendChild(document.createTextNode(Double.toString(maxPower)));
                plugEl.appendChild(maxEl);

                Element typeEl = document.createElement("type");
                typeEl.appendChild(document.createTextNode(type));
                plugEl.appendChild(typeEl);

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource domSource = new DOMSource(document);
                StreamResult streamResult = new StreamResult(new File(xmlFilePath));

                transformer.transform(domSource, streamResult);
            } else {
                document = documentBuilder.newDocument();

                Element root = document.createElement("plugs");
                document.appendChild(root);

                Element plugEl = document.createElement("plug");
                root.appendChild(plugEl);

                Element addrEl = document.createElement("ip");
                addrEl.appendChild(document.createTextNode(address));
                plugEl.appendChild(addrEl);

                Element maxEl = document.createElement("maxpower");
                maxEl.appendChild(document.createTextNode(Double.toString(maxPower)));
                plugEl.appendChild(maxEl);

                Element typeEl = document.createElement("type");
                typeEl.appendChild(document.createTextNode(type));
                plugEl.appendChild(typeEl);

                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                DOMSource domSource = new DOMSource(document);
                StreamResult streamResult = new StreamResult(new File(xmlFilePath));

                transformer.transform(domSource, streamResult);
            }
        } catch (Exception e) {e.printStackTrace();}
    }

    public void readClient() {
        try {
            File file = new File(xmlFilePath);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(file);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("plug");

            for (int itr = 0; itr < nodeList.getLength(); itr++)
            {
                Node node = nodeList.item(itr);
                if (node.getNodeType() == Node.ELEMENT_NODE)
                {
                    Element eElement = (Element) node;

                    String plugIp = eElement.getElementsByTagName("ip").item(0).getTextContent();
                    double plugMaxPow = Double.parseDouble(eElement.getElementsByTagName("maxpower").item(0).getTextContent());
                    String plugType = eElement.getElementsByTagName("type").item(0).getTextContent();
                    JSONObject json = new JSONObject();
                    json.put("max_power_usage", plugMaxPow);
                    json.put("type", plugType);
                    clientsMap.put(plugIp, json);
                }
            }
        } catch (Exception e) {e.printStackTrace();}
    }
}
