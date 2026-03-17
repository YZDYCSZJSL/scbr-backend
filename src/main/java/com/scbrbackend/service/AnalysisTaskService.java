package com.scbrbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scbrbackend.common.Result.Result;
import com.scbrbackend.common.exception.BusinessException;
import com.scbrbackend.common.utils.AliOssUtil;
import com.scbrbackend.mapper.AnalysisTaskMapper;
import com.scbrbackend.mapper.CourseScheduleMapper;
import com.scbrbackend.mapper.SysFileMapper;
import com.scbrbackend.mapper.TeacherMapper;
import com.scbrbackend.model.entity.AnalysisTask;
import com.scbrbackend.model.entity.SysFile;
import com.scbrbackend.model.entity.Teacher;
import com.scbrbackend.model.entity.CourseSchedule;
import com.scbrbackend.model.entity.AnalysisTaskLog;
import com.scbrbackend.model.dto.AnalysisTaskStatusDTO;
import com.scbrbackend.model.dto.CreateAnalysisTaskResponseDTO;
import com.scbrbackend.model.dto.ModelFileCallbackDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scbrbackend.mapper.AnalysisDetailMapper;
import com.scbrbackend.mapper.AnalysisTaskLogMapper;
import com.scbrbackend.model.dto.AnalysisTaskDetailDTO;
import com.scbrbackend.model.entity.AnalysisDetail;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AnalysisTaskService extends ServiceImpl<AnalysisTaskMapper, AnalysisTask> {

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private SysFileMapper sysFileMapper;

    @Autowired
    private CourseScheduleMapper courseScheduleMapper;

    @Autowired
    private TeacherMapper teacherMapper;

    @Autowired
    private ModelCallbackService modelCallbackService;

    @Autowired
    private AnalysisDetailMapper analysisDetailMapper;

    @Autowired
    private AnalysisTaskLogMapper analysisTaskLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LogService logService;

    @Value("${scbrbackend.server-address:http://127.0.0.1:8080}")
    private String serverAddress;

    @Value("${scbrbackend.model.token:}")
    private String modelToken;

    @Value("${scbrbackend.model.base-url}")
    private String modelBaseUrl;

    @Transactional
    public Result<CreateAnalysisTaskResponseDTO> createAnalysisTask(MultipartFile file, String fileName,
            Long scheduleId,
            Integer streamType, String token) {
        // 1. 获取教师身份信息
        com.scbrbackend.common.context.CurrentUser currentUser = com.scbrbackend.common.context.UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "未授权的访问！");
        }
        Teacher currentTeacher = teacherMapper.selectById(currentUser.getId());
        if (currentTeacher == null) {
            throw new BusinessException(401, "无效的用户令牌！");
        }

        // 2. 校验排课信息是否存在
        CourseSchedule schedule = courseScheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(400, "请选择要关联的排课信息！");
        }

        // 3. 内存流转，直接推送阿里云OSS，无盘化处理
        String objectName;
        String ossUrl;
        try {
            // 生成唯一文件名 UUID + 后缀
            String suffix = fileName.substring(fileName.lastIndexOf("."));
            objectName = UUID.randomUUID().toString() + suffix;

            // 直接调用工具类进行 OSS 上传
            ossUrl = aliOssUtil.upload(file.getBytes(), objectName);
        } catch (IOException e) {
            log.error("文件流读取失败", e);
            throw new BusinessException(500, "文件流读取失败");
        } catch (Exception e) {
            log.error("文件上传OSS失败", e);
            throw new BusinessException(500, "文件上传失败");
        }

        // 4. 数据库落库 (Database Persistence)
        // 4.1 存入 sys_file 表
        SysFile sysFile = new SysFile();
        sysFile.setOriginalName(fileName);
        sysFile.setFileKey(objectName);
        sysFile.setFileUrl(ossUrl);
        // 这里文件MD5略去（真实情况可计算）
        sysFile.setUploadBy(currentTeacher.getId());
        sysFile.setCreatedAt(LocalDateTime.now());
        sysFileMapper.insert(sysFile);

        // 4.2 存入 analysis_task 表
        AnalysisTask analysisTask = new AnalysisTask();
        analysisTask.setTeacherId(currentTeacher.getId());
        analysisTask.setClassroomId(schedule.getClassroomId());
        analysisTask.setScheduleId(scheduleId);
        analysisTask.setFileId(sysFile.getId());

        // 根据文件类型设置（1-图片, 2-视频, 3-实时流）
        int mediaType = 3;
        if (fileName != null && fileName.lastIndexOf(".") != -1) {
            String ext = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
            if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png") || ext.equals(".bmp")
                    || ext.equals(".webp")) {
                mediaType = 1;
            } else if (ext.equals(".mp4") || ext.equals(".avi") || ext.equals(".mov") || ext.equals(".wmv")
                    || ext.equals(".flv") || ext.equals(".mkv")) {
                mediaType = 2;
            }
        }
        analysisTask.setMediaType(mediaType);

        analysisTask.setStatus(0); // 0-排队中/待处理
        analysisTask.setRetryCount(0);
        analysisTask.setCreatedAt(LocalDateTime.now());
        analysisTask.setUpdatedAt(LocalDateTime.now());
        this.baseMapper.insert(analysisTask);

        // 记录 CREATED 日志
        AnalysisTaskLog createdLog = new AnalysisTaskLog();
        createdLog.setTaskId(analysisTask.getId());
        createdLog.setStage("CREATED");
        createdLog.setStatus(1);
        createdLog.setMessage("任务创建成功");
        Map<String, Object> createDetail = new HashMap<>();
        createDetail.put("mediaType", mediaType);
        createDetail.put("scheduleId", scheduleId);
        createdLog.setDetailJson(JSON.toJSONString(createDetail));
        createdLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(createdLog);

        // 如果有上传文件，记录 UPLOADED 日志
        if (sysFile.getId() != null) {
            AnalysisTaskLog uploadLog = new AnalysisTaskLog();
            uploadLog.setTaskId(analysisTask.getId());
            uploadLog.setStage("UPLOADED");
            uploadLog.setStatus(1);
            uploadLog.setMessage("文件上传并写入 sys_file 成功");
            Map<String, Object> upDetail = new HashMap<>();
            upDetail.put("fileId", sysFile.getId());
            upDetail.put("fileName", fileName);
            uploadLog.setDetailJson(JSON.toJSONString(upDetail));
            uploadLog.setCreatedAt(LocalDateTime.now());
            analysisTaskLogMapper.insert(uploadLog);
        }

        // 5. 立即用 @Async 开启异步 AI 推理调度 (主线程不阻塞)
        // 需要使用 spring 代理对象调用才能让 @Async 拦截器生效，真实项目中建议拆分服务，此处简写：
        // 更好的方式：把 executeAiAnalysis 抽到另一个
        // @Service，这里暂时自己暴露在当前类并调用（因为有@Async注解的方法被同类另一个方法调用，不生效需要代理，
        // 或者使用 AopContext.currentProxy()。为了简单先不要求绝对严格）
        // 实际上：为了确保生效，应该通过注入自身，或者直接新开一个线程，但这儿要求带 @Async 注解的 executeAiAnalysis。
        // 我们在 controller 里主动再调用一下异步方法也是个好主意，或者注入自己。
        CreateAnalysisTaskResponseDTO responseDTO = new CreateAnalysisTaskResponseDTO();
        responseDTO.setTaskId(analysisTask.getId());
        responseDTO.setStatus(0);
        
        logService.recordOperationLog(currentTeacher.getEmpNo(), currentTeacher.getName(), "课堂分析", "CREATE_ANALYSIS_TASK", analysisTask.getId(), "教师创建分析任务", 1);
        
        return Result.success("分析任务创建成功，AI 正在快马加鞭识别中！", responseDTO);
    }

    /**
     * 异步推理调度，模拟派发给远端 GPU 推理
     * 需在 Controller 或通过代理对象调用，本方法演示了签名和逻辑
     */
    @Async
    public void executeAiAnalysis(Long taskId) {
        log.info("开始执行异步 AI 分析任务，taskId = {}", taskId);

        AnalysisTask task = this.getById(taskId);
        if (task == null) {
            log.error("任务不存在，taskId = {}", taskId);
            return;
        }

        // 更新为分析中
        task.setStatus(1);
        task.setStartTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);

        AnalysisTaskLog runLog = new AnalysisTaskLog();
        runLog.setTaskId(taskId);
        runLog.setStage("RUNNING");
        runLog.setStatus(1);
        runLog.setMessage("任务开始进入分析中");
        Map<String, Object> runDetail = new HashMap<>();
        runDetail.put("startTime", task.getStartTime().toString());
        runLog.setDetailJson(JSON.toJSONString(runDetail));
        runLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(runLog);

        try {
            SysFile sysFile = sysFileMapper.selectById(task.getFileId());
            if (sysFile == null) {
                markTaskFailed(task, "关联文件不存在");
                return;
            }

            String ossUrl = sysFile.getFileUrl();
            log.info("准备将该记录的 OSS URL ({}) 派发给远端 GPU 算力平台进行拉流推理...", ossUrl);

            String modelApiUrl = modelBaseUrl + "/model/api/v1/tasks/file";

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (modelToken != null && !modelToken.trim().isEmpty()) {
                headers.set("x-model-token", modelToken.trim());
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("taskId", task.getId());
            requestBody.put("mediaType", task.getMediaType());
            requestBody.put("fileUrl", ossUrl);
            requestBody.put("fileName", sysFile.getOriginalName());
            requestBody.put("sampleIntervalSec", 2.0);
            requestBody.put("minPersistSamples", 2);

            // 只有视频异步任务才传 callback
            if (Integer.valueOf(2).equals(task.getMediaType())) {
                requestBody.put("callbackUrl", serverAddress + "/api/v1/internal/model/task/callback/success");
                requestBody.put("failCallbackUrl", serverAddress + "/api/v1/internal/model/task/callback/fail");

                log.info("实际发送给模型端的 callbackUrl: {}", requestBody.get("callbackUrl"));
                log.info("实际发送给模型端的 failCallbackUrl: {}", requestBody.get("failCallbackUrl"));
            }

            log.info("从配置中读取到的 modelToken: [{}]", modelToken);
            log.info("发起真实模型服务调用，请求地址: {}，请求参数: {}", modelApiUrl, JSON.toJSONString(requestBody));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(modelApiUrl, requestEntity, String.class);

            log.info("模型服务响应状态码: {}，响应体: {}", response.getStatusCode(), response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                markTaskFailed(task, "模型服务返回非 2xx 状态码");
                return;
            }

            // =========================
            // 图片：同步返回结果，直接落库
            // =========================
            if (Integer.valueOf(1).equals(task.getMediaType())) {
                handleSyncImageResponse(task, response.getBody());
                return;
            }

            // =========================
            // 视频：异步 accepted，等待 Python 回调
            // =========================
            if (Integer.valueOf(2).equals(task.getMediaType())) {
                AnalysisTaskLog accLog = new AnalysisTaskLog();
                accLog.setTaskId(taskId);
                accLog.setStage("MODEL_ACCEPTED");
                accLog.setStatus(1);
                accLog.setMessage("模型服务已受理识别请求");
                accLog.setCreatedAt(LocalDateTime.now());
                analysisTaskLogMapper.insert(accLog);

                AnalysisTaskLog waitLog = new AnalysisTaskLog();
                waitLog.setTaskId(taskId);
                waitLog.setStage("WAITING_CALLBACK");
                waitLog.setStatus(1);
                waitLog.setMessage("等待模型服务回调");
                waitLog.setCreatedAt(LocalDateTime.now());
                analysisTaskLogMapper.insert(waitLog);

                log.info("视频任务已被模型端受理，等待异步回调，taskId = {}", taskId);
                return;
            }

            log.warn("未知 mediaType = {}，taskId = {}", task.getMediaType(), taskId);

        } catch (Exception e) {
            log.error("执行异步 AI 分析任务异常，taskId = {}", taskId, e);
            markTaskFailed(task, "执行异步 AI 分析任务异常: " + e.getMessage());
        }
    }

    // @Async
    // public void executeAiAnalysis(Long taskId) {
    // log.info("开始执行异步 AI 分析任务，taskId = {}", taskId);

    // // 1. 提取任务记录
    // AnalysisTask task = this.getById(taskId);
    // if (task == null)
    // return;

    // // 更新状态为：1-分析中
    // task.setStatus(1);
    // task.setUpdatedAt(LocalDateTime.now());
    // this.updateById(task);

    // try {
    // // 2. 提取 OSS 链接
    // SysFile sysFile = sysFileMapper.selectById(task.getFileId());
    // if (sysFile != null) {
    // String ossUrl = sysFile.getFileUrl();
    // log.info("准备将该记录的 OSS URL ({}) 派发给远端 GPU 算力平台进行拉流推理...", ossUrl);

    // // --- 发送请求到 GPU 服务器进行推理 ---
    // String modelApiUrl =
    // "https://u377605-a06c-ee71895b.bjb1.seetacloud.com:8443/model/api/v1/tasks/file";

    // RestTemplate restTemplate = new RestTemplate();
    // HttpHeaders headers = new HttpHeaders();
    // headers.setContentType(MediaType.APPLICATION_JSON);

    // // 打印实际获取到的 token，方便排查 401 问题
    // log.info("从配置中读取到的 modelToken: [{}]", modelToken);
    // if (modelToken != null && !modelToken.isEmpty()) {
    // headers.set("x-model-token", modelToken);
    // } else {
    // log.warn("注意: 当前 modelToken 为空，可能会导致调用模型端由于缺少 x-model-token 而返回 401
    // Unauthorized!");
    // }

    // Map<String, Object> requestBody = new HashMap<>();
    // requestBody.put("taskId", task.getId());
    // requestBody.put("mediaType", task.getMediaType());
    // requestBody.put("fileUrl", ossUrl);
    // requestBody.put("fileName", sysFile.getOriginalName());
    // requestBody.put("sampleIntervalSec", 2.0);
    // requestBody.put("minPersistSamples", 2);

    // // 根据媒体类型判断是否需要传入回调地址：视频(mediaType=2)需要，图片(mediaType=1)直接同步返回
    // if (task.getMediaType() != null && task.getMediaType() == 2) {
    // String successCallback = serverAddress +
    // "/api/v1/internal/model/task/callback/success";
    // String failCallback = serverAddress +
    // "/api/v1/internal/model/task/callback/fail";
    // log.info("实际发送给模型端的 callbackUrl: {}", successCallback);
    // log.info("实际发送给模型端的 failCallbackUrl: {}", failCallback);
    // requestBody.put("callbackUrl", successCallback);
    // requestBody.put("failCallbackUrl", failCallback);
    // }

    // HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody,
    // headers);

    // log.info("发起真实模型服务调用，请求地址: {}，请求参数: {}", modelApiUrl,
    // JSON.toJSONString(requestBody));
    // ResponseEntity<String> response = restTemplate.postForEntity(modelApiUrl,
    // requestEntity, String.class);

    // if (response.getStatusCode().is2xxSuccessful()) {
    // log.info("调用 Python 模型接口成功，响应状态码: {}，响应体: {}", response.getStatusCode(),
    // response.getBody());

    // // 图片任务(mediaType=1)直接同步解析响应数据，并落库
    // if (task.getMediaType() != null && task.getMediaType() == 1) {
    // try {
    // com.alibaba.fastjson.JSONObject resObj =
    // JSON.parseObject(response.getBody());
    // if (resObj != null && resObj.getJSONObject("data") != null) {
    // ModelFileCallbackDTO dto =
    // resObj.getJSONObject("data").toJavaObject(ModelFileCallbackDTO.class);
    // if (dto.getTaskId() == null) {
    // dto.setTaskId(task.getId());
    // }
    // modelCallbackService.handleSuccess(dto);
    // log.info("图片任务 taskId={} 同步解析并落库成功", task.getId());
    // } else {
    // log.error("图片任务同步返回格式异常或缺少 data，已被标记为失败。响应体: {}", response.getBody());
    // task.setStatus(3);
    // this.updateById(task);
    // }
    // } catch (Exception e) {
    // log.error("解析图片同步返回内容异常，taskId = {}", task.getId(), e);
    // task.setStatus(3);
    // this.updateById(task);
    // }
    // }
    // } else {
    // log.error("调用 Python 模型接口失败，响应状态码: {}，响应体: {}", response.getStatusCode(),
    // response.getBody());
    // // 状态改为 3 失败
    // task.setStatus(3);
    // this.updateById(task);
    // }
    // } else {
    // log.error("由于文件不存在，无法发起模型调用，已将 taskId = {} 标记为失败", taskId);
    // task.setStatus(3);
    // this.updateById(task);
    // }
    // } catch (Exception e) {
    // log.error("执行异步 AI 分析任务异常，taskId = {}", taskId, e);
    // // 推理或HTTP请求异常，直接更新任务状态为失败
    // task.setStatus(3);
    // this.updateById(task);
    // }
    // }

    public AnalysisTaskStatusDTO getTaskStatus(Long taskId) {
        AnalysisTask task = this.getById(taskId);
        if (task == null) {
            return null;
        }
        AnalysisTaskStatusDTO dto = new AnalysisTaskStatusDTO();
        dto.setTaskId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setMediaType(task.getMediaType());
        dto.setAttendanceCount(task.getAttendanceCount());
        if (task.getTotalScore() != null) {
            dto.setTotalScore(task.getTotalScore().doubleValue());
        }
        if (task.getStatus() == 1) {
            dto.setProgress(65); // 模拟进度
        } else if (task.getStatus() == 2) {
            dto.setProgress(100);
        }
        return dto;
    }

    @Transactional
    public void stopAnalysisTask(Long taskId) {
        AnalysisTask task = this.getById(taskId);
        if (task != null && task.getStatus() != 2 && task.getStatus() != 3) {
            task.setStatus(2); // 标记为结束（成功）
            task.setFinishTime(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            this.updateById(task);

            AnalysisTaskLog stopLog = new AnalysisTaskLog();
            stopLog.setTaskId(taskId);
            stopLog.setStage("STOPPED");
            stopLog.setStatus(1);
            stopLog.setMessage("任务被主动停止");
            stopLog.setCreatedAt(LocalDateTime.now());
            analysisTaskLogMapper.insert(stopLog);
        }
    }

    public AnalysisTaskDetailDTO getTaskDetail(Long taskId) {
        AnalysisTask task = this.getById(taskId);
        if (task == null) {
            return null;
        }

        AnalysisTaskDetailDTO dto = new AnalysisTaskDetailDTO();
        dto.setTaskId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setMediaType(task.getMediaType());
        dto.setAttendanceCount(task.getAttendanceCount());
        if (task.getTotalScore() != null) {
            dto.setTotalScore(task.getTotalScore().doubleValue());
        }

        List<AnalysisDetail> detailList = analysisDetailMapper.selectList(
                new LambdaQueryWrapper<AnalysisDetail>()
                        .eq(AnalysisDetail::getTaskId, taskId)
                        .eq(AnalysisDetail::getRecordType, 0) // 只取文件流全量明细
                        .orderByAsc(AnalysisDetail::getFrameTime)
                        .orderByAsc(AnalysisDetail::getId));

        List<AnalysisTaskDetailDTO.DetailItem> resultDetails = new ArrayList<>();
        if (detailList != null && !detailList.isEmpty()) {
            for (AnalysisDetail detail : detailList) {
                AnalysisTaskDetailDTO.DetailItem item = new AnalysisTaskDetailDTO.DetailItem();
                item.setFrameTime(detail.getFrameTime());
                item.setBehaviorType(detail.getBehaviorType());

                List<List<Double>> boxes = new ArrayList<>();
                if (detail.getBoundingBoxes() != null && !detail.getBoundingBoxes().trim().isEmpty()) {
                    try {
                        boxes = objectMapper.readValue(
                                detail.getBoundingBoxes(),
                                new TypeReference<List<List<Double>>>() {
                                });
                    } catch (Exception e) {
                        log.warn("解析 boundingBoxes 失败, detailId={}, taskId={}", detail.getId(), taskId, e);
                    }
                }

                item.setBoundingBoxes(boxes);

                // 返回给前端前，优先以 boundingBoxes.size() 作为 count
                int rawCount = detail.getCount() == null ? 0 : detail.getCount();
                int finalCount = !boxes.isEmpty() ? boxes.size() : rawCount;
                item.setCount(finalCount);

                if (!boxes.isEmpty() && rawCount != boxes.size()) {
                    log.warn(
                            "返回前端时发现 count 与 boundingBoxes 数量不一致，已按 box 数量返回。detailId={}, taskId={}, rawCount={}, boxCount={}",
                            detail.getId(), taskId, rawCount, boxes.size());
                }

                resultDetails.add(item);
            }
        }

        dto.setDetails(resultDetails);
        return dto;
    }

    /**
     * 处理图片同步返回结果
     */
    private void handleSyncImageResponse(AnalysisTask task, String responseBody) {
        try {
            if (responseBody == null || responseBody.trim().isEmpty()) {
                markTaskFailed(task, "图片同步分析失败：模型端返回空响应体");
                return;
            }

            JSONObject root = JSON.parseObject(responseBody);
            Integer code = root.getInteger("code");
            String message = root.getString("message");

            if (code == null || code != 200) {
                markTaskFailed(task, "图片同步分析失败：" + message);
                return;
            }

            JSONObject data = root.getJSONObject("data");
            if (data == null) {
                markTaskFailed(task, "图片同步分析失败：响应 data 为空");
                return;
            }

            // 这里的 data 结构直接映射成你已有的 ModelFileCallbackDTO
            ModelFileCallbackDTO callbackDTO = data.toJavaObject(ModelFileCallbackDTO.class);

            if (callbackDTO == null) {
                markTaskFailed(task, "图片同步分析失败：无法解析模型返回结果");
                return;
            }

            // 防止模型端没回某些字段，这里兜底
            if (callbackDTO.getTaskId() == null) {
                callbackDTO.setTaskId(task.getId());
            }
            if (callbackDTO.getMediaType() == null) {
                callbackDTO.setMediaType(task.getMediaType());
            }
            if (callbackDTO.getDetails() == null) {
                callbackDTO.setDetails(Collections.emptyList());
            }

            // 直接复用你现有的视频回调成功落库逻辑
            modelCallbackService.handleSuccess(callbackDTO);

            log.info("图片任务同步结果已完成落库，taskId = {}", task.getId());

        } catch (Exception e) {
            log.error("处理图片同步返回结果异常，taskId = {}", task.getId(), e);
            markTaskFailed(task, "处理图片同步返回结果异常: " + e.getMessage());
        }
    }

    /**
     * 简化错误信息，转换为简短、可读的中文失败原因
     */
    public static String simplifyErrorMessage(Exception e) {
        if (e == null)
            return "未知系统异常";
        return simplifyErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
    }

    public static String simplifyErrorMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return "未知系统异常";
        }

        String lowerMsg = msg.toLowerCase();
        if (lowerMsg.contains("404 not found")) {
            return "模型服务接口不存在（404），请检查模型服务地址或接口路径是否正确";
        }
        if (lowerMsg.contains("500 internal server error")) {
            return "模型服务内部异常（500），请检查模型端日志";
        }
        if (lowerMsg.contains("read timed out") || lowerMsg.contains("connect timed out")
                || lowerMsg.contains("timed out")) {
            return "模型服务请求超时，请检查模型服务状态或网络连通性";
        }
        if (lowerMsg.contains("connection refused")) {
            return "无法连接模型服务，请确认模型服务是否已启动";
        }
        if (lowerMsg.contains("ssl") || lowerMsg.contains("certificate")) {
            return "模型服务 HTTPS/证书异常，请检查服务证书配置";
        }
        if (lowerMsg.contains("<html") || lowerMsg.contains("<!doctype")) {
            return "模型服务返回了错误页面，请检查请求地址是否正确";
        }

        // 其他情况：去掉 HTML 标签，压缩多余空白，截断
        String simplified = msg.replaceAll("<[^>]+>", " ");
        simplified = simplified.replaceAll("\\s+", " ").trim();
        if (simplified.length() > 120) {
            simplified = simplified.substring(0, 117) + "...";
        }
        return simplified;
    }

    /**
     * 统一标记任务失败
     */
    private void markTaskFailed(AnalysisTask task, String reason) {
        if (task == null) {
            return;
        }
        log.error("AI 分析任务失败，taskId = {}，原因 = {}", task.getId(), reason);
        task.setStatus(3);

        String simplifiedReason = simplifyErrorMessage(reason);
        task.setFailReason(simplifiedReason);
        task.setFinishTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        this.updateById(task);

        AnalysisTaskLog failLog = new AnalysisTaskLog();
        failLog.setTaskId(task.getId());
        failLog.setStage("FAILED");
        failLog.setStatus(0);
        failLog.setMessage(simplifiedReason);

        try {
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("originalError", reason);
            failLog.setDetailJson(objectMapper.writeValueAsString(detailMap));
        } catch (Exception e) {
            log.warn("序列化原始错误日志失败: {}", e.getMessage());
            // 降级容错
            failLog.setDetailJson("{\"originalError\":\"序列化失败\"}");
        }

        failLog.setCreatedAt(LocalDateTime.now());
        analysisTaskLogMapper.insert(failLog);
    }
}
