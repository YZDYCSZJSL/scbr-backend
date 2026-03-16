package com.scbrbackend.service;

import com.scbrbackend.common.Result.PageResult;
import com.scbrbackend.model.dto.LoginLogPageQueryDTO;
import com.scbrbackend.model.dto.OperationLogPageQueryDTO;
import com.scbrbackend.model.dto.TaskLogPageQueryDTO;
import com.scbrbackend.model.vo.LoginLogPageVO;
import com.scbrbackend.model.vo.OperationLogPageVO;
import com.scbrbackend.model.vo.TaskLogPageVO;
import com.scbrbackend.model.entity.SysLoginLog;
import com.scbrbackend.model.entity.SysOperationLog;

public interface LogService {
    PageResult<LoginLogPageVO> getLoginLogPage(LoginLogPageQueryDTO query);
    PageResult<OperationLogPageVO> getOperationLogPage(OperationLogPageQueryDTO query);
    PageResult<TaskLogPageVO> getTaskLogPage(TaskLogPageQueryDTO query);

    void recordLoginLog(SysLoginLog log);
    void recordOperationLog(SysOperationLog log);

    default void recordOperationLog(String empNo, String userName, String moduleName, String operationType, Long businessId, String operationDesc, Integer operationStatus) {
        SysOperationLog log = new SysOperationLog();
        log.setEmpNo(empNo);
        log.setUserName(userName);
        log.setModuleName(moduleName);
        log.setOperationType(operationType);
        log.setBusinessId(businessId);
        log.setOperationDesc(operationDesc);
        log.setOperationStatus(operationStatus);
        
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs = (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                jakarta.servlet.http.HttpServletRequest request = attrs.getRequest();
                if (request != null) {
                    log.setRequestMethod(request.getMethod());
                    log.setRequestUrl(request.getRequestURI());
                }
            }
        } catch (Exception e) {
            // Context not available, ignore
        }
        log.setCreatedAt(java.time.LocalDateTime.now());
        recordOperationLog(log);
    }
}
