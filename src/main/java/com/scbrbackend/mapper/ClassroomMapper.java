package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scbrbackend.model.entity.Classroom;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClassroomMapper extends BaseMapper<Classroom> {
}
