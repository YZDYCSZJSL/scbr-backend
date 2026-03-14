package com.scbrbackend.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReportExportDTO {
    /**
     * 包含选中记录 ID 的数组
     */
    private List<Long> ids;
}
