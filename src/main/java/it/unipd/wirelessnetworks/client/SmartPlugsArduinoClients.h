#include <Arduino.h>

#include <WiFi.h>
#include <WiFiMulti.h>
#include <WiFiClientSecure.h>

#include <WebSocketClient.h>
#include <thread>
#include <chrono>
#include <time.h>
#include <stdlib.h>
#include <string.h>

WebSocketsClient webSocket;
string id, name, watts;
srand (time(NULL)); // TODO: maybe to be done somewhere else???

#define USE_SERIAL Serial1

void hexdump(const void *mem, uint32_t len, uint8_t cols = 16) {
	const uint8_t* src = (const uint8_t*) mem;
	USE_SERIAL.printf("\n[HEXDUMP] Address: 0x%08X len: 0x%X (%d)", (ptrdiff_t)src, len, len);
	for(uint32_t i = 0; i < len; i++) {
		if(i % cols == 0) {
			USE_SERIAL.printf("\n[0x%08X] 0x%08X: ", (ptrdiff_t)src, i);
		}
		USE_SERIAL.printf("%02X ", *src);
		src++;
	}
	USE_SERIAL.printf("\n");
}

void webSocketEvent(WStype_t type, uint8_t * payload, size_t length) {

	switch(type) {
		case WStype_DISCONNECTED:
			USE_SERIAL.printf("[CLIENT-" + id + "] Closed connection\n");
			break;
		case WStype_CONNECTED:
			USE_SERIAL.printf("[CLIENT-" + id + "] Connected to server, waiting for response...\n");

			// send message to server when Connected
			webSocket.sendTXT("{'id':'" + id + "', 'name': '" + name + "', 'watts': '" + watts + "'}");
			break;
		case WStype_TEXT:
			USE_SERIAL.printf("[WSc] get text: %s\n", payload);

			// TODO: how the fuck to turn string to json? where the fuck is the string received by the server? in payload?
            JSONObject obj = new JSONObject(/*TODO: INSERT HERE STRING RECEIVED BY SERVER*/);
            string messageId = obj.getString("id");
            string type = obj.getString("type");
            // if the message sent from the server is to this client
            if (messageId.compare(id) == 0) {
                switch (str2int(type)) {
                    case str2int("OK"):
                        // server recognizes on its own the client is already connected and disconnects it
                        // here the client waits for a while and disconnects and tells the server so
                        this_thread::sleep_for(chrono::seconds(rand() % 10 + 1));   // TODO: this_thread:: & chrono:: may not be needed
                        sendData(data);("{'id':'" + id + "', 'name': '" + name + "', 'watts': '" + watts + "'}");
                        break;
                    case str2int("EXCEEDED"):
                        // here the client waits for a while and tries to reconnect
                        sleep_for(seconds(rand() % 10 + 1));    // this needs to be done somewhere ;
                        sendData("{'id':'" + id + "', 'name': '" + name + "', 'watts': '" + watts + "'}");
                        break;
                    case str2int("DISCONNECT"):
                        // here the client closes the connection with the server
                        break;
                }
            }
            break;
		case WStype_BIN:
		case WStype_ERROR:			
		case WStype_FRAGMENT_TEXT_START:
		case WStype_FRAGMENT_BIN_START:
		case WStype_FRAGMENT:
		case WStype_FRAGMENT_FIN:
			break;
	}

}

void setup() {
    // TODO: ALL OF THIS NEEDS TO BE DECIDED

	// USE_SERIAL.begin(921600);
	USE_SERIAL.begin(115200);

	//Serial.setDebugOutput(true);
	USE_SERIAL.setDebugOutput(true);

	USE_SERIAL.println();
	USE_SERIAL.println();
	USE_SERIAL.println();

	for(uint8_t t = 4; t > 0; t--) {
		USE_SERIAL.printf("[SETUP] BOOT WAIT %d...\n", t);
		USE_SERIAL.flush();
		delay(1000);
	}

	WiFiMulti.addAP("SSID", "passpasspass");

	//WiFi.disconnect();
	while(WiFiMulti.run() != WL_CONNECTED) {
		delay(100);
	}

	// server address, port and URL
	webSocket.begin("192.168.0.123", 81, "/");

	// event handler
	webSocket.onEvent(webSocketEvent);

	// use HTTP Basic Authorization this is optional remove if not needed
	webSocket.setAuthorization("user", "Password");

	// try ever 5000 again if connection has failed
	webSocket.setReconnectInterval(5000);

}

void loop() {
	webSocket.loop();
}