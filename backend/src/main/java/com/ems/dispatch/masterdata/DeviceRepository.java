package com.ems.dispatch.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, String> {
    List<Device> findBySiteIdOrderById(String siteId);
    List<Device> findByEnabledTrue();
}
