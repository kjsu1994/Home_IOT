package com.homeiot.device.service;

import com.homeiot.auth.service.OwnershipService;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.dto.DeviceClaimRequest;
import com.homeiot.device.dto.DeviceClaimTokenHistoryResponse;
import com.homeiot.device.dto.DeviceClaimTokenResponse;
import com.homeiot.device.dto.DeviceDetailResponse;
import com.homeiot.device.dto.DeviceMetadataUpdateRequest;
import com.homeiot.device.dto.DevicePreferenceUpdateRequest;
import com.homeiot.device.dto.DeviceRegisterRequest;
import com.homeiot.device.dto.DeviceRegisterResponse;
import com.homeiot.device.dto.DeviceShareAssignRequest;
import com.homeiot.device.dto.DeviceShareResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceClaimToken;
import com.homeiot.device.entity.DeviceMemberPreference;
import com.homeiot.device.entity.DeviceShare;
import com.homeiot.device.entity.DeviceShareInvite;
import com.homeiot.device.repository.DeviceClaimTokenRepository;
import com.homeiot.device.repository.DeviceMemberPreferenceRepository;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.repository.DeviceShareInviteRepository;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.member.entity.Member;
import com.homeiot.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DeviceService {
    private static final Duration OFFLINE_AFTER = Duration.ofSeconds(90);
    private static final Duration CLAIM_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration SHARE_INVITE_TTL = Duration.ofDays(14);

    private final DeviceRepository deviceRepository;
    private final DeviceMemberPreferenceRepository preferenceRepository;
    private final DeviceShareRepository deviceShareRepository;
    private final DeviceShareInviteRepository deviceShareInviteRepository;
    private final DeviceClaimTokenRepository deviceClaimTokenRepository;
    private final MemberRepository memberRepository;
    private final JavaMailSender javaMailSender;
    private final DeviceTokenService deviceTokenService;
    private final DeviceAuditService deviceAuditService;
    private final OwnershipService ownershipService;
    private final String bootstrapApiKey;
    private final String senderAddress;
    private final String publicBaseUrl;

    public DeviceService(DeviceRepository deviceRepository,
                         DeviceMemberPreferenceRepository preferenceRepository,
                         DeviceShareRepository deviceShareRepository,
                         DeviceShareInviteRepository deviceShareInviteRepository,
                         DeviceClaimTokenRepository deviceClaimTokenRepository,
                         MemberRepository memberRepository,
                         JavaMailSender javaMailSender,
                         DeviceTokenService deviceTokenService,
                         DeviceAuditService deviceAuditService,
                         OwnershipService ownershipService,
                         @Value("${app.device-api-key:}") String bootstrapApiKey,
                         @Value("${mail.address.master:no-reply@homeiot.local}") String senderAddress,
                         @Value("${app.public-base-url:https://ctfmon.shop}") String publicBaseUrl) {
        this.deviceRepository = deviceRepository;
        this.preferenceRepository = preferenceRepository;
        this.deviceShareRepository = deviceShareRepository;
        this.deviceShareInviteRepository = deviceShareInviteRepository;
        this.deviceClaimTokenRepository = deviceClaimTokenRepository;
        this.memberRepository = memberRepository;
        this.javaMailSender = javaMailSender;
        this.deviceTokenService = deviceTokenService;
        this.deviceAuditService = deviceAuditService;
        this.ownershipService = ownershipService;
        this.bootstrapApiKey = bootstrapApiKey == null ? "" : bootstrapApiKey.trim();
        this.senderAddress = senderAddress;
        this.publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank() ? "https://ctfmon.shop" : publicBaseUrl.trim();
    }

    @Transactional
    public DeviceRegisterResponse register(DeviceRegisterRequest request, String presentedKey) {
        Optional<Device> existingDevice = deviceRepository.findByDeviceId(request.deviceId());
        validateRegistrationKey(existingDevice, presentedKey);

        Device device = existingDevice
                .map(existing -> {
                    existing.refreshFrom(
                            request.name(),
                            request.deviceType(),
                            request.fwVersion(),
                            request.ip(),
                            request.rssi()
                    );
                    return existing;
                })
                .orElseGet(() -> new Device(
                        request.deviceId(),
                        request.name(),
                        request.deviceType(),
                        request.fwVersion(),
                        request.ip(),
                        request.rssi()
                ));

        boolean isNew = device.getId() == null;
        boolean issueDeviceToken = shouldIssueDeviceToken(device, presentedKey);
        String deviceApiKey = null;
        if (issueDeviceToken) {
            deviceApiKey = deviceTokenService.generateToken();
            device.updateDeviceApiKeyHash(deviceTokenService.hashToken(deviceApiKey));
        }

        deviceRepository.save(device);

        return new DeviceRegisterResponse(
                device.getDeviceId(),
                true,
                deviceApiKey,
                isNew ? "device registered" : "device registration refreshed"
        );
    }

    @Transactional
    public DeviceClaimTokenResponse issueClaimToken(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceManager(device);
        Member createdBy = ownershipService.currentMember();
        OffsetDateTime now = OffsetDateTime.now();
        Optional<DeviceClaimToken> reusableToken = deviceClaimTokenRepository
                .findFirstByDevice_DeviceIdAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterAndTokenValueIsNotNullOrderByCreatedAtDesc(deviceId, now);
        if (reusableToken.isPresent()) {
            DeviceClaimToken token = reusableToken.get();
            return new DeviceClaimTokenResponse(device.getDeviceId(), token.getTokenValue(), token.getExpiresAt());
        }

        String token = deviceTokenService.generateClaimToken();
        OffsetDateTime expiresAt = now.plus(CLAIM_TOKEN_TTL);
        deviceClaimTokenRepository.save(new DeviceClaimToken(device, deviceTokenService.hashToken(token), token, expiresAt, createdBy));
        deviceAuditService.record(device, createdBy, "CLAIM_QR_ISSUED", null, "Claim QR issued");
        return new DeviceClaimTokenResponse(device.getDeviceId(), token, expiresAt);
    }

    public List<DeviceClaimTokenHistoryResponse> getClaimTokenHistory(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceManager(device);
        OffsetDateTime now = OffsetDateTime.now();
        return deviceClaimTokenRepository.findTop20ByDevice_DeviceIdOrderByCreatedAtDesc(deviceId).stream()
                .map(token -> toClaimTokenHistoryResponse(token, now))
                .toList();
    }

    @Transactional
    public DeviceClaimTokenHistoryResponse revokeClaimToken(String deviceId, Long tokenId) {
        ownershipService.requireDeviceManager(findDevice(deviceId));
        DeviceClaimToken token = deviceClaimTokenRepository.findByIdAndDevice_DeviceId(tokenId, deviceId)
                .orElseThrow(() -> new NotFoundException("claim token not found: " + tokenId));
        token.revoke();
        deviceAuditService.recordCurrentActor(token.getDevice(), "CLAIM_QR_REVOKED", String.valueOf(token.getId()), "Claim QR revoked");
        return toClaimTokenHistoryResponse(token, OffsetDateTime.now());
    }

    public List<DeviceShareResponse> getShares(String deviceId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceManager(device);
        List<DeviceShareResponse> responses = new ArrayList<>();
        List<DeviceShare> shares = deviceShareRepository.findAllByDevice_DeviceIdOrderByCreatedAtAsc(deviceId);
        shares.stream().map(share -> toShareResponse(device, share)).forEach(responses::add);
        deviceShareInviteRepository.findAllByDevice_DeviceIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtAsc(deviceId).stream()
                .filter(invite -> invite.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(this::toInviteResponse)
                .forEach(responses::add);

        Member owner = device.getOwner();
        if (owner != null && shares.stream().noneMatch(share -> share.getMember().getId().equals(owner.getId()))) {
            responses.add(new DeviceShareResponse(
                    null,
                    owner.getId(),
                    owner.getEmail(),
                    owner.getName(),
                    DeviceShare.ROLE_OWNER,
                    DeviceShare.ROLE_OWNER,
                    "ACTIVE",
                    true,
                    true,
                    device.getCreatedAt(),
                    null
            ));
        }
        return responses;
    }

    @Transactional
    public DeviceShareResponse assignShare(String deviceId, DeviceShareAssignRequest request) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceManager(device);
        Member inviter = ownershipService.currentMember();
        DeviceShare.Source source = ownershipService.isMaster() ? DeviceShare.Source.ADMIN : DeviceShare.Source.FAMILY;
        String role = DeviceShare.normalizeRole(request.role());
        String email = normalizeEmail(request.email());
        Optional<Member> maybeMember = memberRepository.findByEmail(email)
                .filter(candidate -> candidate.getStatus() == Member.Status.ACTIVE);

        if (maybeMember.isEmpty()) {
            DeviceShareInvite invite = deviceShareInviteRepository.findByDeviceAndEmailAndAcceptedAtIsNullAndRevokedAtIsNull(device, email)
                    .orElseGet(() -> deviceShareInviteRepository.save(new DeviceShareInvite(device, email, role, source, inviter, OffsetDateTime.now().plus(SHARE_INVITE_TTL))));
            sendShareInviteMail(invite);
            deviceAuditService.record(device, inviter, "SHARE_INVITED", email, "Share invitation sent with role " + invite.getRole());
            return toInviteResponse(invite);
        }

        Member member = maybeMember.get();
        DeviceShare share = deviceShareRepository.findByDevice_DeviceIdAndMember_Id(deviceId, member.getId())
                .orElseGet(() -> deviceShareRepository.save(new DeviceShare(device, member, role, source)));
        share.updateRole(role);
        if (DeviceShare.ROLE_OWNER.equals(role)) {
            device.assignOwner(member);
        }
        deviceAuditService.record(device, inviter, "SHARE_ASSIGNED", member.getEmail(), "Share assigned with role " + share.getRole());
        return toShareResponse(device, share);
    }

    @Transactional
    public void removeShare(String deviceId, Long memberId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceManager(device);
        deviceShareRepository.findByDevice_DeviceIdAndMember_Id(deviceId, memberId)
                .ifPresent(share -> {
                    deviceAuditService.recordCurrentActor(device, "SHARE_REMOVED", share.getMember().getEmail(), "Share removed");
                    deviceShareRepository.delete(share);
                });
        memberRepository.findById(memberId).ifPresent(device::unassignOwnerIf);
    }

    @Transactional
    public void revokeShareInvite(String deviceId, Long inviteId) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceManager(device);
        DeviceShareInvite invite = deviceShareInviteRepository.findByIdAndDevice_DeviceId(inviteId, deviceId)
                .orElseThrow(() -> new NotFoundException("share invite not found: " + inviteId));
        invite.revoke();
        deviceAuditService.recordCurrentActor(device, "SHARE_INVITE_REVOKED", invite.getEmail(), "Share invitation revoked");
    }

    @Transactional
    public DeviceDetailResponse claim(DeviceClaimRequest request) {
        if (request.claimToken() != null && !request.claimToken().isBlank()) {
            return claimWithShareToken(request);
        }
        return claimWithDeviceApiKey(request);
    }

    private DeviceDetailResponse claimWithShareToken(DeviceClaimRequest request) {
        Member member = ownershipService.currentMember();
        DeviceClaimToken claimToken = deviceClaimTokenRepository.findByTokenHash(deviceTokenService.hashToken(request.claimToken().trim()))
                .orElseThrow(() -> new UnauthorizedException("invalid claim token"));
        if (!claimToken.isUsable(OffsetDateTime.now())) {
            throw new UnauthorizedException("claim token expired or already used");
        }

        Device device = claimToken.getDevice();
        if (request.deviceId() != null && !request.deviceId().isBlank() && !device.getDeviceId().equals(request.deviceId().trim())) {
            throw new UnauthorizedException("claim token does not match device");
        }

        shareDeviceWithMember(device, member, DeviceShare.Source.QR);
        claimToken.markUsed(member);
        updateOptionalMetadata(device, request);
        deviceAuditService.record(device, member, "CLAIM_QR_USED", member.getEmail(), "Device registered by QR");
        return toDetailResponse(device);
    }

    private DeviceDetailResponse claimWithDeviceApiKey(DeviceClaimRequest request) {
        if (request.deviceId() == null || request.deviceId().isBlank()) {
            throw new IllegalArgumentException("device id is required");
        }
        if (request.deviceApiKey() == null || request.deviceApiKey().isBlank()) {
            throw new IllegalArgumentException("device api key is required");
        }

        Member member = ownershipService.currentMember();
        Device device = deviceRepository.findByDeviceId(request.deviceId())
                .orElseThrow(() -> new NotFoundException("device not found: " + request.deviceId()));

        if (device.ownerId() != null && !device.ownerId().equals(member.getId())) {
            throw new UnauthorizedException("device already registered to another user");
        }
        if (!deviceTokenService.matches(device, request.deviceApiKey())) {
            throw new UnauthorizedException("invalid device api key");
        }

        shareDeviceWithMember(device, member, DeviceShare.Source.QR);
        updateOptionalMetadata(device, request);
        deviceAuditService.record(device, member, "DEVICE_CLAIMED", member.getEmail(), "Device registered by key");
        return toDetailResponse(device);
    }

    private void shareDeviceWithMember(Device device, Member member, DeviceShare.Source source) {
        device.assignOwner(member);
        if (!deviceShareRepository.existsByDeviceAndMember_Id(device, member.getId())) {
            deviceShareRepository.save(new DeviceShare(device, member, DeviceShare.ROLE_CONTROLLER, source));
        }
    }

    private void updateOptionalMetadata(Device device, DeviceClaimRequest request) {
        if (request.displayName() != null || request.room() != null) {
            device.updateMetadata(
                    request.displayName() == null ? device.getDisplayName() : request.displayName(),
                    request.room() == null ? device.getRoom() : request.room()
            );
        }
    }

    @Transactional
    public DeviceDetailResponse updateMetadata(String deviceId, DeviceMetadataUpdateRequest request) {
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceControl(device);

        device.updateMetadata(request.displayName(), request.room());
        deviceAuditService.recordCurrentActor(device, "METADATA_UPDATED", null, "Device metadata updated");

        return toDetailResponse(device);
    }

    @Transactional
    public DeviceDetailResponse updatePreference(String deviceId, DevicePreferenceUpdateRequest request) {
        Device device = findDevice(deviceId);
        ownershipService.requireDeviceAccess(device);
        Member member = ownershipService.currentMember();
        DeviceMemberPreference preference = preferenceRepository.findByDeviceAndMember(device, member)
                .orElseGet(() -> preferenceRepository.save(new DeviceMemberPreference(device, member)));
        preference.update(request.displayName(), request.room());
        deviceAuditService.record(device, member, "PREFERENCE_UPDATED", member.getEmail(), "Personal device preference updated");
        return toDetailResponse(device);
    }

    private DeviceDetailResponse toDetailResponse(Device device) {
        DeviceMemberPreference preference = currentPreference(device);
        return new DeviceDetailResponse(
                device.getDeviceId(),
                effectiveName(device, preference),
                device.getDisplayName(),
                effectiveRoom(device, preference),
                device.getDeviceType(),
                device.getFwVersion(),
                device.getIp(),
                device.getRssi(),
                isOnline(device),
                device.getLastSeenAt(),
                device.getCreatedAt(),
                device.getHome() == null ? null : device.getHome().getId(),
                device.getHome() == null ? null : device.getHome().getName(),
                preference == null ? null : preference.getDisplayName(),
                preference == null ? null : preference.getRoom(),
                ownershipService.canControlDevice(device),
                ownershipService.canManageDevice(device)
        );
    }

    private DeviceMemberPreference currentPreference(Device device) {
        if (ownershipService.isMaster()) {
            return null;
        }
        Member member = ownershipService.currentMember();
        return preferenceRepository.findByDeviceAndMember(device, member).orElse(null);
    }

    private String effectiveName(Device device, DeviceMemberPreference preference) {
        return preference != null && preference.getDisplayName() != null ? preference.getDisplayName() : device.getEffectiveName();
    }

    private String effectiveRoom(Device device, DeviceMemberPreference preference) {
        return preference != null && preference.getRoom() != null ? preference.getRoom() : device.getRoom();
    }

    private boolean isOnline(Device device) {
        if (!device.isOnline() || device.getLastSeenAt() == null) {
            return false;
        }
        return device.getLastSeenAt().isAfter(OffsetDateTime.now().minus(OFFLINE_AFTER));
    }

    private DeviceShareResponse toShareResponse(Device device, DeviceShare share) {
        Member member = share.getMember();
        boolean owner = device.ownerId() != null && device.ownerId().equals(member.getId());
        return new DeviceShareResponse(
                null,
                member.getId(),
                member.getEmail(),
                member.getName(),
                owner ? DeviceShare.ROLE_OWNER : share.getRole(),
                share.getSource().name(),
                "ACTIVE",
                owner,
                true,
                share.getCreatedAt(),
                null
        );
    }

    private DeviceShareResponse toInviteResponse(DeviceShareInvite invite) {
        return new DeviceShareResponse(
                invite.getId(),
                null,
                invite.getEmail(),
                "",
                invite.getRole(),
                invite.getSource().name(),
                "PENDING",
                false,
                true,
                invite.getCreatedAt(),
                invite.getExpiresAt()
        );
    }

    private DeviceClaimTokenHistoryResponse toClaimTokenHistoryResponse(DeviceClaimToken token, OffsetDateTime now) {
        return new DeviceClaimTokenHistoryResponse(
                token.getId(),
                claimTokenStatus(token, now),
                token.getCreatedBy() == null ? null : token.getCreatedBy().getEmail(),
                token.getUsedBy() == null ? null : token.getUsedBy().getEmail(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getUsedAt(),
                token.getRevokedAt()
        );
    }

    private String claimTokenStatus(DeviceClaimToken token, OffsetDateTime now) {
        if (token.getRevokedAt() != null) {
            return "REVOKED";
        }
        if (token.getUsedAt() != null) {
            return "USED";
        }
        if (!token.getExpiresAt().isAfter(now)) {
            return "EXPIRED";
        }
        return "AVAILABLE";
    }

    private Device findDevice(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
    }

    private void sendShareInviteMail(DeviceShareInvite invite) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (senderAddress != null && !senderAddress.isBlank()) {
            message.setFrom(senderAddress);
        }
        message.setTo(invite.getEmail());
        message.setSubject("[Home_IOT] Device sharing invitation");
        message.setText("You have been invited to access a Home_IOT device.\n\nDevice: "
                + invite.getDevice().getEffectiveName()
                + "\nOpen: " + publicBaseUrl + "/iot/"
                + "\nExpires at: " + invite.getExpiresAt()
                + "\n\nSign up or log in with this email address to accept the sharing invitation automatically.");
        try {
            javaMailSender.send(message);
        } catch (MailException exception) {
            throw new IllegalStateException("failed to send share invite email", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRegistrationKey(Optional<Device> existingDevice, String presentedKey) {
        if (bootstrapApiKey.isBlank()) {
            return;
        }
        if (bootstrapApiKey.equals(presentedKey)) {
            return;
        }
        if (existingDevice.isPresent() && deviceTokenService.matches(existingDevice.get(), presentedKey)) {
            return;
        }
        throw new UnauthorizedException("invalid device api key");
    }

    private boolean shouldIssueDeviceToken(Device device, String presentedKey) {
        if (bootstrapApiKey.isBlank()) {
            return device.getDeviceApiKeyHash() == null;
        }
        return device.getDeviceApiKeyHash() == null || bootstrapApiKey.equals(presentedKey);
    }
}
