package com.scbrbackend.model.dto.dashboard;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardKpiItem {
    private String label;
    private Double value;
    private String unit;
}
