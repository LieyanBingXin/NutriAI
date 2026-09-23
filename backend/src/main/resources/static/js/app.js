const API_BASE = '/api';

let currentUser = null;
let historyPage = 0;
const HISTORY_SIZE = 10;

document.addEventListener('DOMContentLoaded', () => {
    initAuthState();
    document.getElementById('profileForm').addEventListener('submit', handleSubmit);
    document.getElementById('loginForm').addEventListener('submit', handleLogin);
    document.getElementById('registerForm').addEventListener('submit', handleRegister);
    document.getElementById('saveProfileBtn').addEventListener('click', handleSaveProfile);
    document.getElementById('profileForm').addEventListener('reset', () => {
        setTimeout(() => {
            if (currentUser) loadProfile();
        }, 100);
    });
});

async function apiFetch(path, options = {}) {
    const res = await fetch(`${API_BASE}${path}`, {
        ...options,
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });

    if (res.status === 401) {
        return { ok: false, data: null, error: '未登录' };
    }

    const json = await res.json();
    return {
        ok: res.ok && json.code === 200,
        data: json.data,
        error: json.message || '请求失败'
    };
}

async function initAuthState() {
    const res = await apiFetch('/auth/me');
    if (res.ok && res.data) {
        currentUser = res.data;
        updateNav();
        loadProfile();
        loadHistory();
    } else {
        currentUser = null;
        updateNav();
    }
}

function updateNav() {
    const nav = document.getElementById('navActions');
    const saveBtn = document.getElementById('saveProfileBtn');
    const historySection = document.getElementById('historySection');

    if (currentUser) {
        nav.innerHTML = `
            <div class="nav-user">
                <span class="user-name">👤 ${currentUser.displayName || currentUser.username}</span>
                <button class="btn-icon" onclick="showHistory()">📚 历史</button>
                <button class="btn-icon" onclick="handleLogout()">登出</button>
            </div>
        `;
        saveBtn.style.display = 'inline-block';
        historySection.style.display = 'block';
    } else {
        nav.innerHTML = `
            <button class="btn-ghost" onclick="openAuthModal('login')">登录</button>
            <button class="btn-ghost btn-ghost-primary" onclick="openAuthModal('register')">注册</button>
        `;
        saveBtn.style.display = 'none';
        historySection.style.display = 'none';
    }
}

function openAuthModal(tab) {
    const modal = document.getElementById('authModal');
    modal.style.display = 'flex';
    switchAuthTab(tab);
}

function closeAuthModal() {
    document.getElementById('authModal').style.display = 'none';
    document.getElementById('loginError').textContent = '';
    document.getElementById('registerError').textContent = '';
}

function switchAuthTab(tab) {
    document.querySelectorAll('.modal-tab').forEach(t => {
        t.classList.toggle('active', t.dataset.tab === tab);
    });
    document.getElementById('loginForm').style.display = tab === 'login' ? 'flex' : 'none';
    document.getElementById('registerForm').style.display = tab === 'register' ? 'flex' : 'none';
    document.getElementById('loginError').textContent = '';
    document.getElementById('registerError').textContent = '';
}

async function handleLogin(e) {
    e.preventDefault();
    const form = e.target;
    const data = {
        username: form.username.value,
        password: form.password.value
    };

    const errEl = document.getElementById('loginError');
    errEl.textContent = '';

    const res = await apiFetch('/auth/login', {
        method: 'POST',
        body: JSON.stringify(data)
    });

    if (res.ok) {
        currentUser = res.data;
        closeAuthModal();
        updateNav();
        showToast('登录成功！', 'success');
        await loadProfile();
        await loadHistory();
    } else {
        errEl.textContent = res.error;
    }
}

async function handleRegister(e) {
    e.preventDefault();
    const form = e.target;
    const data = {
        username: form.username.value,
        password: form.password.value,
        email: form.email.value || null
    };

    const errEl = document.getElementById('registerError');
    errEl.textContent = '';

    const res = await apiFetch('/auth/register', {
        method: 'POST',
        body: JSON.stringify(data)
    });

    if (res.ok) {
        showToast('注册成功！请登录', 'success');
        switchAuthTab('login');
        document.querySelector('#loginForm [name="username"]').value = data.username;
        document.querySelector('#loginForm [name="password"]').focus();
    } else {
        errEl.textContent = res.error;
    }
}

async function handleLogout() {
    const res = await apiFetch('/auth/logout', { method: 'POST' });
    currentUser = null;
    updateNav();
    showToast('已登出', 'info');
}

function showHistory() {
    document.getElementById('historySection').scrollIntoView({ behavior: 'smooth' });
}

async function loadProfile() {
    const res = await apiFetch('/user/profile');
    if (res.ok && res.data) {
        fillFormFromProfile(res.data);
        showToast('已加载您的画像', 'info');
    }
}

function fillFormFromProfile(p) {
    const form = document.getElementById('profileForm');
    if (p.gender) form.gender.value = p.gender;
    if (p.age) form.age.value = p.age;
    if (p.heightCm) form.heightCm.value = p.heightCm;
    if (p.weightKg) form.weightKg.value = p.weightKg;
    if (p.activityLevel) form.activityLevel.value = p.activityLevel;
    if (p.healthGoal) form.healthGoal.value = p.healthGoal;
    if (p.tastePreference) form.tastePreference.value = p.tastePreference;
    if (p.foodPreferences && p.foodPreferences.length) {
        document.getElementById('foodPrefs').value = p.foodPreferences.join(', ');
    }
    if (p.foodAllergies && p.foodAllergies.length) {
        document.getElementById('foodAllergies').value = p.foodAllergies.join(', ');
    }
}

async function handleSaveProfile() {
    const form = document.getElementById('profileForm');
    const prefsRaw = document.getElementById('foodPrefs').value.trim();
    const allergiesRaw = document.getElementById('foodAllergies').value.trim();

    const dto = {
        gender: form.gender.value,
        age: parseInt(form.age.value),
        heightCm: parseFloat(form.heightCm.value),
        weightKg: parseFloat(form.weightKg.value),
        activityLevel: form.activityLevel.value,
        healthGoal: form.healthGoal.value,
        tastePreference: form.tastePreference.value,
        foodPreferences: prefsRaw ? prefsRaw.split(',').map(s => s.trim()).filter(Boolean) : [],
        foodAllergies: allergiesRaw ? allergiesRaw.split(',').map(s => s.trim()).filter(Boolean) : []
    };

    const res = await apiFetch('/user/profile', {
        method: 'PUT',
        body: JSON.stringify(dto)
    });

    if (res.ok) {
        showToast('画像已保存！', 'success');
    } else {
        showToast('保存失败: ' + res.error, 'error');
    }
}

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
        const res = await apiFetch('/recommend', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            throw new Error(res.error);
        }

        renderResult(res.data);

        if (currentUser) {
            await loadHistory();
        }
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

async function loadHistory(page = 0) {
    historyPage = page;
    const res = await apiFetch(`/recommend/history?page=${page}&size=${HISTORY_SIZE}`);
    if (!res.ok) return;
    renderHistory(res.data);
}

function renderHistory(data) {
    const list = document.getElementById('historyList');

    if (!data.content || data.content.length === 0) {
        list.innerHTML = '<div class="history-empty">📭 暂无历史记录<br><small>登录后您的每次推荐都会自动保存</small></div>';
        document.getElementById('historyPagination').innerHTML = '';
        return;
    }

    list.innerHTML = data.content.map(item => `
        <div class="history-item" onclick="viewHistoryDetail(${item.id})">
            <div class="history-info">
                <div class="history-summary">${item.summary || '(无概要)'}</div>
                <div class="history-meta">
                    <span>AI: ${item.aiProvider || '-'}</span>
                    <span>${formatDate(item.createdAt)}</span>
                </div>
            </div>
            <div class="history-actions" onclick="event.stopPropagation()">
                <button class="btn-delete" onclick="deleteHistory(${item.id})">删除</button>
            </div>
        </div>
    `).join('');

    renderPagination(data);
}

function renderPagination(data) {
    const pag = document.getElementById('historyPagination');
    if (data.totalPages <= 1) {
        pag.innerHTML = '';
        return;
    }

    let html = `<span>共 ${data.totalElements} 条</span>`;
    html += `<button class="page-btn" ${data.first ? 'disabled' : ''} onclick="loadHistory(${data.page - 1})">上一页</button>`;
    html += `<span>${data.page + 1} / ${data.totalPages}</span>`;
    html += `<button class="page-btn" ${data.last ? 'disabled' : ''} onclick="loadHistory(${data.page + 1})">下一页</button>`;
    pag.innerHTML = html;
}

async function viewHistoryDetail(id) {
    const res = await apiFetch(`/recommend/history/${id}`);
    if (!res.ok) {
        showToast('加载失败: ' + res.error, 'error');
        return;
    }

    const detail = res.data;
    if (detail.profile) {
        fillFormFromDetail(detail.profile);
    }
    renderResult(detail.recommendation);
    showToast(`历史记录 · ${formatDate(detail.createdAt)}`, 'info');
}

function fillFormFromDetail(p) {
    const form = document.getElementById('profileForm');
    if (p.gender) form.gender.value = p.gender;
    if (p.age) form.age.value = p.age;
    if (p.heightCm) form.heightCm.value = p.heightCm;
    if (p.weightKg) form.weightKg.value = p.weightKg;
    if (p.activityLevel) form.activityLevel.value = p.activityLevel;
    if (p.healthGoal) form.healthGoal.value = p.healthGoal;
    if (p.tastePreference) form.tastePreference.value = p.tastePreference;
    if (p.foodPreferences && p.foodPreferences.length) {
        document.getElementById('foodPrefs').value = p.foodPreferences.join(', ');
    }
    if (p.foodAllergies && p.foodAllergies.length) {
        document.getElementById('foodAllergies').value = p.foodAllergies.join(', ');
    }
}

async function deleteHistory(id) {
    if (!confirm('确定删除这条历史记录？')) return;
    const res = await apiFetch(`/recommend/history/${id}`, { method: 'DELETE' });
    if (res.ok) {
        showToast('已删除', 'success');
        loadHistory(historyPage);
    } else {
        showToast('删除失败: ' + res.error, 'error');
    }
}

function formatDate(dt) {
    if (!dt) return '';
    const d = new Date(dt);
    const pad = n => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

let toastTimer = null;
function showToast(msg, type = 'info') {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.className = `toast ${type}`;
    toast.style.display = 'block';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toast.style.display = 'none';
    }, 2500);
}