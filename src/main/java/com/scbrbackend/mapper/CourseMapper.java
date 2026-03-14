package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scbrbackend.model.entity.Course;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
