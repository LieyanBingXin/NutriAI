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
            log.warn("未找到 provider={} 的 AI 服务，回退到 mock", provider);
            return registry.get("mock");
        }
        if (!service.isAvailable()) {
            log.warn("AI 服务 provider={} 未就绪，回退到 mock", provider);
            return registry.get("mock");
        }
        return service;
    }

    public AiService getService(String provider) {
        return registry.get(provider);
    }

    public List<String> listProviders() {
        return List.copyOf(registry.keySet());
    }
}