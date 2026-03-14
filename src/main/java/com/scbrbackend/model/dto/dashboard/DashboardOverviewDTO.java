package com.scbrbackend.model.dto.dashboard;

import lombok.Data;
import java.util.List;

@Data
public class DashboardOverviewDTO {
    private Integer role;
    private DashboardUserInfo userInfo;

    private List<DashboardKpiItem> kpi;

    private String pieTitle;
    private List<DashboardNameValueItem> pie;

    private String barTitle;
    private List<DashboardNameValueItem> bar;

    private String lineTitle;
    private List<String> lineDates;
    private List<Number> lineValues;
}
