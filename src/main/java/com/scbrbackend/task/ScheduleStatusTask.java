package com.scbrbackend.task;

import com.scbrbackend.mapper.CourseScheduleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduleStatusTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduleStatusTask.class);

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void syncScheduleStatus() {
        // log.info("[定时任务] 开始同步排课状态...");

        // long startTime = System.currentTimeMillis();

        // 动作一：未开始 -> 进行中
        int inProgressCount = courseScheduleMapper.updateStatusToInProgress();
        // 动作二：进行中 -> 已结束
        int finishedCount = courseScheduleMapper.updateStatusToFinished();

        // long costTime = System.currentTimeMillis() - startTime;

        // log.info("[定时任务] 同步完成。上课 {} 节，下课 {} 节，耗时 {} ms",
        // inProgressCount, finishedCount, costTime);
    }
}
