#include <ESP8266WiFi.h>
#include <ArduinoJson.h>
#include <WiFiUdp.h>
#include <SoftwareSerial.h>
#include <ModbusMaster.h>
SoftwareSerial pzem(D5,D6);
ModbusMaster node;// (RX,TX) connect to TX,RX of PZEM for NodeMCU
#define RELAY D7
const char* ssid = "Fritz! Network";
const char* password = "Oliver2016Artu2015";
double voltage_usage, current_usage, active_power, active_energy, frequency, power_factor, over_power_alarm; 
uint8_t result; uint16_t data[6];
WiFiUDP Udp;
unsigned int localUdpPort = 4210;
char incomingPacket[256];
char replyPacket[] = "Hi there! Got the message :-)";
void setup() {
  Serial.begin(115200);
  pzem.begin(9600);
  node.begin(1, pzem);
  Serial.println();
  pinMode(RELAY, OUTPUT);
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
  digitalWrite(RELAY, HIGH);
}

void loop() {
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
    pzemdata();
    }
}
void pzemdata(){

    result = node.readInputRegisters(0x0000, 10);
    
    if (result == node.ku8MBSuccess)  
    {
    voltage_usage      = (node.getResponseBuffer(0x00) / 10.0f);
    current_usage      = (node.getResponseBuffer(0x01) / 1000.000f);
    active_power       = (node.getResponseBuffer(0x03) / 10.0f);
    active_energy      = (node.getResponseBuffer(0x05) / 1000.0f);
    frequency          = (node.getResponseBuffer(0x07) / 10.0f);
    power_factor       = (node.getResponseBuffer(0x08) / 100.0f);
    over_power_alarm   = (node.getResponseBuffer(0x09));
    }
    Sr
  Serial.print("VOLTAGE:           ");   Serial.println(voltage_usage);   // V
  Serial.print("CURRENT_USAGE:     ");   Serial.println(current_usage, 3);  //  A
  Serial.print("ACTIVE_POWER:      ");   Serial.println(active_power);   //  W
  Serial.print("ACTIVE_ENERGY:     ");   Serial.println(active_energy, 3);  // kWh
  Serial.print("FREQUENCY:         ");   Serial.println(frequency);    // Hz
  Serial.print("POWER_FACTOR:      ");   Serial.println(power_factor);
  Serial.print("OVER_POWER_ALARM:  ");   Serial.println(over_power_alarm, 0);
  Serial.println("====================================================");
}

void requestStrategy(const JsonObject& jsonDocument, const IPAddress& remoteIP, const int remotePort) {
  StaticJsonDocument<200> doc;
  JsonObject obj = doc.createNestedObject();
  const char* s = jsonDocument["act"];
  if (strcmp(s, "INIT") == 0) {
    obj["act"] = "ACK";
    obj["sts"] = digitalRead(RELAY);
  }
  else if (strcmp(s, "ON") == 0) {
    obj["act"] = "ACK";
    digitalWrite(RELAY, HIGH);
    obj["sts"] = digitalRead(RELAY);
    }
  else if (strcmp(s, "OFF") == 0) {
    obj["act"] = "ACK";
    digitalWrite(RELAY, LOW);
    obj["sts"] = digitalRead(RELAY);
  }
  Udp.beginPacket(remoteIP, remotePort);
  serializeJson(doc, Udp);
  Udp.endPacket();
}
