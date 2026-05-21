package com.homeiot.device.service;

import com.homeiot.auth.service.OwnershipService;
import com.homeiot.common.error.NotFoundException;
import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.dto.HomeCreateRequest;
import com.homeiot.device.dto.HomeMemberAssignRequest;
import com.homeiot.device.dto.HomeMemberResponse;
import com.homeiot.device.dto.HomeResponse;
import com.homeiot.device.entity.Device;
import com.homeiot.device.entity.DeviceShare;
import com.homeiot.device.entity.HomeGroup;
import com.homeiot.device.entity.HomeMember;
import com.homeiot.device.repository.DeviceRepository;
import com.homeiot.device.repository.DeviceShareRepository;
import com.homeiot.device.repository.HomeGroupRepository;
import com.homeiot.device.repository.HomeMemberRepository;
import com.homeiot.member.entity.Member;
import com.homeiot.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class HomeService {
    private final HomeGroupRepository homeGroupRepository;
    private final HomeMemberRepository homeMemberRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceShareRepository deviceShareRepository;
    private final MemberRepository memberRepository;
    private final OwnershipService ownershipService;
    private final DeviceAuditService deviceAuditService;

    public HomeService(HomeGroupRepository homeGroupRepository,
                       HomeMemberRepository homeMemberRepository,
                       DeviceRepository deviceRepository,
                       DeviceShareRepository deviceShareRepository,
                       MemberRepository memberRepository,
                       OwnershipService ownershipService,
                       DeviceAuditService deviceAuditService) {
        this.homeGroupRepository = homeGroupRepository;
        this.homeMemberRepository = homeMemberRepository;
        this.deviceRepository = deviceRepository;
        this.deviceShareRepository = deviceShareRepository;
        this.memberRepository = memberRepository;
        this.ownershipService = ownershipService;
        this.deviceAuditService = deviceAuditService;
    }

    public List<HomeResponse> getHomes() {
        if (ownershipService.isMaster()) {
            return homeGroupRepository.findAll().stream().map(this::toResponse).toList();
        }
        Member member = ownershipService.currentMember();
        LinkedHashMap<Long, HomeGroup> homes = new LinkedHashMap<>();
        homeGroupRepository.findAllByOwner_IdOrderByCreatedAtAsc(member.getId())
                .forEach(home -> homes.put(home.getId(), home));
        homeMemberRepository.findAllByMember_IdOrderByCreatedAtAsc(member.getId()).stream()
                .map(HomeMember::getHome)
                .forEach(home -> homes.putIfAbsent(home.getId(), home));
        return homes.values().stream().map(this::toResponse).toList();
    }

    @Transactional
    public HomeResponse createHome(HomeCreateRequest request) {
        Member owner = ownershipService.currentMember();
        HomeGroup home = homeGroupRepository.save(new HomeGroup(request.name(), owner));
        homeMemberRepository.save(new HomeMember(home, owner, DeviceShare.ROLE_OWNER));
        return toResponse(home);
    }

    @Transactional
    public HomeResponse addMember(Long homeId, HomeMemberAssignRequest request) {
        HomeGroup home = findHome(homeId);
        requireHomeManager(home);
        String role = DeviceShare.normalizeRole(request.role());
        Member member = memberRepository.findByEmail(normalizeEmail(request.email()))
                .filter(candidate -> candidate.getStatus() == Member.Status.ACTIVE)
                .orElseThrow(() -> new NotFoundException("active member not found: " + request.email()));
        homeMemberRepository.findByHomeAndMember_Id(home, member.getId())
                .orElseGet(() -> homeMemberRepository.save(new HomeMember(home, member, role)));
        deviceRepository.findAllByHomeOrderByCreatedAtAsc(home)
                .forEach(device -> shareDevice(device, member, role));
        return toResponse(home);
    }

    @Transactional
    public HomeResponse assignDevice(Long homeId, String deviceId) {
        HomeGroup home = findHome(homeId);
        requireHomeManager(home);
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("device not found: " + deviceId));
        ownershipService.requireDeviceManager(device);
        device.assignHome(home);
        homeMemberRepository.findAllByHomeOrderByCreatedAtAsc(home)
                .forEach(member -> shareDevice(device, member.getMember(), member.getRole()));
        deviceAuditService.recordCurrentActor(device, "HOME_ASSIGNED", home.getName(), "Device assigned to home group");
        return toResponse(home);
    }

    private void shareDevice(Device device, Member member, String role) {
        DeviceShare share = deviceShareRepository.findByDevice_DeviceIdAndMember_Id(device.getDeviceId(), member.getId())
                .orElseGet(() -> deviceShareRepository.save(new DeviceShare(device, member, role, DeviceShare.Source.FAMILY)));
        share.updateRole(role);
        deviceAuditService.recordCurrentActor(device, "HOME_SHARE_SYNCED", member.getEmail(), "Home member share synced");
    }

    private HomeGroup findHome(Long homeId) {
        return homeGroupRepository.findById(homeId)
                .orElseThrow(() -> new NotFoundException("home not found: " + homeId));
    }

    private void requireHomeManager(HomeGroup home) {
        if (ownershipService.isMaster()) {
            return;
        }
        Member member = ownershipService.currentMember();
        if (home.getOwner().getId().equals(member.getId())) {
            return;
        }
        homeMemberRepository.findByHomeAndMember_Id(home, member.getId())
                .filter(homeMember -> DeviceShare.ROLE_OWNER.equals(homeMember.getRole()))
                .orElseThrow(() -> new UnauthorizedException("home manager required"));
    }

    private HomeResponse toResponse(HomeGroup home) {
        return new HomeResponse(
                home.getId(),
                home.getName(),
                home.getOwner().getEmail(),
                home.getCreatedAt(),
                homeMemberRepository.findAllByHomeOrderByCreatedAtAsc(home).stream()
                        .map(this::toMemberResponse)
                        .toList()
        );
    }

    private HomeMemberResponse toMemberResponse(HomeMember homeMember) {
        Member member = homeMember.getMember();
        return new HomeMemberResponse(member.getId(), member.getEmail(), member.getName(), homeMember.getRole());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
