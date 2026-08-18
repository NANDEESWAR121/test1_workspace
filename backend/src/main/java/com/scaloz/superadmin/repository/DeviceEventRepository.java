package com.scaloz.superadmin.repository;

import com.scaloz.superadmin.model.DeviceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {
    List<DeviceEvent> findByUserIdAndUserTypeOrderByTimestampDesc(Long userId, String userType);
}
