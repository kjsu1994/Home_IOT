package com.homeiot.telemetry.entity;

import com.homeiot.device.entity.Device;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "telemetry_events")
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false)
    private Device device;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TelemetryEvent() {
    }

    public TelemetryEvent(Device device, String eventType, String payloadJson) {
        this.device = device;
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
