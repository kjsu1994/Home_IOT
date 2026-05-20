package com.homeiot.device.service;

import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceShare;
import com.homeiot.device.entity.DeviceShareInvite;
import com.homeiot.device.repository.DeviceShareInviteRepository;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.member.entity.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class DeviceShareInviteService {
    private final DeviceShareInviteRepository inviteRepository;
    private final DeviceShareRepository shareRepository;

    public DeviceShareInviteService(DeviceShareInviteRepository inviteRepository,
                                    DeviceShareRepository shareRepository) {
        this.inviteRepository = inviteRepository;
        this.shareRepository = shareRepository;
    }

    @Transactional
    public void acceptPendingInvites(Member member) {
        String email = normalizeEmail(member.getEmail());
        OffsetDateTime now = OffsetDateTime.now();
        inviteRepository.findAllByEmailAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtAsc(email).stream()
                .filter(invite -> invite.isAcceptable(now))
                .forEach(invite -> acceptInvite(invite, member));
    }

    private void acceptInvite(DeviceShareInvite invite, Member member) {
        Device device = invite.getDevice();
        device.assignOwner(member);
        if (!shareRepository.existsByDeviceAndMember_Id(device, member.getId())) {
            shareRepository.save(new DeviceShare(device, member, invite.getRole(), invite.getSource()));
        }
        invite.markAccepted();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
