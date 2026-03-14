package com.scbrbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scbrbackend.model.entity.CourseSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CourseScheduleMapper extends BaseMapper<CourseSchedule> {

    @Select("<script>" +
            "SELECT COUNT(1) FROM course_schedule " +
            "WHERE classroom_id = #{classroomId} " +
            "AND status != 2 " +
            "AND start_time &lt; #{endTime} " +
            "AND end_time &gt; #{startTime} " +
            "<if test='excludeId != null'> AND id != #{excludeId} </if>" +
            "</script>")
    int checkClassroomConflict(@Param("classroomId") Long classroomId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") Long excludeId);

    @Select("<script>" +
            "SELECT COUNT(1) FROM course_schedule " +
            "WHERE teacher_id = #{teacherId} " +
            "AND status != 2 " +
            "AND start_time &lt; #{endTime} " +
            "AND end_time &gt; #{startTime} " +
            "<if test='excludeId != null'> AND id != #{excludeId} </if>" +
            "</script>")
    int checkTeacherConflict(@Param("teacherId") Long teacherId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") Long excludeId);

    @Update("UPDATE course_schedule SET status = 1 WHERE status = 0 AND start_time <= NOW()")
    int updateStatusToInProgress();

    @Update("UPDATE course_schedule SET status = 2 WHERE status = 1 AND end_time <= NOW()")
    int updateStatusToFinished();
}
