package com.nutriai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 「AI 教练」冰箱有料请求 —— 用现有食材 + 营养缺口生成今晚可做的菜
 */
@Data
public class FridgeRequest {

    @NotNull(message = "用户信息不能为空")
    private UserProfileRequest profile;

    /** 家里现成的食材，如 ["鸡胸肉", "西兰花", "剩的半根胡萝卜"] */
    private List<String> ingredients;

    /** 额外说明（可选） */
    private String notes;
}