package com.homeiot.auth.service;

import com.homeiot.member.entity.PasswordResetToken;
import com.homeiot.member.repository.MemberRepository;
import com.homeiot.member.repository.PasswordResetTokenRepository;
import com.homeiot.member.service.MemberService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@Transactional
public class PasswordResetService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;
    private final String senderAddress;
    private final long expireMinutes;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
                                MemberRepository memberRepository,
                                MemberService memberService,
                                PasswordEncoder passwordEncoder,
                                JavaMailSender javaMailSender,
                                @Value("${mail.address.master:no-reply@homeiot.local}") String senderAddress,
                                @Value("${password-reset.expire-minutes:30}") long expireMinutes) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.memberRepository = memberRepository;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.javaMailSender = javaMailSender;
        this.senderAddress = senderAddress;
        this.expireMinutes = expireMinutes;
    }

    public void requestReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (memberRepository.findByEmail(email).isEmpty()) {
            return;
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(expireMinutes);
        PasswordResetToken token = passwordResetTokenRepository.findByEmail(email).orElseGet(PasswordResetToken::new);
        token.issue(email, passwordEncoder.encode(code), expiresAt);
        passwordResetTokenRepository.save(token);
        sendResetMail(email, code, expiresAt);
    }

    public void confirmReset(String rawEmail, String rawCode, String newPassword) {
        String email = normalizeEmail(rawEmail);
        PasswordResetToken token = passwordResetTokenRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("password reset token not found"));
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("password reset token expired");
        }
        if (!passwordEncoder.matches(rawCode == null ? "" : rawCode.trim(), token.getResetCodeHash())) {
            throw new IllegalArgumentException("password reset code invalid");
        }
        memberService.updatePassword(email, newPassword);
        passwordResetTokenRepository.deleteByEmail(email);
    }

    private void sendResetMail(String email, String code, OffsetDateTime expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (senderAddress != null && !senderAddress.isBlank()) {
            message.setFrom(senderAddress);
        }
        message.setTo(email);
        message.setSubject("[Home_IOT] Password reset code");
        message.setText("Home_IOT 비밀번호 재설정\n\n재설정 코드: " + code + "\n만료시간: " + expiresAt + "\n\n요청하지 않았다면 이 메일을 무시하세요.");
        javaMailSender.send(message);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
