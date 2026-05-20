package com.homeiot.member.service;

import com.homeiot.common.error.UnauthorizedException;
import com.homeiot.device.service.DeviceShareInviteService;
import com.homeiot.member.dto.MemberDtos;
import com.homeiot.member.entity.Member;
import com.homeiot.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final SignupVerificationService signupVerificationService;
    private final DeviceShareInviteService deviceShareInviteService;

    public MemberService(MemberRepository memberRepository,
                         PasswordEncoder passwordEncoder,
                         RoleService roleService,
                         SignupVerificationService signupVerificationService,
                         DeviceShareInviteService deviceShareInviteService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.signupVerificationService = signupVerificationService;
        this.deviceShareInviteService = deviceShareInviteService;
    }

    @Transactional
    public MemberDtos.MemberResponse signup(MemberDtos.SignupRequest request) {
        String email = normalizeEmail(request.email());
        signupVerificationService.assertVerifiedEmailForSignup(email);

        Member member = memberRepository.findByEmail(email)
                .map(existing -> {
                    if (existing.getStatus() != Member.Status.WITHDRAWN) {
                        throw new IllegalArgumentException("member already exists");
                    }
                    existing.reactivateLocal(request.name().trim(), passwordEncoder.encode(request.password()), roleService.rolesFor(email));
                    return existing;
                })
                .orElseGet(() -> new Member(
                        email,
                        request.name().trim(),
                        passwordEncoder.encode(request.password()),
                        Member.AuthProvider.LOCAL,
                        roleService.rolesFor(email)
                ));

        Member saved = memberRepository.save(member);
        deviceShareInviteService.acceptPendingInvites(saved);
        signupVerificationService.consumeVerifiedEmail(email);
        return toResponse(saved);
    }

    public boolean isEmailAvailable(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        return memberRepository.findByEmail(email)
                .map(member -> member.getStatus() == Member.Status.WITHDRAWN)
                .orElse(true);
    }

    public Member findActiveByEmail(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("invalid credentials"));
        if (member.getStatus() != Member.Status.ACTIVE) {
            throw new UnauthorizedException("account disabled");
        }
        return member;
    }

    @Transactional
    public Member findOrCreateOauthMember(String rawEmail, String rawName, Member.AuthProvider provider) {
        String email = normalizeEmail(rawEmail);
        String name = rawName == null || rawName.isBlank() ? email.substring(0, email.indexOf('@')) : rawName.trim();
        Member member = memberRepository.findByEmail(email)
                .map(existing -> {
                    existing.linkOauth(name, provider, roleService.rolesFor(email));
                    return existing;
                })
                .orElseGet(() -> new Member(email, name, null, provider, roleService.rolesFor(email)));
        Member saved = memberRepository.save(member);
        deviceShareInviteService.acceptPendingInvites(saved);
        return saved;
    }

    @Transactional
    public void updatePassword(String rawEmail, String rawPassword) {
        Member member = memberRepository.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new IllegalArgumentException("member not found"));
        member.updatePasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Transactional
    public MemberDtos.MemberResponse updateAccount(Member member, MemberDtos.AccountUpdateRequest request) {
        member.updateProfile(request.name());
        memberRepository.save(member);
        return toResponse(member);
    }

    @Transactional
    public void changePassword(Member member, MemberDtos.PasswordChangeRequest request) {
        String currentPassword = request.currentPassword();
        if (member.getPasswordHash() != null) {
            if (currentPassword == null || currentPassword.isBlank() || !passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
                throw new UnauthorizedException("current password does not match");
            }
        }
        member.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
        memberRepository.save(member);
    }

    public MemberDtos.MemberResponse toResponse(Member member) {
        return new MemberDtos.MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getAuthProvider(),
                member.getRoles()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
