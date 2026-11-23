package com.nutriai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AssistantChatRequest {

    @Size(max = 2000, message = "消息过长")
    private String message;

    @Size(max = 30, message = "强调标签过多")
    private List<Map<String, String>> emphasizeTags;
    private List<Map<String, String>> history;
    private List<Map<String, Object>> suggestedRecipes;
}