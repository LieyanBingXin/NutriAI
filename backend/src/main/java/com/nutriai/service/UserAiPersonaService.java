package com.nutriai.service;

import com.nutriai.entity.UserAiPersona;
import com.nutriai.repository.UserAiPersonaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAiPersonaService {

    private final UserAiPersonaRepository repository;

    public List<String> listTags(Long userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream().map(UserAiPersona::getTagName).collect(Collectors.toList());
    }

    @Transactional
    public UserAiPersona addTag(Long userId, String tagName) {
        if (tagName == null || tagName.isBlank()) throw new IllegalArgumentException("标签不能为空");
        tagName = tagName.trim();
        if (tagName.length() > 60) tagName = tagName.substring(0, 60);
        if (repository.existsByUserIdAndTagName(userId, tagName)) {
            throw new IllegalArgumentException("标签「" + tagName + "」已存在");
        }
        UserAiPersona p = new UserAiPersona();
        p.setUserId(userId);
        p.setTagName(tagName);
        return repository.save(p);
    }

    @Transactional
    public void removeTag(Long userId, Long id) {
        repository.deleteByUserIdAndId(userId, id);
    }

    @Transactional
    public void removeTagByName(Long userId, String tagName) {
        repository.deleteByUserIdAndTagName(userId, tagName);
    }

    @Transactional
    public void clearAll(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    public String buildContext(Long userId) {
        List<String> tags = listTags(userId);
        if (tags.isEmpty()) return null;
        return "【AI 角色设定·请融入回复风格】" + String.join("、", tags) + "\n";
    }
}