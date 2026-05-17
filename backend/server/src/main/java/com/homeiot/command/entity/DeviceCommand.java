package com.homeiot.command.entity;

import com.homeiot.device.entity.Device;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_commands")
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id_fk", nullable = false)
    private Device device;

    @Column(name = "command_type", nullable = false, length = 80)
    private String commandType;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_status", nullable = false, length = 20)
    private CommandStatus status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    protected DeviceCommand() {
    }

    public DeviceCommand(Device device, String commandType, String payloadJson) {
        this.device = device;
        this.commandType = commandType;
        this.payloadJson = payloadJson;
        this.status = CommandStatus.PENDING;
        this.requestedAt = OffsetDateTime.now();
    }

    public void markSent() {
        this.status = CommandStatus.SENT;
        this.sentAt = OffsetDateTime.now();
        this.failureReason = null;
    }

    public void markApplied() {
        this.status = CommandStatus.APPLIED;
        this.appliedAt = OffsetDateTime.now();
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = CommandStatus.FAILED;
        this.failedAt = OffsetDateTime.now();
        this.failureReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Device getDevice() {
        return device;
    }

    public String getCommandType() {
        return commandType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public CommandStatus getStatus() {
        return status;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }

    public OffsetDateTime getFailedAt() {
        return failedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
