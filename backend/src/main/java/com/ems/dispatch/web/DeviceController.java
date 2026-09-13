package com.ems.dispatch.web;

import com.ems.dispatch.masterdata.Device;
import com.ems.dispatch.masterdata.DeviceRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 设备台账管理。 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceRepository repository;

    public DeviceController(DeviceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Device> list(@RequestParam(required = false) String siteId) {
        return siteId == null ? repository.findAll()
                : repository.findBySiteIdOrderById(siteId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> get(@PathVariable String id) {
        return repository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Device create(@Valid @RequestBody Device device) {
        return repository.save(device);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Device> update(@PathVariable String id, @RequestBody Device body) {
        return repository.findById(id).map(d -> {
            d.setName(body.getName());
            d.setType(body.getType());
            d.setProtocol(body.getProtocol());
            d.setEndpoint(body.getEndpoint());
            d.setSiteId(body.getSiteId());
            d.setEnabled(body.isEnabled());
            return ResponseEntity.ok(repository.save(d));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
