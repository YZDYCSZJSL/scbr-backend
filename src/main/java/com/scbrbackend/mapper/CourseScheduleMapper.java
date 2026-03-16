package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.model.vo.SchedulePageVO;
import com.scbrbackend.model.vo.ScheduleAnalysisItemVO;
import com.scbrbackend.model.vo.ScheduleAnalysisInitVO;
import com.scbrbackend.model.vo.ScheduleConflictItemVO;
import com.scbrbackend.model.vo.WeekScheduleItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseScheduleMapper extends BaseMapper<CourseSchedule> {

    /**
     * 分页查询排课（联表查询课程/教师/教室/节次信息）
     */
    List<SchedulePageVO> selectSchedulePage(@Param("query") com.scbrbackend.model.dto.SchedulePageQueryDTO query);

    /**
     * 查询排课总数（用于分页）
     */
    long selectSchedulePageCount(@Param("query") com.scbrbackend.model.dto.SchedulePageQueryDTO query);

    /**
     * 教师冲突检测（基于规则型排课模型）
     * 通过联查 sys_section_time 比较 section_no 判断节次范围重叠
     */
    int checkTeacherConflict(@Param("teacherId") Long teacherId,
                             @Param("academicYear") String academicYear,
                             @Param("semester") Integer semester,
                             @Param("weekday") Integer weekday,
                             @Param("startSectionNo") Integer startSectionNo,
                             @Param("endSectionNo") Integer endSectionNo,
                             @Param("startWeek") Integer startWeek,
                             @Param("endWeek") Integer endWeek,
                             @Param("weekType") Integer weekType,
                             @Param("excludeId") Long excludeId);

    /**
     * 教室冲突检测（基于规则型排课模型）
     */
    int checkClassroomConflict(@Param("classroomId") Long classroomId,
                               @Param("academicYear") String academicYear,
                               @Param("semester") Integer semester,
                               @Param("weekday") Integer weekday,
                               @Param("startSectionNo") Integer startSectionNo,
                               @Param("endSectionNo") Integer endSectionNo,
                               @Param("startWeek") Integer startWeek,
                               @Param("endWeek") Integer endWeek,
                               @Param("weekType") Integer weekType,
                               @Param("excludeId") Long excludeId);

    /**
     * 查询 analysis-list（文件流：status=1或2 的排课）
     */
    List<ScheduleAnalysisItemVO> selectAnalysisListForFile(@Param("teacherId") Long teacherId);

    /**
     * 查询 analysis-list（实时流：基于当前周次、星期、节次筛选正在上课的排课）
     */
    List<ScheduleAnalysisItemVO> selectAnalysisListForStream(@Param("teacherId") Long teacherId,
                                                             @Param("academicYear") String academicYear,
                                                             @Param("semester") Integer semester,
                                                             @Param("currentWeek") int currentWeek,
                                                             @Param("currentWeekday") int currentWeekday,
                                                             @Param("currentSectionNo") Integer currentSectionNo);

    /**
     * 查询教师排课冲突明细
     */
    List<ScheduleConflictItemVO> selectTeacherConflictList(@Param("teacherId") Long teacherId,
                                                           @Param("academicYear") String academicYear,
                                                           @Param("semester") Integer semester,
                                                           @Param("weekday") Integer weekday,
                                                           @Param("startSectionNo") Integer startSectionNo,
                                                           @Param("endSectionNo") Integer endSectionNo,
                                                           @Param("startWeek") Integer startWeek,
                                                           @Param("endWeek") Integer endWeek,
                                                           @Param("weekType") Integer weekType,
                                                           @Param("excludeId") Long excludeId);

    /**
     * 查询教室排课冲突明细
     */
    List<ScheduleConflictItemVO> selectClassroomConflictList(@Param("classroomId") Long classroomId,
                                                             @Param("academicYear") String academicYear,
                                                             @Param("semester") Integer semester,
                                                             @Param("weekday") Integer weekday,
                                                             @Param("startSectionNo") Integer startSectionNo,
                                                             @Param("endSectionNo") Integer endSectionNo,
                                                             @Param("startWeek") Integer startWeek,
                                                             @Param("endWeek") Integer endWeek,
                                                             @Param("weekType") Integer weekType,
                                                             @Param("excludeId") Long excludeId);

    /**
     * 查询周课表视图
     */
    List<WeekScheduleItemVO> selectWeekView(@Param("academicYear") String academicYear,
                                            @Param("semester") Integer semester,
                                            @Param("teacherId") Long teacherId,
                                            @Param("classroomId") Long classroomId);

    /**
     * 排课预填信息查询
     */
    ScheduleAnalysisInitVO selectAnalysisInitData(@Param("scheduleId") Long scheduleId);
}
