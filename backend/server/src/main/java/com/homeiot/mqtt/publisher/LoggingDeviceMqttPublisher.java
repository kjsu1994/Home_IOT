package com.homeiot.mqtt.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingDeviceMqttPublisher implements DeviceMqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingDeviceMqttPublisher.class);

    @Override
    public boolean publishCommand(String deviceId, Long commandId, String commandType, String payloadJson) {
        log.info("MQTT publish simulated topic=devices/{}/commands commandId={} type={} payload={}",
                deviceId, commandId, commandType, payloadJson);
        return true;
    }
}
