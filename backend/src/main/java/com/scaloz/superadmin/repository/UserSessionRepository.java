package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByJwtId(String jwtId);
    List<UserSession> findByUserIdAndUserTypeAndRevokedFalse(Long userId, String userType);
    List<UserSession> findByDeviceIdAndRevokedFalse(String deviceId);
}
