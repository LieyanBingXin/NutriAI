package com.nutriai.service;

import com.nutriai.entity.FridgeItem;
import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.FridgeRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冰箱有料 · 持久化记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final UserRepository userRepository;

    private static final java.util.regex.Pattern NOISE = java.util.regex.Pattern.compile(
            ".*(?:换一批|别管|不用管|不管|忽略|别用|不要用|存货|推荐|一批|能做什么|能做的|怎么|帮我|给我).*");

    private boolean isValidIngredient(String s) {
        if (s == null || s.isBlank()) return false;
        String t = s.trim();
        if (t.length() > 15) return false;
        return !NOISE.matcher(t).matches();
    }

    public List<String> list(Long userId) {
        return fridgeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FridgeItem::getIngredient)
                .filter(this::isValidIngredient)
                .toList();
    }

    /** 返回完整冰箱条目（含数量/单位/有效期），供前端展示 */
    public List<Map<String, Object>> listItems(Long userId) {
        return fridgeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> isValidIngredient(item.getIngredient()))
                .map(item -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ingredient", item.getIngredient());
                    m.put("amountGram", item.getAmountGram());
                    m.put("unit", item.getUnit());
                    m.put("expiryDate", item.getExpiryDate() != null ? item.getExpiryDate().toString() : null);
                    return m;
                }).toList();
    }

    @Transactional
    public int remember(Long userId, List<String> ingredients) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));
        int added = 0;
        for (String raw : ingredients) {
            String ingredient = raw.trim();
            if (ingredient.isBlank()) continue;
            if (!isValidIngredient(ingredient)) continue;
            if (!fridgeRepository.existsByUserIdAndIngredientIgnoreCase(userId, ingredient)) {
                FridgeItem item = new FridgeItem(user, ingredient);
                parseQuantity(ingredient, item);
                fridgeRepository.save(item);
                added++;
            }
        }
        return added;
    }

    /** 尝试从食材名中提取数量和单位，如 "200g鸡胸肉" → amount=200, unit=g */
    private void parseQuantity(String raw, FridgeItem item) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(g|克|kg|公斤|斤|个|只|盒|包|瓶|ml|毫升|升)").matcher(raw);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String u = m.group(2);
                if (u.equals("公斤") || u.equals("kg")) { val *= 1000; u = "g"; }
                else if (u.equals("斤")) { val *= 500; u = "g"; }
                item.setAmountGram((int) val);
                item.setUnit(u);
            } catch (NumberFormatException ignored) {}
        }
    }

    @Transactional
    public void forgetOne(Long userId, String ingredient) {
        fridgeRepository.deleteByUserIdAndIngredientIgnoreCase(userId, ingredient.trim());
    }

    @Transactional
    public void forgetAll(Long userId) {
        fridgeRepository.deleteByUserId(userId);
    }

    public String buildFridgeContext(Long userId) {
        List<String> items = list(userId);
        if (items.isEmpty()) return "";
        return "\n\n【用户冰箱里现有食材】\n" + String.join("、", items) + "\n生成推荐时尽量优先使用以上食材。";
    }
}