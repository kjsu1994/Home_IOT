package com.homeiot.ota.entity;

import com.homeiot.device.entity.Device;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ota_history")
public class OtaHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false)
    private Device device;

    @Column(name = "from_version", length = 80)
    private String fromVersion;

    @Column(name = "to_version", length = 80)
    private String toVersion;

    @Column(name = "ota_status", nullable = false, length = 30)
    private String status;

    @Column(name = "message_text", length = 255)
    private String message;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OtaHistory() {
    }

    public OtaHistory(Device device, String fromVersion, String toVersion, String status, String message,
                      OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        this.device = device;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.status = status;
        this.message = message;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = OffsetDateTime.now();
    }
}
