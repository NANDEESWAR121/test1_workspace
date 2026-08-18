package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByDeviceId(String deviceId);
    List<UserDevice> findTop20ByUserIdAndUserTypeAndStatusOrderByLastSeenDesc(Long userId, String userType, String status);
}
