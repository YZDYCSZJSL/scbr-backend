package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.SystemConfigDTO;
import com.scbrbackend.service.SysWeightConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/config")
public class SysWeightConfigController {

    @Autowired
    private SysWeightConfigService sysWeightConfigService;

    @GetMapping("/page")
    public Result<PageResult<SystemConfigDTO>> getConfigPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return sysWeightConfigService.getConfigPage(page, size);
    }

    @PostMapping
    public Result<Object> addConfig(@RequestBody SystemConfigDTO requestDTO) {
        return sysWeightConfigService.addConfig(requestDTO);
    }

    @PutMapping
    public Result<Object> updateConfig(@RequestBody SystemConfigDTO requestDTO) {
        return sysWeightConfigService.updateConfig(requestDTO);
    }

    @DeleteMapping("/{id}")
    public Result<Object> deleteConfig(@PathVariable Long id) {
        return sysWeightConfigService.deleteConfig(id);
    }

    @PutMapping("/{id}/active")
    public Result<String> activateConfig(@PathVariable Long id) {
        return sysWeightConfigService.activateConfig(id);
    }

    @PostMapping("/import")
    public Result<String> importConfig(@RequestParam("file") MultipartFile file) {
        return sysWeightConfigService.importConfig(file);
    }
}
