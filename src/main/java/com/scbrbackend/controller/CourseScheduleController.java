package com.scbrbackend.controller;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.SchedulePageQueryDTO;
import com.scbrbackend.model.dto.ScheduleRequestDTO;
import com.scbrbackend.model.vo.ScheduleAnalysisInitVO;
import com.scbrbackend.model.vo.ScheduleAnalysisItemVO;
import com.scbrbackend.model.vo.SchedulePageVO;
import com.scbrbackend.service.CourseScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.scbrbackend.common.exception.BusinessException;

import java.util.List;



@RestController
@RequestMapping("/api/v1/admin/schedule")
public class CourseScheduleController {

    @Autowired
    private CourseScheduleService courseScheduleService;



    @Autowired
    private com.scbrbackend.service.LogService logService;



    // ======================== 分页查询 ========================

    @GetMapping("/page")
    public Result<PageResult<SchedulePageVO>> getSchedulePage(SchedulePageQueryDTO query) {
        return courseScheduleService.getSchedulePage(query);
    }

    // ======================== 新增排课 ========================

    @PostMapping
    public Result<String> addSchedule(@RequestBody ScheduleRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<String> res = courseScheduleService.saveOrUpdateSchedule(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                        "排课管理", "CREATE_SCHEDULE", requestDTO.getId(), "新增排课", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                        "排课管理", "CREATE_SCHEDULE", null, "新增排课失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                    "排课管理", "CREATE_SCHEDULE", null, "新增排课失败：" + msg, 0);
            throw e;
        }
    }

    // ======================== 修改排课 ========================

    @PutMapping
    public Result<String> updateSchedule(@RequestBody ScheduleRequestDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<String> res = courseScheduleService.saveOrUpdateSchedule(requestDTO);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                        "排课管理", "UPDATE_SCHEDULE", requestDTO.getId(), "修改排课", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                        "排课管理", "UPDATE_SCHEDULE", requestDTO.getId(),
                        "修改排课失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                    "排课管理", "UPDATE_SCHEDULE", requestDTO.getId(),
                    "修改排课失败：" + msg, 0);
            throw e;
        }
    }

    // ======================== 删除排课 ========================

    @DeleteMapping("/{id}")
    public Result<Object> deleteSchedule(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        try {
            Result<Object> res = courseScheduleService.deleteSchedule(id);
            if (res.getCode() == 200) {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                        "排课管理", "DELETE_SCHEDULE", id, "删除排课", 1);
            } else {
                logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                        "排课管理", "DELETE_SCHEDULE", id,
                        "删除排课失败：" + res.getMessage(), 0);
            }
            return res;
        } catch (Exception e) {
            String msg = e instanceof BusinessException ? e.getMessage() : "系统内部异常";
            logService.recordOperationLog(teacher.getEmpNo(), teacher.getName(),
                    "排课管理", "DELETE_SCHEDULE", id, "删除排课失败：" + msg, 0);
            throw e;
        }
    }

    // ======================== analysis-list ========================

    @GetMapping("/analysis-list")
    public Result<List<ScheduleAnalysisItemVO>> getAnalysisList(
            @RequestParam int streamType,
            jakarta.servlet.http.HttpServletRequest request) {
        com.scbrbackend.common.context.CurrentUser user = com.scbrbackend.common.context.UserContext.getCurrentUser();
        // 如果这里原来的 streamType 等验证逻辑需要 token 字符串，可能需要适配，但从原本业务看 token 只是用来识别用户。
        // 原生业务里的 courseScheduleService.getAnalysisList(streamType, token) 是怎么写的呢?
        // 我们来看刚才搜到的 service实现。
        // 不过由于不改变Service层的方法签名，这里我们可以传入 user.getEmpNo() 等信息以代 token 或是传入 token 本身（因为需要被下游验证的话）...
        // 稍等，如果是放进拦截器验证，其实 downstream 用不到 token。
        // 但安全起见，我们暂且按原样截取 token 传入（虽然原样传 jwt 也可）。
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return courseScheduleService.getAnalysisList(streamType, token);
    }

    // ======================== 节次列表（新增） ========================

    @GetMapping("/section-time/list")
    public Result<List<com.scbrbackend.model.vo.SectionTimeVO>> getSectionTimeList(
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        if (teacher.getRole() != 1) {
            throw new BusinessException(403, "无权限访问节次列表！");
        }
        return Result.success("success", courseScheduleService.getSectionTimeList());
    }

    // ======================== 排课冲突检测（新增） ========================

    @PostMapping("/conflict/check")
    public Result<com.scbrbackend.model.vo.ScheduleConflictVO> checkConflict(
            @RequestBody com.scbrbackend.model.dto.ScheduleConflictCheckDTO requestDTO,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        if (teacher.getRole() != 1) {
            throw new BusinessException(403, "无权限进行排课冲突检测！");
        }
        return courseScheduleService.checkConflict(requestDTO);
    }

    // ======================== 周课表视图查询（新增） ========================

    @GetMapping("/week-view")
    public Result<List<com.scbrbackend.model.vo.WeekScheduleItemVO>> getWeekView(
            @RequestParam String academicYear,
            @RequestParam Integer semester,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classroomId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        if (teacher.getRole() != 1) {
            throw new BusinessException(403, "无权限查询周课表视图！");
        }
        return courseScheduleService.getWeekView(academicYear, semester, teacherId, classroomId);
    }

    // ======================== 排课预填信息查询（新增） ========================

    @GetMapping("/{id}/analysis-init")
    public Result<ScheduleAnalysisInitVO> getAnalysisInitInfo(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        com.scbrbackend.common.context.CurrentUser teacher = com.scbrbackend.common.context.UserContext.getCurrentUser();
        // 当前优先按管理员接口实现，若需放开给教师，需校验排课的teacher_id
        if (teacher.getRole() != 1) {
            throw new BusinessException(403, "无权限访问！");
        }
        return courseScheduleService.getAnalysisInitInfo(id);
    }
}
