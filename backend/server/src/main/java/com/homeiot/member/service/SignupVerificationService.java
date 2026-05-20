package com.homeiot.member.service;

import com.homeiot.member.dto.MemberDtos;
import com.homeiot.member.entity.Member;
import com.homeiot.member.entity.SignupVerification;
import com.homeiot.member.repository.MemberRepository;
import com.homeiot.member.repository.SignupVerificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
@Transactional
public class SignupVerificationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String EMAIL_COOLDOWN_KEY_PREFIX = "signup:email-verification:cooldown:";
    private static final String EMAIL_HOURLY_KEY_PREFIX = "signup:email-verification:email-hourly:";
    private static final String IP_HOURLY_KEY_PREFIX = "signup:email-verification:ip-hourly:";
    private static final String VERIFY_FAILURE_KEY_PREFIX = "signup:email-verification:verify-fail:";

    private final SignupVerificationRepository signupVerificationRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;
    private final StringRedisTemplate redisTemplate;
    private final String senderAddress;
    private final long verificationExpireMinutes;
    private final long requestCooldownSeconds;
    private final long maxRequestsPerEmailPerHour;
    private final long maxRequestsPerIpPerHour;
    private final long maxVerificationFailures;

    public SignupVerificationService(SignupVerificationRepository signupVerificationRepository,
                                     MemberRepository memberRepository,
                                     PasswordEncoder passwordEncoder,
                                     JavaMailSender javaMailSender,
                                     StringRedisTemplate redisTemplate,
                                     @Value("${mail.address.master:no-reply@homeiot.local}") String senderAddress,
                                     @Value("${signup.email-verification.expire-minutes:30}") long verificationExpireMinutes,
                                     @Value("${signup.email-verification.request.cooldown-seconds:60}") long requestCooldownSeconds,
                                     @Value("${signup.email-verification.request.max-per-email-per-hour:5}") long maxRequestsPerEmailPerHour,
                                     @Value("${signup.email-verification.request.max-per-ip-per-hour:20}") long maxRequestsPerIpPerHour,
                                     @Value("${signup.email-verification.confirm.max-failures:5}") long maxVerificationFailures) {
        this.signupVerificationRepository = signupVerificationRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.javaMailSender = javaMailSender;
        this.redisTemplate = redisTemplate;
        this.senderAddress = senderAddress;
        this.verificationExpireMinutes = verificationExpireMinutes;
        this.requestCooldownSeconds = requestCooldownSeconds;
        this.maxRequestsPerEmailPerHour = maxRequestsPerEmailPerHour;
        this.maxRequestsPerIpPerHour = maxRequestsPerIpPerHour;
        this.maxVerificationFailures = maxVerificationFailures;
    }

    public MemberDtos.EmailVerificationSendResponse sendVerificationCode(String rawEmail, String clientIp) {
        String email = normalizeEmail(rawEmail);
        verifyEmailAvailable(email);
        enforceRequestRateLimit(email, normalizeClientIp(clientIp));

        String code = generateVerificationCode();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(verificationExpireMinutes);
        SignupVerification verification = signupVerificationRepository.findByEmail(email).orElseGet(SignupVerification::new);
        verification.issueNewCode(email, passwordEncoder.encode(code), expiresAt);
        signupVerificationRepository.save(verification);

        sendVerificationMail(email, code, expiresAt);
        recordRequestRateLimit(email, normalizeClientIp(clientIp));
        redisTemplate.delete(VERIFY_FAILURE_KEY_PREFIX + email);
        return new MemberDtos.EmailVerificationSendResponse(email, true, expiresAt);
    }

    public MemberDtos.EmailVerificationConfirmResponse confirmVerificationCode(String rawEmail, String rawCode) {
        String email = normalizeEmail(rawEmail);
        String code = rawCode == null ? "" : rawCode.trim();
        SignupVerification verification = signupVerificationRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("email verification not found"));
        if (verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("email verification code expired");
        }
        enforceVerificationFailureLimit(email, verification.getExpiresAt());
        if (!passwordEncoder.matches(code, verification.getVerificationCodeHash())) {
            recordVerificationFailure(email, verification.getExpiresAt());
            throw new IllegalArgumentException("email verification code invalid");
        }
        verification.markVerified(OffsetDateTime.now());
        redisTemplate.delete(VERIFY_FAILURE_KEY_PREFIX + email);
        return new MemberDtos.EmailVerificationConfirmResponse(email, true, verification.getVerifiedAt());
    }

    @Transactional(readOnly = true)
    public void assertVerifiedEmailForSignup(String rawEmail) {
        SignupVerification verification = signupVerificationRepository.findByEmail(normalizeEmail(rawEmail))
                .orElseThrow(() -> new IllegalArgumentException("email verification required"));
        if (verification.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("email verification code expired");
        }
        if (!verification.isVerified()) {
            throw new IllegalArgumentException("email verification required");
        }
    }

    public void consumeVerifiedEmail(String rawEmail) {
        signupVerificationRepository.findByEmail(normalizeEmail(rawEmail)).ifPresent(signupVerificationRepository::delete);
    }

    private void verifyEmailAvailable(String email) {
        memberRepository.findByEmail(email)
                .filter(member -> member.getStatus() != Member.Status.WITHDRAWN)
                .ifPresent(member -> {
                    throw new IllegalArgumentException("member already exists");
                });
    }

    private void sendVerificationMail(String email, String code, OffsetDateTime expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (senderAddress != null && !senderAddress.isBlank()) {
            message.setFrom(senderAddress);
        }
        message.setTo(email);
        message.setSubject("[Home_IOT] Email verification code");
        message.setText("Home_IOT 이메일 인증\n\n인증코드: " + code + "\n인증만료시간: " + expiresAt + "\n\n요청하지 않았다면 이 메일을 무시하세요.");
        try {
            javaMailSender.send(message);
        } catch (MailException exception) {
            throw new IllegalStateException("failed to send verification email", exception);
        }
    }

    private void enforceRequestRateLimit(String email, String clientIp) {
        String cooldownKey = EMAIL_COOLDOWN_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long remaining = redisTemplate.getExpire(cooldownKey);
            throw new IllegalArgumentException("please wait " + Math.max(1L, remaining == null ? requestCooldownSeconds : remaining) + " seconds before requesting another code");
        }
        ensureWindowAllowance(EMAIL_HOURLY_KEY_PREFIX + email, maxRequestsPerEmailPerHour, "email reached hourly verification request limit");
        ensureWindowAllowance(IP_HOURLY_KEY_PREFIX + clientIp, maxRequestsPerIpPerHour, "ip reached hourly verification request limit");
    }

    private void ensureWindowAllowance(String key, long maxAllowed, String message) {
        String current = redisTemplate.opsForValue().get(key);
        long count = current == null ? 0 : Long.parseLong(current);
        if (count >= maxAllowed) {
            throw new IllegalArgumentException(message);
        }
    }

    private void recordRequestRateLimit(String email, String clientIp) {
        incrementWithTtl(EMAIL_COOLDOWN_KEY_PREFIX + email, Duration.ofSeconds(requestCooldownSeconds));
        incrementWithTtl(EMAIL_HOURLY_KEY_PREFIX + email, Duration.ofHours(1));
        incrementWithTtl(IP_HOURLY_KEY_PREFIX + clientIp, Duration.ofHours(1));
    }

    private void incrementWithTtl(String key, Duration ttl) {
        Long updated = redisTemplate.opsForValue().increment(key);
        if (updated != null && updated == 1L) {
            redisTemplate.expire(key, ttl);
        }
    }

    private void enforceVerificationFailureLimit(String email, OffsetDateTime expiresAt) {
        String current = redisTemplate.opsForValue().get(VERIFY_FAILURE_KEY_PREFIX + email);
        long count = current == null ? 0 : Long.parseLong(current);
        if (count >= maxVerificationFailures) {
            throw new IllegalArgumentException("too many invalid verification attempts");
        }
    }

    private void recordVerificationFailure(String email, OffsetDateTime expiresAt) {
        String key = VERIFY_FAILURE_KEY_PREFIX + email;
        Long updated = redisTemplate.opsForValue().increment(key);
        if (updated != null && updated == 1L) {
            Duration ttl = Duration.between(OffsetDateTime.now(), expiresAt);
            redisTemplate.expire(key, ttl.isNegative() ? Duration.ofMinutes(1) : ttl);
        }
    }

    private String generateVerificationCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeClientIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }
}
