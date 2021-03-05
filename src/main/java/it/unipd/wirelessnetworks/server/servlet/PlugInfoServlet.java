package it.unipd.wirelessnetworks.server.servlet;

import it.unipd.wirelessnetworks.server.ClientData;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class PlugInfoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        PrintWriter writer = resp.getWriter();
        writer.println(ClientData.getInstance().getAllClients().toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String param = req.getParameter("available_watts");
        double available_watts = Double.parseDouble(param);
        ClientData.getInstance().setAvailableWatts(available_watts);
        PrintWriter writer = resp.getWriter();
        writer.println("{\"available_watts\":" + available_watts);
    }

};