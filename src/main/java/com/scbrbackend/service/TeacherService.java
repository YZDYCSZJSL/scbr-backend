package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.TeacherRequestDTO;
import com.scbrbackend.model.dto.TeacherResponseDTO;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.mapper.CourseScheduleMapper;
import com.scbrbackend.mapper.TeacherMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeacherService extends ServiceImpl<TeacherMapper, Teacher> {

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    /**
     * 分页查询教师列表
     */
    public Result<PageResult<TeacherResponseDTO>> getTeacherPage(int page, int size, String keyword, Integer role,
            Integer status) {
        Page<Teacher> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(i -> i.like(Teacher::getEmpNo, keyword)
                    .or().like(Teacher::getName, keyword)
                    .or().like(Teacher::getDepartment, keyword));
        }
        if (role != null) {
            wrapper.eq(Teacher::getRole, role);
        }
        if (status != null) {
            wrapper.eq(Teacher::getStatus, status);
        }

        wrapper.orderByDesc(Teacher::getCreatedAt);

        Page<Teacher> teacherPage = this.page(pageParam, wrapper);

        List<TeacherResponseDTO> dtoList = teacherPage.getRecords().stream().map(teacher -> {
            TeacherResponseDTO dto = new TeacherResponseDTO();
            BeanUtils.copyProperties(teacher, dto);
            dto.setEmpNo(teacher.getEmpNo());
            dto.setCreatedAt(teacher.getCreatedAt());
            dto.setUpdatedAt(teacher.getUpdatedAt());
            return dto;
        }).collect(Collectors.toList());

        PageResult<TeacherResponseDTO> result = new PageResult<>();
        result.setTotal(teacherPage.getTotal());
        result.setPage(teacherPage.getCurrent());
        result.setSize(teacherPage.getSize());
        result.setRecords(dtoList);

        return Result.success("success", result);
    }

    /**
     * 新增教师
     */
    public Result<Object> addTeacher(TeacherRequestDTO requestDTO) {
        // 校验工号唯一
        long count = this.count(new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, requestDTO.getEmpNo()));
        if (count > 0) {
            return Result.error(400, "工号已存在！");
        }

        Teacher teacher = new Teacher();
        BeanUtils.copyProperties(requestDTO, teacher);
        teacher.setEmpNo(requestDTO.getEmpNo());

        // 密码加密
        if (StringUtils.hasText(requestDTO.getPassword())) {
            String encryptedPass = DigestUtils.md5DigestAsHex(requestDTO.getPassword().getBytes());
            teacher.setPassword(encryptedPass);
        } else {
            return Result.error(400, "初始密码不能为空！");
        }

        teacher.setCreatedAt(LocalDateTime.now());
        teacher.setUpdatedAt(LocalDateTime.now());

        this.save(teacher);
        requestDTO.setId(teacher.getId());
        return Result.success("新增教师成功！", null); // 或者返回 { "id": xxx }
    }

    /**
     * 编辑教师信息
     */
    public Result<Object> updateTeacher(TeacherRequestDTO requestDTO) {
        Teacher teacher = this.getById(requestDTO.getId());
        if (teacher == null) {
            return Result.error(404, "教师不存在");
        }

        // 校验工号更新是否冲突
        if (StringUtils.hasText(requestDTO.getEmpNo()) && !requestDTO.getEmpNo().equals(teacher.getEmpNo())) {
            long count = this.count(new LambdaQueryWrapper<Teacher>().eq(Teacher::getEmpNo, requestDTO.getEmpNo()));
            if (count > 0) {
                return Result.error(400, "工号已存在！");
            }
            teacher.setEmpNo(requestDTO.getEmpNo());
        }

        if (StringUtils.hasText(requestDTO.getName()))
            teacher.setName(requestDTO.getName());
        if (StringUtils.hasText(requestDTO.getPhone()))
            teacher.setPhone(requestDTO.getPhone());
        if (StringUtils.hasText(requestDTO.getDepartment()))
            teacher.setDepartment(requestDTO.getDepartment());
        if (requestDTO.getRole() != null)
            teacher.setRole(requestDTO.getRole());

        if (StringUtils.hasText(requestDTO.getPassword())) {
            String encryptedPass = DigestUtils.md5DigestAsHex(requestDTO.getPassword().getBytes());
            teacher.setPassword(encryptedPass);
        }

        teacher.setUpdatedAt(LocalDateTime.now());
        this.updateById(teacher);

        return Result.success("修改保存成功！", null);
    }

    /**
     * 禁用/启用
     */
    public Result<Object> changeStatus(Long id, Integer status) {
        Teacher teacher = this.getById(id);
        if (teacher == null)
            return Result.error(404, "教师不存在");

        teacher.setStatus(status);
        teacher.setUpdatedAt(LocalDateTime.now());
        this.updateById(teacher);

        return Result.success("状态切换成功！", null);
    }

    /**
     * 删除教师
     */
    public Result<Object> deleteTeacher(Long id) {
        // 校验外键依赖 (如排课表)
        long count = courseScheduleMapper
                .selectCount(new LambdaQueryWrapper<CourseSchedule>().eq(CourseSchedule::getTeacherId, id));
        if (count > 0) {
            return Result.error(500, "删除失败，该账号已存关联排课信息！");
        }

        this.removeById(id);
        return Result.success("删除成功！", null);
    }
}
