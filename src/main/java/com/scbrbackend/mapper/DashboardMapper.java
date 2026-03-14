package com.scbrbackend.mapper;

import com.scbrbackend.model.dto.dashboard.DashboardNameValueItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    // Admin KPIs
    Integer getTotalTaskCount();
    Map<String, Object> getGlobalAttendanceRate(@Param("days") int days);
    Double getGlobalAverageScore(@Param("days") int days);
    List<DashboardNameValueItem> getGlobalBehaviorPie(@Param("days") int days);
    List<DashboardNameValueItem> getGlobalTeacherScoreRanking(@Param("days") int days, @Param("limit") int limit);
    List<Map<String, Object>> getGlobalScoreTrend(@Param("days") int days);

    // Teacher KPIs
    Integer getTeacherCourseCount(@Param("teacherId") Long teacherId);
    Map<String, Object> getTeacherAttendanceRate(@Param("teacherId") Long teacherId, @Param("days") int days);
    Double getTeacherAverageScore(@Param("teacherId") Long teacherId, @Param("days") int days);
    List<DashboardNameValueItem> getTeacherBehaviorPie(@Param("teacherId") Long teacherId, @Param("days") int days);
    List<DashboardNameValueItem> getTeacherCourseScoreRanking(@Param("teacherId") Long teacherId, @Param("days") int days);
    List<Map<String, Object>> getTeacherScoreTrend(@Param("teacherId") Long teacherId, @Param("days") int days);
}
