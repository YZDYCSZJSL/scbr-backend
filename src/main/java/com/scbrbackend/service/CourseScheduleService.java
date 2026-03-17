package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.mapper.*;
import com.scbrbackend.model.dto.ScheduleConflictCheckDTO;
import com.scbrbackend.model.dto.SchedulePageQueryDTO;
import com.scbrbackend.model.dto.ScheduleRequestDTO;
import com.scbrbackend.model.entity.*;
import com.scbrbackend.model.vo.ScheduleAnalysisInitVO;
import com.scbrbackend.model.vo.ScheduleAnalysisItemVO;
import com.scbrbackend.model.vo.ScheduleConflictItemVO;
import com.scbrbackend.model.vo.ScheduleConflictVO;
import com.scbrbackend.model.vo.SchedulePageVO;
import com.scbrbackend.model.vo.SectionTimeVO;
import com.scbrbackend.model.vo.WeekScheduleItemVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CourseScheduleService extends ServiceImpl<CourseScheduleMapper, CourseSchedule> {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ClassroomMapper classroomMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private SysSectionTimeMapper sectionTimeMapper;

    @Autowired
    private SysSemesterConfigMapper semesterConfigMapper;

    // ======================== 新增/编辑 ========================

    public Result<String> saveOrUpdateSchedule(ScheduleRequestDTO dto) {
        // 1. 编辑时校验状态锁，仅允许修改 status=0 的排课
        if (dto.getId() != null) {
            CourseSchedule existing = this.getById(dto.getId());
            if (existing == null) {
                throw new BusinessException(500, "排课记录不存在！");
            }
            if (existing.getStatus() != null && existing.getStatus() != 0) {
                throw new BusinessException(500, "操作失败：已开始或已结束的课程无法修改！");
            }
        }

        // 2. 基本校验
        if (dto.getAcademicYear() == null || dto.getAcademicYear().isEmpty()) {
            throw new BusinessException(500, "学年不能为空！");
        }
        if (dto.getSemester() == null || (dto.getSemester() != 1 && dto.getSemester() != 2)) {
            throw new BusinessException(500, "学期必须为 1 或 2！");
        }

        // 校验学期配置是否存在
        Long configCount = semesterConfigMapper.selectCount(
                new LambdaQueryWrapper<SysSemesterConfig>()
                        .eq(SysSemesterConfig::getAcademicYear, dto.getAcademicYear())
                        .eq(SysSemesterConfig::getSemester, dto.getSemester())
        );
        if (configCount == null || configCount == 0) {
            throw new BusinessException(400, "所选学年学期未配置，请先维护学期配置");
        }

        if (dto.getCourseId() == null || courseMapper.selectById(dto.getCourseId()) == null) {
            throw new BusinessException(500, "课程不存在！");
        }
        if (dto.getTeacherId() == null || teacherMapper.selectById(dto.getTeacherId()) == null) {
            throw new BusinessException(500, "教师不存在！");
        }
        if (dto.getClassroomId() == null || classroomMapper.selectById(dto.getClassroomId()) == null) {
            throw new BusinessException(500, "教室不存在！");
        }
        if (dto.getWeekday() == null || dto.getWeekday() < 1 || dto.getWeekday() > 7) {
            throw new BusinessException(500, "星期必须在 1-7 之间！");
        }

        // 3. 校验节次
        SysSectionTime startSection = sectionTimeMapper.selectById(dto.getStartSectionId());
        SysSectionTime endSection = sectionTimeMapper.selectById(dto.getEndSectionId());
        if (startSection == null) {
            throw new BusinessException(500, "开始节次不存在！");
        }
        if (endSection == null) {
            throw new BusinessException(500, "结束节次不存在！");
        }
        // 基于 section_no 比较，不直接假设 id 顺序可比
        if (startSection.getSectionNo() > endSection.getSectionNo()) {
            throw new BusinessException(500, "开始节次不能晚于结束节次！");
        }

        // 4. 校验周次
        if (dto.getStartWeek() == null || dto.getEndWeek() == null) {
            throw new BusinessException(500, "起止周次不能为空！");
        }
        if (dto.getStartWeek() > dto.getEndWeek()) {
            throw new BusinessException(500, "开始周不能大于结束周！");
        }
        if (dto.getWeekType() == null || dto.getWeekType() < 0 || dto.getWeekType() > 2) {
            throw new BusinessException(500, "周次类型必须为 0(全周)/1(单周)/2(双周)！");
        }

        // 5. 冲突检测 —— 基于 section_no 比较
        int teacherConflict = this.baseMapper.checkTeacherConflict(
                dto.getTeacherId(), dto.getAcademicYear(), dto.getSemester(),
                dto.getWeekday(),
                startSection.getSectionNo(), endSection.getSectionNo(),
                dto.getStartWeek(), dto.getEndWeek(), dto.getWeekType(),
                dto.getId());
        if (teacherConflict > 0) {
            throw new BusinessException(500, "排课冲突：当前选定时段该教师已安排其他课程，请重新规划！");
        }

        int classroomConflict = this.baseMapper.checkClassroomConflict(
                dto.getClassroomId(), dto.getAcademicYear(), dto.getSemester(),
                dto.getWeekday(),
                startSection.getSectionNo(), endSection.getSectionNo(),
                dto.getStartWeek(), dto.getEndWeek(), dto.getWeekType(),
                dto.getId());
        if (classroomConflict > 0) {
            throw new BusinessException(500, "排课冲突：该教室在指定时段内已被占用！");
        }

        // 6. 落库
        CourseSchedule schedule = new CourseSchedule();
        BeanUtils.copyProperties(dto, schedule);
        if (schedule.getId() == null && schedule.getStatus() == null) {
            schedule.setStatus(0);
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (schedule.getCreatedAt() == null) {
            schedule.setCreatedAt(now);
        }
        schedule.setUpdatedAt(now);

        this.saveOrUpdate(schedule);
        dto.setId(schedule.getId());
        return Result.success("保存成功！数据无冲突。", null);
    }

    // ======================== 分页查询 ========================

    public Result<PageResult<SchedulePageVO>> getSchedulePage(SchedulePageQueryDTO query) {
        long total = this.baseMapper.selectSchedulePageCount(query);
        List<SchedulePageVO> records = this.baseMapper.selectSchedulePage(query);

        PageResult<SchedulePageVO> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        result.setRecords(records);
        return Result.success("success", result);
    }

    // ======================== 删除 ========================

    public Result<Object> deleteSchedule(Long id) {
        CourseSchedule schedule = this.getById(id);
        if (schedule == null) {
            return Result.error(404, "排课不存在");
        }
        if (schedule.getStatus() != null && schedule.getStatus() != 0) {
            return Result.error(500, "无法删除已开始或已结束的排课！");
        }
        this.removeById(id);
        return Result.success("删除成功！", null);
    }

    // ======================== analysis-list ========================

    public Result<List<ScheduleAnalysisItemVO>> getAnalysisList(int streamType, String token) {
        com.scbrbackend.common.context.CurrentUser currentUser = com.scbrbackend.common.context.UserContext.getCurrentUser();
        if (currentUser == null) {
            return Result.error(401, "无效的用户令牌！");
        }
        Teacher currentTeacher = teacherMapper.selectById(currentUser.getId());
        if (currentTeacher == null) {
            return Result.error(401, "无效的用户令牌！");
        }

        // 普通教师仅看自己的排课，管理员看全部
        Long teacherIdFilter = (currentTeacher.getRole() == 1) ? null : currentTeacher.getId();

        if (streamType == 2) {
            // 文件流场景：查 status=1 或 2 的排课
            List<ScheduleAnalysisItemVO> list = this.baseMapper.selectAnalysisListForFile(teacherIdFilter);
            return Result.success("success", list);
        } else if (streamType == 1) {
            // 实时流场景：依赖 sys_semester_config 推算当前周次 + 匹配当前节次
            return getAnalysisListForStream(teacherIdFilter);
        }

        return Result.error(400, "无效的 streamType 参数！");
    }

    /**
     * 实时流 analysis-list 实现
     * 依赖 sys_semester_config 学期配置表推算当前周次
     */
    private Result<List<ScheduleAnalysisItemVO>> getAnalysisListForStream(Long teacherIdFilter) {
        // 1. 获取当前启用的学期配置
        SysSemesterConfig config = semesterConfigMapper.selectOne(
                new LambdaQueryWrapper<SysSemesterConfig>()
                        .eq(SysSemesterConfig::getStatus, 1)
                        .orderByDesc(SysSemesterConfig::getCreatedAt)
                        .last("LIMIT 1"));

        if (config == null || config.getTermStartDate() == null) {
            return Result.error(500,
                    "实时流排课查询不可用：未配置有效的学期信息（sys_semester_config），请管理员先设置当前学期的开学日期。");
        }

        // 2. 推算当前周次
        LocalDate today = LocalDate.now();
        LocalDate termStart = config.getTermStartDate();
        if (today.isBefore(termStart)) {
            return Result.success("当前日期在开学日期之前，暂无进行中的课程。",
                    java.util.Collections.emptyList());
        }
        long daysDiff = ChronoUnit.DAYS.between(termStart, today);
        int currentWeek = (int) (daysDiff / 7) + 1;

        // 3. 获取当前星期几 (1=周一 ... 7=周日)
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        int currentWeekday = dayOfWeek.getValue();

        // 4. 推算当前节次
        LocalTime now = LocalTime.now();
        Integer currentSectionNo = getCurrentSectionNo(now);

        // 如果当前不在任何节次时段内，则实时流返回空，不要返回全天所有课
        if (currentSectionNo == null) {
            return Result.success("当前不在任何排课节次时间内。", java.util.Collections.emptyList());
        }

        // 5. 查询
        List<ScheduleAnalysisItemVO> list = this.baseMapper.selectAnalysisListForStream(
                teacherIdFilter,
                config.getAcademicYear(),
                config.getSemester(),
                currentWeek,
                currentWeekday,
                currentSectionNo);

        return Result.success("success", list);
    }

    /**
     * 根据当前时间匹配 sys_section_time 中正在进行的节次编号
     * 如果当前时间不在任何节次范围内，返回 null（此时 SQL 将不对节次做筛选，
     * 即返回当天该周同星期的全部排课，由前端展示参考）
     */
    private Integer getCurrentSectionNo(LocalTime now) {
        List<SysSectionTime> sections = sectionTimeMapper.selectList(
                new LambdaQueryWrapper<SysSectionTime>()
                        .eq(SysSectionTime::getStatus, 1)
                        .orderByAsc(SysSectionTime::getSortNo));

        for (SysSectionTime s : sections) {
            if (!now.isBefore(s.getStartTime()) && !now.isAfter(s.getEndTime())) {
                return s.getSectionNo();
            }
        }
        return null; // 当前不在任何节次时段内
    }

    // ======================== 节次列表（供 Controller 调用） ========================

    public List<SectionTimeVO> getSectionTimeList() {
        List<SysSectionTime> list = sectionTimeMapper.selectList(
                new LambdaQueryWrapper<SysSectionTime>()
                        .eq(SysSectionTime::getStatus, 1)
                        .orderByAsc(SysSectionTime::getSortNo)
                        .orderByAsc(SysSectionTime::getSectionNo));
        return list.stream().map(s -> {
            SectionTimeVO vo = new SectionTimeVO();
            BeanUtils.copyProperties(s, vo);
            return vo;
        }).collect(java.util.stream.Collectors.toList());
    }

    // ======================== 冲突检测 ========================

    public Result<ScheduleConflictVO> checkConflict(ScheduleConflictCheckDTO dto) {
        if (dto.getAcademicYear() == null || dto.getAcademicYear().trim().isEmpty()) {
            return Result.error(400, "学年不能为空");
        }
        if (dto.getSemester() == null || (dto.getSemester() != 1 && dto.getSemester() != 2)) {
            return Result.error(400, "学期只能是 1 或 2");
        }
        if (dto.getWeekday() == null || dto.getWeekday() < 1 || dto.getWeekday() > 7) {
            return Result.error(400, "星期只能是 1~7");
        }
        if (dto.getWeekType() == null || dto.getWeekType() < 0 || dto.getWeekType() > 2) {
            return Result.error(400, "周次类型只能是 0/1/2");
        }
        if (dto.getStartWeek() == null || dto.getEndWeek() == null) {
            return Result.error(400, "起止周次不能为空");
        }
        if (dto.getStartSectionId() == null || dto.getEndSectionId() == null) {
            return Result.error(400, "节次参数缺失");
        }
        SysSectionTime startSection = sectionTimeMapper.selectById(dto.getStartSectionId());
        SysSectionTime endSection = sectionTimeMapper.selectById(dto.getEndSectionId());
        if (startSection == null || endSection == null) {
            return Result.error(400, "指定的节次不存在");
        }
        if (startSection.getSectionNo() > endSection.getSectionNo()) {
            return Result.error(400, "开始节次不能晚于结束节次");
        }
        if (dto.getStartWeek() > dto.getEndWeek()) {
            return Result.error(400, "开始周不能大于结束周");
        }

        List<ScheduleConflictItemVO> teacherConflictList = new java.util.ArrayList<>();
        List<ScheduleConflictItemVO> classroomConflictList = new java.util.ArrayList<>();

        if (dto.getTeacherId() != null) {
            teacherConflictList = this.baseMapper.selectTeacherConflictList(
                    dto.getTeacherId(), dto.getAcademicYear(), dto.getSemester(),
                    dto.getWeekday(), startSection.getSectionNo(), endSection.getSectionNo(),
                    dto.getStartWeek(), dto.getEndWeek(), dto.getWeekType(), dto.getId());
        }

        if (dto.getClassroomId() != null) {
            classroomConflictList = this.baseMapper.selectClassroomConflictList(
                    dto.getClassroomId(), dto.getAcademicYear(), dto.getSemester(),
                    dto.getWeekday(), startSection.getSectionNo(), endSection.getSectionNo(),
                    dto.getStartWeek(), dto.getEndWeek(), dto.getWeekType(), dto.getId());
        }

        ScheduleConflictVO vo = new ScheduleConflictVO();
        vo.setTeacherConflictList(teacherConflictList);
        vo.setClassroomConflictList(classroomConflictList);
        vo.setTeacherConflict(!teacherConflictList.isEmpty());
        vo.setClassroomConflict(!classroomConflictList.isEmpty());
        vo.setHasConflict(vo.getTeacherConflict() || vo.getClassroomConflict());

        return Result.success("success", vo);
    }

    // ======================== 周课表视图查询 ========================

    public Result<List<WeekScheduleItemVO>> getWeekView(String academicYear, Integer semester, Long teacherId, Long classroomId) {
        if (academicYear == null || semester == null) {
            return Result.error(400, "学年和学期不能为空");
        }
        List<WeekScheduleItemVO> list = this.baseMapper.selectWeekView(academicYear, semester, teacherId, classroomId);
        return Result.success("success", list);
    }

    // ======================== 排课预填信息查询 ========================

    public Result<ScheduleAnalysisInitVO> getAnalysisInitInfo(Long id) {
        if (id == null) {
            return Result.error(400, "排课ID不能为空！");
        }
        ScheduleAnalysisInitVO vo = this.baseMapper.selectAnalysisInitData(id);
        if (vo == null) {
            return Result.error(404, "该排课记录不存在，或已被删除！");
        }

        // 组装文本字段
        String[] weekdays = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        if (vo.getWeekday() != null && vo.getWeekday() >= 1 && vo.getWeekday() <= 7) {
            vo.setWeekdayName(weekdays[vo.getWeekday()]);
        }

        if (vo.getStartSectionNo() != null && vo.getEndSectionNo() != null) {
            vo.setSectionRangeText("第" + vo.getStartSectionNo() + "-" + vo.getEndSectionNo() + "节");
        }

        if (vo.getStartSectionTime() != null && vo.getEndSectionTime() != null) {
            java.time.format.DateTimeFormatter timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
            vo.setSectionTimeText(vo.getStartSectionTime().format(timeFormatter) + " ~ " + vo.getEndSectionTime().format(timeFormatter));
        }

        if (vo.getWeekType() != null) {
            String typeName = vo.getWeekType() == 0 ? "全周" : (vo.getWeekType() == 1 ? "单周" : "双周");
            vo.setWeekTypeName(typeName);
            if (vo.getStartWeek() != null && vo.getEndWeek() != null) {
                if (vo.getWeekType() == 0) {
                    vo.setWeekRangeText(vo.getStartWeek() + "-" + vo.getEndWeek() + "周");
                } else {
                    vo.setWeekRangeText(vo.getStartWeek() + "-" + vo.getEndWeek() + "周 " + typeName);
                }
            }
        }

        return Result.success("success", vo);
    }
}
