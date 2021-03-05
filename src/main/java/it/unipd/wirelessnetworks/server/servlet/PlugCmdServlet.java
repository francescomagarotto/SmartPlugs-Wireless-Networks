package it.unipd.wirelessnetworks.server.servlet;

import it.unipd.wirelessnetworks.server.ServerCommands;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

public class PlugCmdServlet extends HttpServlet
{
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        StringBuffer jb = new StringBuffer();
        String line = null;
        try {
            BufferedReader reader = req.getReader();
            while ((line = reader.readLine()) != null)
                jb.append(line);
        } catch (Exception e) { /*report an error*/ }

        try {
            JSONObject jsonObject = new JSONObject(jb.toString());
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