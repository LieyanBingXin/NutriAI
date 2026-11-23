package com.nutriai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class AssistantChatResponse {

    private String reply;

    private List<String> actions = new ArrayList<>();

    private Object data;

    private List<String> tips = new ArrayList<>();

    private boolean fromAi = false;

    private List<PendingAction> pendingActions = new ArrayList<>();

    @Data
    public static class PendingAction {
        private String id = UUID.randomUUID().toString().substring(0, 8);
        private String type;
        private String label;
        private String confirmText = "确认";
        private String endpoint;
        private String method = "POST";
        private Object payload;
        private String cardType;

        public static PendingAction create(String type, String label, String endpoint, Object payload, String cardType) {
            return create(type, label, endpoint, payload, cardType, "POST");
        }

        public static PendingAction create(String type, String label, String endpoint, Object payload, String cardType, String method) {
            PendingAction a = new PendingAction();
            a.type = type;
            a.label = label;
            a.endpoint = endpoint;
            a.payload = payload;
            a.cardType = cardType;
            a.method = method;
            if ("FOOD_DIARY".equals(type)) a.confirmText = "记入日记";
            else if ("WEIGHT_RECORD".equals(type)) a.confirmText = "确认记录";
            else if ("PERSONA_TAG".equals(type)) a.confirmText = "添加到 AI 设定";
            else if ("UNDO_DIARY".equals(type)) a.confirmText = "确认撤销";
            else if ("FRIDGE_CLEAR".equals(type)) a.confirmText = "确认清空";
            else if ("FRIDGE_REMOVE".equals(type)) a.confirmText = "确认移除";
            else if ("FRIDGE_ADD".equals(type)) a.confirmText = "记入冰箱";
            else if ("PREFERENCE".equals(type)) a.confirmText = "确认偏好";
            else if ("PROFILE_UPDATE".equals(type)) a.confirmText = "更新画像";
            return a;
        }
    }
}