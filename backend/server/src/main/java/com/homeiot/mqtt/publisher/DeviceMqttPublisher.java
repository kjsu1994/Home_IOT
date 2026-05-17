package com.homeiot.mqtt.publisher;

public interface DeviceMqttPublisher {
    boolean publishCommand(String deviceId, Long commandId, String commandType, String payloadJson);
}
