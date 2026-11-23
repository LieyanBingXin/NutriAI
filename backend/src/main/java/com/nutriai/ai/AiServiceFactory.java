package com.nutriai.ai;

import com.nutriai.config.NutriAiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务工厂 —— 根据配置动态选择 AiService 实现。
 * <p>
 * 新增 AI 服务商时，只需：
 * <ol>
 *   <li>实现 {@link AiService} 接口并加 {@code @Component}</li>
 *   <li>在 application.yml 中配置 {@code nutriai.ai.provider=你的provider名}</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceFactory {

    private final List<AiService> aiServices;
    private final NutriAiProperties properties;

    private final Map<String, AiService> registry = new HashMap<>();
    private volatile boolean fallbackLogged = false;

    @PostConstruct
    public void init() {
        for (AiService svc : aiServices) {
            registry.put(svc.getProviderName(), svc);
            log.info("注册 AI 服务: provider={}, class={}", svc.getProviderName(), svc.getClass().getSimpleName());
        }
    }

    public AiService getService() {
        String provider = properties.getAi().getProvider();
        AiService service = registry.get(provider);
        if (service == null) {
            logFallbackOnce("未找到 provider=" + provider + " 的 AI 服务");
            return registry.get("mock");
        }
        if (!service.isAvailable()) {
            logFallbackOnce("AI 服务 provider=" + provider + " 未配置 API Key");
            return registry.get("mock");
        }
        return service;
    }

    /** 回退到 Mock 只提示一次，避免每条消息刷 WARN 日志 */
    private void logFallbackOnce(String reason) {
        if (fallbackLogged) return;
        fallbackLogged = true;
        log.warn("{}，已切换到离线 Mock 模式（此提示仅出现一次）", reason);
    }

    public AiService getService(String provider) {
        return registry.get(provider);
    }

    public List<String> listProviders() {
        return List.copyOf(registry.keySet());
    }
}