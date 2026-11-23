package com.nutriai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 「AI 教练」实时反馈请求
 */
@Data
public class CoachFeedbackRequest {

    @Valid
    @NotNull(message = "用户信息不能为空")
    private UserProfileRequest profile;

    /** 用户刚吃的一道食物描述（可选，语音/手动补充） */
    private String latestMeal;

    /** 额外的说明（可选） */
    private List<String> notes;
}