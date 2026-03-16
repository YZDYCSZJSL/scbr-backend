package com.scbrbackend.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 排课状态定时同步任务
 *
 * 【V6.1 临时停用说明】
 * 此定时任务在旧模型中通过 start_time / end_time 判断排课是否已开始或已结束，
 * 并自动更新 course_schedule.status 字段。
 *
 * 在 V6.1 规则型排课模型下，course_schedule 不再保存具体的 start_time / end_time，
 * 因此旧的 SQL（WHERE status = 0 AND start_time <= NOW()）已不可用。
 *
 * 如需恢复自动状态更新，需要基于以下逻辑重新实现：
 * 1. 读取 sys_semester_config 获取学期开学日期
 * 2. 计算当前周次
 * 3. 结合 weekday、start_section / end_section、start_week / end_week、week_type
 *    判断排课是否已进入"进行中"或"已结束"状态
 *
 * 当前阶段暂时停用此定时任务，排课的 status 字段可由管理员手动管理，
 * 或在后续迭代中引入基于学期配置的新状态计算逻辑。
 */
@Component
public class ScheduleStatusTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduleStatusTask.class);

    /**
     * 原定时任务入口 —— 已临时停用
     * 保持 @Scheduled 注解以维持 Spring 上下文扫描一致性，但方法体内不执行任何操作。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void syncScheduleStatus() {
        // [V6.1 临时停用] 规则型排课模型下无 start_time / end_time，旧状态同步逻辑不适用。
        // 后续迭代可基于 sys_semester_config + sys_section_time 重新实现状态自动更新。
        //
        // 旧逻辑参考（已移除）：
        // int inProgressCount = courseScheduleMapper.updateStatusToInProgress();
        // int finishedCount = courseScheduleMapper.updateStatusToFinished();
    }
}
