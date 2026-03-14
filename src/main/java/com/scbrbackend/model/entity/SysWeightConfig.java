package com.scbrbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统行为权重配置方案实体类
 */
@Data
@TableName("sys_weight_config")
public class SysWeightConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private Integer isActive; // 1-已激活, 0-未激活
    private String configContent; // 配置明文参数(包含多种行为的具体权重), 存JSON字符串
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
