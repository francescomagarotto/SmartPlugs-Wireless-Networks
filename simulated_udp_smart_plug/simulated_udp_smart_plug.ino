#include <ESP8266WiFi.h>
#include <ArduinoJson.h>
#include <WiFiUdp.h>
#include <arduino-timer.h>
#include <SoftwareSerial.h>
#include <ModbusMaster.h>

auto timer = timer_create_default();
const char* ssid = "Fritz! Network";
const char* password = "Oliver2016Artu2015";
const char* type = "DISHWASHER";
double max_usage = 0.0;

IPAddress serverIPAddress = NULL;
int serverPort = 4210;

uint8_t result; uint16_t data[6];
WiFiUDP Udp;
unsigned int localUdpPort = 4210;
char incomingPacket[256];
void setup() {
  Serial.begin(115200);
  Serial.printf("Connecting to %s ", ssid);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    Serial.print(".");
  }
  Serial.println(" connected");
  Udp.begin(localUdpPort);
  Serial.printf("Now listening at IP %s, UDP port %d\n", WiFi.localIP().toString().c_str(), localUdpPort);
}

void loop() {
  timer.tick();
  timer.every(5000, check);
  int packetSize = Udp.parsePacket();
  if (packetSize)
  {
    // receive incoming UDP packets
    Serial.printf("Received %d bytes from %s, port %d\n", packetSize, Udp.remoteIP().toString().c_str(), Udp.remotePort());
    int len = Udp.read(incomingPacket, 255);
    if (len > 0)
    {
      incomingPacket[len] = 0;
    }
    Serial.printf("UDP packet contents: %s\n", incomingPacket);
    StaticJsonDocument<200> doc;
    DeserializationError error = deserializeJson(doc, incomingPacket);
    requestStrategy(doc.as<JsonObject>(), Udp.remoteIP(), Udp.remotePort());
    // send back a reply, to the IP address and port we got the packet from
  }
}

void requestStrategy(const JsonObject& jsonDocument, const IPAddress& remoteIP, const int remotePort) {
  StaticJsonDocument<200> doc;
  JsonObject obj = doc.createNestedObject();
  const char* s = jsonDocument["act"];
  bool performMessage = false;
  if (strcmp(s, "INIT") == 0 && !serverIPAddress.isSet()) {
    serverIPAddress = IPAddress(remoteIP);
    //serverPort = remotePort;
    obj["act"] = "INIT";
    obj["type"] = type;
    obj["max_power_usage"] = max_usage;
    performMessage = true;
  }
  else if (strcmp(s, "SET") == 0) {
      max_usage = jsonDocument["max_usage"].as<double>();
      obj["act"] = "ACK";
      obj["max_power_usage"] = max_usage;
      performMessage = true;
    }
  else if (strcmp(s, "ON") == 0) {
    obj["act"] = "ACK";
    obj["timestamp"] = jsonDocument["timestamp"].as<String>();
    obj["sts"] = 1;
    performMessage = true;
  }
  else if (strcmp(s, "OFF") == 0) {
    obj["act"] = "ACK";
    obj["timestamp"] = jsonDocument["timestamp"].as<String>();
    obj["sts"] = 0;
    performMessage = true;
  }
  if(performMessage) {
  Udp.beginPacket(remoteIP, serverPort);
  serializeJson(doc, Udp);
  Udp.endPacket();
  }
}

bool check(void*) {
  double active_power = random(1000, 2000);
  double delta = (active_power*10/100);
   active_power = random(active_power-delta, active_power+delta);
  if(serverIPAddress.isSet() &&  active_power > max_usage + 0.15 || active_power < max_usage - 0.15) {
    Serial.printf("IP in check: %s\n", serverIPAddress.toString().c_str());
      max_usage = active_power;
      StaticJsonDocument<200> doc;
      JsonObject obj = doc.createNestedObject();
      obj["act"] = "UPDATE";
      obj["type"] = type;
      obj["active_power"] = active_power;
      Udp.beginPacket(serverIPAddress, serverPort);
      serializeJson(doc, Udp);
      Udp.endPacket();
  }
  return true;
  }
