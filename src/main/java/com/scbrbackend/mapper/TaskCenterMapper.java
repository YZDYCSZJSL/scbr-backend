package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scbrbackend.model.dto.TaskCenterDetailDTO;
import com.scbrbackend.model.dto.TaskCenterPageQueryDTO;
import com.scbrbackend.model.dto.TaskCenterRecordDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskCenterMapper {
    IPage<TaskCenterRecordDTO> selectTaskPage(Page<?> page, @Param("query") TaskCenterPageQueryDTO query);
    
    TaskCenterDetailDTO selectTaskDetail(@Param("taskId") Long taskId, @Param("teacherId") Long teacherId);
}
