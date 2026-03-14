package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scbrbackend.model.entity.Teacher;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教师 Mapper
 */
@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {
}
