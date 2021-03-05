package it.unipd.wirelessnetworks.server;


import it.unipd.wirelessnetworks.server.servlet.PlugCmdServlet;
import it.unipd.wirelessnetworks.server.servlet.PlugInfoServlet;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

class Configurations {
    public static int PORT = 4210;
    public static int TTL = 5;
}

// AbstractScheduledService uses Scheduler to runOneIteration every 60 TimeUnit.SECONDS
public class UDPServer {


    public static void main(String[] args) throws Exception {
        final Logger LOGGER = Logger.getLogger(UDPServer.class.getName());
        // lista sincronizzata client-pacchetto-ttl
        List<ExpectedACK> expectedAcksList = Collections.synchronizedList(new ArrayList<>());
        INITSender initSender = new INITSender();
        ACKResponder ackResponder = new ACKResponder(expectedAcksList);
        RequestsService requestsService = new RequestsService(expectedAcksList);
        requestsService.start();
        initSender.startAsync();
        ackResponder.startAsync();

        ClientData.getInstance().addObserver(requestsService);
        Tomcat tomcat = new Tomcat();
        tomcat.setPort(8080);
        tomcat.setBaseDir("temp");

        String contextPath = "/";
        String docBase = new File(".").getAbsolutePath();

        Context context = tomcat.addContext(contextPath, docBase);

        tomcat.addServlet(contextPath, "pluginfo", new PlugInfoServlet());
        tomcat.addServlet(contextPath, "plugcmd", new PlugCmdServlet());
        context.addServletMappingDecoded("/pluginfo", "pluginfo");
        context.addServletMappingDecoded("/plugcmd", "plugcmd");
        tomcat.start();
        tomcat.getServer().await();
    }
}
