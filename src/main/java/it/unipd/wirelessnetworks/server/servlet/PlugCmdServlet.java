package it.unipd.wirelessnetworks.server.servlet;

import it.unipd.wirelessnetworks.server.INITSender;
import it.unipd.wirelessnetworks.server.ServerCommands;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Logger;

public class PlugCmdServlet extends HttpServlet
{
    public static final Logger LOGGER = Logger.getLogger(PlugCmdServlet.class.getName());
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String body = req.getReader().lines()
                .reduce("", (accumulator, actual) -> accumulator + actual);
        LOGGER.info(body);
        try {
            JSONObject jsonObject = new JSONObject(body);
            if (jsonObject.getString("act").equals("OFF")) {
                ServerCommands.clientOFF(jsonObject.getString("ip"));
            } else if (jsonObject.getString("act").equals("ON")) {
                ServerCommands.clientON(jsonObject.getString("ip"));
            }
        } catch (JSONException e) {
            // crash and burn
            throw new IOException("Error parsing JSON request string");
        }


    }
}