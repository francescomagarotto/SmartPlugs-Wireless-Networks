package it.unipd.wirelessnetworks.client;

import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.lang.Math;


import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

public class SmartPlugsClients extends WebSocketClient{

    private String id, name, watts;

    public SmartPlugsClients(URI serverUri, Draft draft, String id, String name, String watts) {
        super(serverUri, draft);
        this.id = id;
        this.name = name;
        this.watts = watts;
	}

	public SmartPlugsClients(URI serverURI, String id, String name, String watts) {
        super(serverURI);
        this.id = id;
        this.name = name;
        this.watts = watts;
	}

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		send("{'id':'"+id+"', 'name': '"+name+"', 'watts': '"+watts+"'}");
		System.out.println("[CLIENT-"+id+"] Connected to server, waiting for response...");
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		System.out.println("[CLIENT-"+id+"] Closed connection with exit code " + code + " additional info: " + reason);
	}

	@Override
	public void onMessage(String message) {
        JSONObject obj = new JSONObject(message);
        String messageId = obj.getString(id);
        String type = obj.getString("type");
        // if the message sent from the server is to this client
        if(messageId.equals(id)) {  
            switch(type) {
                case "OK":
                    // server recognizes on its own the client is already connected and disconnects it
                    try {
                        Thread.sleep((int)(Math.random()*10000));
                    } catch (Exception e) {
                        System.out.println("[CLIENT-"+id+"] Something went wrong while connected to grid "+e.getMessage());
                    }
                    
                    send("{'id':'"+id+"', 'name': '"+name+"', 'watts': '"+watts+"'}");
                    break;
                case "EXCEEDED":
                    try {
                        Thread.sleep((int)(Math.random()*10000));
                    } catch (Exception e) {
                        System.out.println("[CLIENT-"+id+"] Something went wrong while waiting to retry to connect "+e.getMessage());
                    }
                    send("{'id':'"+id+"', 'name': '"+name+"', 'watts': '"+watts+"'}");
                    break;
                case "DISCONNECT":
                    close();
                    break;
            }
        }
	}

	@Override
	public void onMessage(ByteBuffer message) {
		System.out.println("[CLIENT-"+id+"] Received message in an unsupported format");
	}

	@Override
	public void onError(Exception ex) {
		System.err.println("[CLIENT-"+id+"] An error occurred:" + ex);
	}

    public static void main(String[] args) {
        String[] names = {"dishwasher", "fridge", "washing machine", "tv", "computer", "phon", "oven", "microwave oven", "electric stove", "vacuum"};

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        for(int i=0; i<10; i++) {
            
            
            executorService.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String id = String.valueOf(Thread.currentThread().getId());
                            String name = names[(int)(Math.random()*10)];
                            String watts = String.valueOf((int)(Math.random()*2000)+1);
                            WebSocketClient client = new SmartPlugsClients(new URI("ws://localhost:8887"), id, name, watts);
                            client.connect();
                        } catch (Exception e) { 
                            e.printStackTrace(); 
                        }
                    }
                }
            );
        }

    }
}

/*
// preparing message to send to the server
                        JSONObject obj = new JSONObject();
                        int watt = (int)(Math.random()*2000)+1; // between 1 and 2000
                        obj.put("watt", Integer.valueOf(watt));
                        String message = obj.toString();
                        
                        // connection and sending message
                        Socket socket = new Socket(InetAddress.getLoopbackAddress(), 5056);
                        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                        outputStream.writeBytes(message);

                        // receving response and corresponding behavior
                        String response = inputStream.readUTF();
                        JSONObject responseJSON = new JSONObject(response);
                        String action = responseJSON.getString("type");

                        switch(action) {
                            case "OK":
                                // connects to power grid
                                // 
                                int value = responseJSON.getInt("next");
                                break;
                            case "EXCEEDED":
                                
                                // OGNI NEXT TEMPO PROVO A RICONNETTERMI
                                // hold on, notify user
                                break;
                        }

                        inputStream.close();
                        outputStream.close();
                        socket.close();
*/