package com.nutriai.ai;

import com.nutriai.dto.FoodRecognitionResult;
import com.nutriai.dto.NutritionGapDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AiService {

    RecommendationResponse analyzeAndRecommend(UserProfileRequest profile, String foodDiaryContext);

    FoodRecognitionResult recognizeFood(String description);

    /**
     * 「AI 教练」—— 根据今日已摄入/近7日缺口上下文生成即时文字建议。
     * 默认实现返回占位文案，避免破坏未实现该能力的服务。
     */
    default String generateCoachFeedback(UserProfileRequest profile, String context) {
        return "AI 教练暂不可用：当前 AI 服务未提供实时反馈能力，请先完善一些饮食记录后再试。";
    }

    /**
     * 「AI 教练」营养缺口解读 —— 依据结构性缺口数据给出 AI 建议。
     * 默认实现不做解读，仅原样返回。
     */
    default NutritionGapDto interpretGap(NutritionGapDto gap) {
        return gap;
    }

    /**
     * 「AI 教练」冰箱有料 —— 仅用现有食材 + 营养缺口生成推荐。
     * 默认实现退化为普通推荐。
     */
    default RecommendationResponse recommendFromFridge(UserProfileRequest profile, List<String> ingredients, String context) {
        return analyzeAndRecommend(profile, context);
    }

    default Flux<String> streamAndRecommend(UserProfileRequest profile) {
        return streamAndRecommend(profile, "");
    }

    default Flux<String> streamAndRecommend(UserProfileRequest profile, String foodDiaryContext) {
        return Flux.just(analyzeAndRecommend(profile, foodDiaryContext).toString());
    }

    /**
     * 通用对话兜底——给定 system prompt + 用户完整上下文，返回一句自然语言回复。
     * 真实实现（OpenAiCompatAiService）会 override 调大模型；默认退化为简单拼接。
     */
    default String chatWithContext(String systemPrompt, String userContent) {
        return "AI 兜底回复（离线模式）：" + userContent;
    }

    /**
     * 通用对话兜底·流式版——逐 token 返回 SSE 文本流。
     * 默认降级为一次性返回全文。
     */
    default Flux<String> chatStreamWithContext(String systemPrompt, String userContent) {
        return Flux.just(chatWithContext(systemPrompt, userContent));
    }

    String getProviderName();

    default boolean isAvailable() {
        return true;
    }
}