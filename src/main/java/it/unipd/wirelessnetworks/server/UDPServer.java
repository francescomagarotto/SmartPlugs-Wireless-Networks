package it.unipd.wirelessnetworks.server;


import it.unipd.wirelessnetworks.server.servlet.HelloServlet;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

class Configurations {
    public static int PORT = 4210;
    public static int TTL = 5;
}

// AbstractScheduledService uses Scheduler to runOneIteration every 60 TimeUnit.SECONDS
public class UDPServer {


    public static void main(String[] args) throws Exception {
        // lista sincronizzata client-pacchetto-ttl
        List<ExpectedACK> expectedAcksList = Collections.synchronizedList(new ArrayList<>());
        INITSender initSender = new INITSender();
        ACKResponder ackResponder = new ACKResponder(expectedAcksList);
        RequestsService requestsService = new RequestsService(expectedAcksList);
        requestsService.start();
        initSender.startAsync();
        ackResponder.startAsync();
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.setBaseDir("temp");
        tomcat.setPort(8080);

        String contextPath = "/";
        String docBase = new File(".").getAbsolutePath();

        Context context = tomcat.addContext(contextPath, docBase);

        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                PrintWriter writer = resp.getWriter();

                writer.println(ClientData.getInstance().getAllClients().toString());
            }
        };

        String servletName = "pluginfo";
        String urlPattern = "/pluginfo";

        tomcat.addServlet(contextPath, servletName, servlet);

        tomcat.addServlet(contextPath, "plugcmd", new HelloServlet() {
            @Override
            protected  void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                StringBuffer jb = new StringBuffer();
                String line = null;
                try {
                    BufferedReader reader = req.getReader();
                    while ((line = reader.readLine()) != null)
                        jb.append(line);
                } catch (Exception e) { /*report an error*/ }

                try {
                    JSONObject jsonObject =  new JSONObject(jb.toString());
                    if(jsonObject.getString("act").equals("OFF")) {
                        ServerCommands.clientOFF(jsonObject.getString("ip"));
                    }
                    else if(jsonObject.getString("act").equals("ON")) {
                        ServerCommands.clientON(jsonObject.getString("ip"));
                    }
                } catch (JSONException e) {
                    // crash and burn
                    throw new IOException("Error parsing JSON request string");
                }


            }
        });
        context.addServletMappingDecoded(urlPattern, servletName);
        context.addServletMappingDecoded("/plugcmd", "plugcmd");
        tomcat.start();
        tomcat.getServer().await();
    }
}
