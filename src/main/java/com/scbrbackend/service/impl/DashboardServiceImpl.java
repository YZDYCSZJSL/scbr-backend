package com.scbrbackend.service.impl;

import com.scbrbackend.mapper.DashboardMapper;
import com.scbrbackend.model.dto.dashboard.DashboardKpiItem;
import com.scbrbackend.model.dto.dashboard.DashboardNameValueItem;
import com.scbrbackend.model.dto.dashboard.DashboardOverviewDTO;
import com.scbrbackend.model.dto.dashboard.DashboardUserInfo;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    @Override
    public DashboardOverviewDTO getOverview(Teacher teacher, int days) {
        DashboardOverviewDTO dto = new DashboardOverviewDTO();
        dto.setRole(teacher.getRole());

        DashboardUserInfo userInfo = new DashboardUserInfo();
        userInfo.setEmpNo(teacher.getEmpNo());
        userInfo.setName(teacher.getName());
        userInfo.setRole(teacher.getRole());
        userInfo.setDepartment(teacher.getDepartment());
        dto.setUserInfo(userInfo);

        if (teacher.getRole() != null && teacher.getRole() == 1) {
            buildAdminOverview(dto, days);
        } else {
            buildTeacherOverview(dto, teacher.getId(), days);
        }
        return dto;
    }

    private void buildAdminOverview(DashboardOverviewDTO dto, int days) {
        List<DashboardKpiItem> kpi = new ArrayList<>();
        kpi.add(new DashboardKpiItem("全校累计分析任务数", dashboardMapper.getTotalTaskCount().doubleValue(), "次"));

        Map<String, Object> attRateMap = dashboardMapper.getGlobalAttendanceRate(days);
        kpi.add(new DashboardKpiItem("近七日课堂出勤率", calculateRate(attRateMap), "%"));

        Double avgScore = dashboardMapper.getGlobalAverageScore(days);
        kpi.add(new DashboardKpiItem("近七日全校课堂平均专注度得分", formatDouble(avgScore), "分"));
        dto.setKpi(kpi);

        dto.setPieTitle("各行为占比画像");
        dto.setPie(sanitizeItems(dashboardMapper.getGlobalBehaviorPie(days)));

        dto.setBarTitle("全校教师课堂平均专注度排行 Top 5");
        dto.setBar(sanitizeItems(dashboardMapper.getGlobalTeacherScoreRanking(days, 5)));

        dto.setLineTitle("近七日全校课堂平均专注度趋势");
        buildLineChart(dto, dashboardMapper.getGlobalScoreTrend(days), days);
    }

    private void buildTeacherOverview(DashboardOverviewDTO dto, Long teacherId, int days) {
        List<DashboardKpiItem> kpi = new ArrayList<>();
        kpi.add(new DashboardKpiItem("本学期任课课程数", dashboardMapper.getTeacherCourseCount(teacherId).doubleValue(), "门"));

        Map<String, Object> attRateMap = dashboardMapper.getTeacherAttendanceRate(teacherId, days);
        kpi.add(new DashboardKpiItem("近七日该教师课堂出勤率", calculateRate(attRateMap), "%"));

        Double avgScore = dashboardMapper.getTeacherAverageScore(teacherId, days);
        kpi.add(new DashboardKpiItem("近七日课堂平均专注度得分", formatDouble(avgScore), "分"));
        dto.setKpi(kpi);

        dto.setPieTitle("各行为占比画像");
        dto.setPie(sanitizeItems(dashboardMapper.getTeacherBehaviorPie(teacherId, days)));

        dto.setBarTitle("教师课程专注度排行");
        dto.setBar(sanitizeItems(dashboardMapper.getTeacherCourseScoreRanking(teacherId, days)));

        dto.setLineTitle("近七日课堂平均专注度趋势");
        buildLineChart(dto, dashboardMapper.getTeacherScoreTrend(teacherId, days), days);
    }

    private Double calculateRate(Map<String, Object> rateMap) {
        if (rateMap == null || rateMap.get("total_attendance") == null || rateMap.get("total_student") == null) {
            return 0.0;
        }
        double att = Double.parseDouble(rateMap.get("total_attendance").toString());
        double stu = Double.parseDouble(rateMap.get("total_student").toString());
        if (stu == 0)
            return 0.0;
        return formatDouble((att / stu) * 100);
    }

    private Double formatDouble(Double val) {
        if (val == null)
            return 0.0;
        return Math.round(val * 10.0) / 10.0;
    }

    private List<DashboardNameValueItem> sanitizeItems(List<DashboardNameValueItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        items.forEach(item -> {
            if (item.getValue() == null) {
                item.setValue(0.0);
            } else {
                item.setValue(formatDouble(item.getValue()));
            }
        });
        return items;
    }

    private void buildLineChart(DashboardOverviewDTO dto, List<Map<String, Object>> records, int days) {
        List<String> dates = new ArrayList<>();
        List<Number> values = new ArrayList<>();

        Map<String, Double> dataMap = records.stream().collect(Collectors.toMap(
                m -> m.get("dateStr").toString(),
                m -> formatDouble(m.get("score") == null ? 0.0 : Double.parseDouble(m.get("score").toString()))));

        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = days - 1; i >= 0; i--) {
            String dateStr = today.minusDays(i).format(formatter);
            dates.add(dateStr);
            values.add(dataMap.getOrDefault(dateStr, 0.0));
        }

        dto.setLineDates(dates);
        dto.setLineValues(values);
    }
}
