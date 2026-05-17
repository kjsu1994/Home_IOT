package com.homeiot.mqtt.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "mqtt.command-mode", havingValue = "real")
public class PahoDeviceMqttPublisher implements DeviceMqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(PahoDeviceMqttPublisher.class);

    private final ObjectMapper objectMapper;
    private final String brokerUrl;
    private final String clientIdBase;
    private final String username;
    private final String password;

    private MqttClient client;

    public PahoDeviceMqttPublisher(ObjectMapper objectMapper,
                                   @Value("${mqtt.broker-url}") String brokerUrl,
                                   @Value("${mqtt.client-id:home-iot-server}") String clientIdBase,
                                   @Value("${mqtt.username:}") String username,
                                   @Value("${mqtt.password:}") String password) {
        this.objectMapper = objectMapper;
        this.brokerUrl = brokerUrl;
        this.clientIdBase = clientIdBase;
        this.username = username;
        this.password = password;
    }

    @Override
    public synchronized boolean publishCommand(String deviceId, Long commandId, String commandType, String payloadJson) {
        try {
            ensureConnected();
            String topic = "devices/" + deviceId + "/commands";
            String payload = objectMapper.writeValueAsString(buildPayload(commandId, commandType, payloadJson));

            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(false);
            client.publish(topic, message);
            log.info("MQTT publish topic={} commandId={} type={}", topic, commandId, commandType);
            return true;
        } catch (MqttException | JsonProcessingException ex) {
            log.error("MQTT publish failed for deviceId={} commandId={} type={}", deviceId, commandId, commandType, ex);
            return false;
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException ex) {
            log.warn("MQTT client close failed", ex);
        }
    }

    private void ensureConnected() throws MqttException {
        if (client != null && client.isConnected()) {
            return;
        }

        if (client == null) {
            client = new MqttClient(brokerUrl, clientIdBase + "-publisher-" + UUID.randomUUID());
            client.setCallback(new NoOpCallback());
        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(5);
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }

        IMqttToken token = client.connectWithResult(options);
        token.waitForCompletion();
        log.info("MQTT connected broker={} clientId={}", brokerUrl, client.getClientId());
    }

    private Map<String, Object> buildPayload(Long commandId, String commandType, String payloadJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command_id", commandId);
        payload.put("command_type", commandType);
        payload.put("payload_json", payloadJson == null ? "{}" : payloadJson);
        return payload;
    }

    private static final class NoOpCallback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
        }

        @Override
        public void connectionLost(Throwable cause) {
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}
