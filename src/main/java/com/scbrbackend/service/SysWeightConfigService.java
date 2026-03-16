package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ConfigImportDTO;
import com.scbrbackend.model.dto.SystemConfigDTO;
import com.scbrbackend.model.entity.SysWeightConfig;
import com.scbrbackend.mapper.SysWeightConfigMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class SysWeightConfigService extends ServiceImpl<SysWeightConfigMapper, SysWeightConfig> {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 一键激活指定方案，触发独占机制
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<String> activateConfig(Long id) {
        // 1. 将所有配置 is_active 置为 0 (独占性排他)
        LambdaUpdateWrapper<SysWeightConfig> updateAll = new LambdaUpdateWrapper<>();
        updateAll.set(SysWeightConfig::getIsActive, 0);
        this.update(updateAll);

        // 2. 将指定 id 配置置为 1
        LambdaUpdateWrapper<SysWeightConfig> updateOne = new LambdaUpdateWrapper<>();
        updateOne.eq(SysWeightConfig::getId, id)
                .set(SysWeightConfig::getIsActive, 1);
        this.update(updateOne);

        SysWeightConfig activeConfig = this.getById(id);
        if (activeConfig == null) {
            throw new RuntimeException("配置方案不存在");
        }

        return Result.success("成功切换使用 [" + activeConfig.getConfigName() + "]", null);
    }

    /**
     * 导入静态 JSON 配置文件
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<String> importConfig(MultipartFile file) {
        try {
            // 解析 JSON
            ConfigImportDTO dto = objectMapper.readValue(file.getInputStream(), ConfigImportDTO.class);

            SysWeightConfig config = new SysWeightConfig();
            config.setConfigName(dto.getConfig_name());
            config.setDescription(dto.getDescription());
            config.setIsActive(dto.getIs_active() != null ? dto.getIs_active() : 0);

            // 将内部对象数组序列化为 JSON 字符串进行存储
            String contentJson = objectMapper.writeValueAsString(dto.getConfig_content());
            config.setConfigContent(contentJson);

            this.save(config);

            // 前端仍然可以接收原有的 Result<String>，为不破坏兼容性，只返回 ID 的 String
            return Result.success("成功载入配置方案", config.getId().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(500, "解析 JSON 失败: " + e.getMessage());
        }
    }

    public Result<PageResult<SystemConfigDTO>> getConfigPage(int page, int size) {
        Page<SysWeightConfig> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysWeightConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysWeightConfig::getCreatedAt);

        Page<SysWeightConfig> configPage = this.page(pageParam, wrapper);

        List<SystemConfigDTO> dtoList = configPage.getRecords().stream().map(config -> {
            SystemConfigDTO dto = new SystemConfigDTO();
            BeanUtils.copyProperties(config, dto);
            dto.setConfigName(config.getConfigName());
            dto.setIsActive(config.getIsActive());
            dto.setConfigContent(config.getConfigContent());
            return dto;
        }).collect(Collectors.toList());

        PageResult<SystemConfigDTO> result = new PageResult<>();
        result.setTotal(configPage.getTotal());
        result.setPage(configPage.getCurrent());
        result.setSize(configPage.getSize());
        result.setRecords(dtoList);

        return Result.success("success", result);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> addConfig(SystemConfigDTO requestDTO) {
        SysWeightConfig config = new SysWeightConfig();
        BeanUtils.copyProperties(requestDTO, config);
        config.setConfigName(requestDTO.getConfigName());
        config.setIsActive(requestDTO.getIsActive() != null ? requestDTO.getIsActive() : 0);
        config.setConfigContent(requestDTO.getConfigContent());

        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        this.save(config);

        if (config.getIsActive() == 1) {
            activateConfig(config.getId());
        }

        requestDTO.setId(config.getId());
        return Result.success("新增保存成功！", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Object> updateConfig(SystemConfigDTO requestDTO) {
        SysWeightConfig config = this.getById(requestDTO.getId());
        if (config == null)
            return Result.error(404, "配置方案不存在");

        if (requestDTO.getConfigName() != null)
            config.setConfigName(requestDTO.getConfigName());
        if (requestDTO.getDescription() != null)
            config.setDescription(requestDTO.getDescription());
        if (requestDTO.getConfigContent() != null)
            config.setConfigContent(requestDTO.getConfigContent());

        if (requestDTO.getIsActive() != null) {
            config.setIsActive(requestDTO.getIsActive());
        }

        config.setUpdatedAt(LocalDateTime.now());
        this.updateById(config);

        if (config.getIsActive() == 1) {
            activateConfig(config.getId());
        }

        return Result.success("编辑保存成功！", null);
    }

    public Result<Object> deleteConfig(Long id) {
        SysWeightConfig config = this.getById(id);
        if (config == null)
            return Result.error(404, "配置不存在");

        if (config.getIsActive() == 1) {
            return Result.error(500, "不能删除正在激活中的配置！");
        }

        this.removeById(id);
        return Result.success("删除成功！", null);
    }
}
