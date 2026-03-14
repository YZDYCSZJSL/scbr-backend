package com.scbrbackend.controller;

import com.scbrbackend.common.Result.Result;
import com.scbrbackend.model.dto.ModelFailCallbackDTO;
import com.scbrbackend.model.dto.ModelFileCallbackDTO;
import com.scbrbackend.service.ModelCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/model/task/callback")
@RequiredArgsConstructor
public class InternalModelCallbackController {

    private final ModelCallbackService modelCallbackService;

    @PostMapping("/success")
    public Result<Void> handleSuccess(@RequestBody ModelFileCallbackDTO callbackDTO) {
        modelCallbackService.handleSuccess(callbackDTO);
        return Result.success("模型结果回调成功！", null);
    }

    @PostMapping("/fail")
    public Result<Void> handleFail(@RequestBody ModelFailCallbackDTO callbackDTO) {
        modelCallbackService.handleFail(callbackDTO);
        return Result.success("失败状态已回写！", null);
    }
}
