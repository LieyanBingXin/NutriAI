const API_BASE = '/api';

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('profileForm');
    form.addEventListener('submit', handleSubmit);
});

async function handleSubmit(e) {
    e.preventDefault();

    const form = e.target;
    const formData = new FormData(form);

    const prefsRaw = document.getElementById('foodPrefs').value.trim();
    const allergiesRaw = document.getElementById('foodAllergies').value.trim();

    const payload = {
        gender: formData.get('gender'),
        age: parseInt(formData.get('age')),
        heightCm: parseFloat(formData.get('heightCm')),
        weightKg: parseFloat(formData.get('weightKg')),
        activityLevel: formData.get('activityLevel'),
        healthGoal: formData.get('healthGoal'),
        tastePreference: formData.get('tastePreference'),
        foodPreferences: prefsRaw ? prefsRaw.split(',').map(s => s.trim()).filter(Boolean) : [],
        foodAllergies: allergiesRaw ? allergiesRaw.split(',').map(s => s.trim()).filter(Boolean) : [],
        additionalNotes: []
    };

    showLoading(true);

    try {
        const res = await fetch(`${API_BASE}/recommend`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const json = await res.json();

        if (!res.ok || json.code !== 200) {
            throw new Error(json.message || '请求失败');
        }

        renderResult(json.data);
    } catch (err) {
        alert('生成推荐失败: ' + err.message);
        console.error(err);
    } finally {
        showLoading(false);
    }
}

function showLoading(show) {
    document.getElementById('loadingOverlay').style.display = show ? 'flex' : 'none';
}

function renderResult(data) {
    document.getElementById('resultSection').style.display = 'block';
    document.getElementById('aiBadge').textContent = 'AI: ' + (data.aiProvider || 'unknown');

    document.getElementById('summaryCard').textContent = data.summary || '暂无概要';

    const adv = data.nutritionalAdvice || {};
    document.getElementById('nutritionCard').innerHTML = `
        <h3>📊 每日营养建议</h3>
        <div class="nutrition-grid">
            <div class="nutri-item"><div class="nutri-value">${adv.dailyCalories || '—'}</div><div class="nutri-label">千卡/天</div></div>
            <div class="nutri-item"><div class="nutri-value">${adv.proteinGrams || '—'}g</div><div class="nutri-label">蛋白质</div></div>
            <div class="nutri-item"><div class="nutri-value">${adv.carbsGrams || '—'}g</div><div class="nutri-label">碳水</div></div>
            <div class="nutri-item"><div class="nutri-value">${adv.fatGrams || '—'}g</div><div class="nutri-label">脂肪</div></div>
            <div class="nutri-item"><div class="nutri-value">${adv.bmiCategory || '—'}</div><div class="nutri-label">BMI 分类</div></div>
        </div>
        <p style="margin-top:12px;color:#888;font-size:0.85rem;">${adv.metabolismNote || ''}</p>
    `;

    const recipes = data.recipes || [];
    document.getElementById('recipesContainer').innerHTML = `
        <h3>🍳 推荐食谱 (${recipes.length})</h3>
        ${recipes.map(renderRecipe).join('')}
    `;

    const tips = data.generalTips || [];
    document.getElementById('tipsCard').innerHTML = `
        <h3>💡 饮食建议</h3>
        <ul>${tips.map(t => `<li>${t}</li>`).join('')}</ul>
    `;

    document.getElementById('resultSection').scrollIntoView({ behavior: 'smooth' });
}

function renderRecipe(r) {
    const n = r.nutritionalInfo || {};
    return `
        <div class="recipe-card">
            <h3>${r.name}</h3>
            <p class="desc">${r.description || ''}</p>
            <div class="recipe-meta">
                <span>⏱ ${r.estimatedTime || '未知'}</span>
                <span>📈 难度: ${r.difficulty || '未知'}</span>
                ${n.calories ? `<span>🔥 ${n.calories} kcal</span>` : ''}
            </div>
            <div class="recipe-section">
                <h4>食材</h4>
                <ul>${(r.ingredients || []).map(i => `<li>${i}</li>`).join('')}</ul>
            </div>
            <div class="recipe-section">
                <h4>步骤</h4>
                <ol>${(r.steps || []).map(s => `<li>${s}</li>`).join('')}</ol>
            </div>
            ${(r.tags && r.tags.length) ? `<div class="tags">${r.tags.map(t => `<span class="tag">${t}</span>`).join('')}</div>` : ''}
        </div>
    `;
}