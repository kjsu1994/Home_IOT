package com.homeiot.member.repository;

import com.homeiot.member.entity.SignupVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SignupVerificationRepository extends JpaRepository<SignupVerification, Long> {
    Optional<SignupVerification> findByEmail(String email);
}
