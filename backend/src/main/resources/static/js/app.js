const API = '/api';
let currentUser = null;
let guestMode = false;
let currentRecPayload = null;
let weightChart = null;
let weeklyCalorieChart = null;
let weeklyNutrientChart = null;
let toastTimer = null;
const recipeRenderCache = new Map();
const favoriteRecipeNames = new Set();
const diaryRenderCache = new Map();
const favoriteRenderCache = new Map();
const recipeBookRenderCache = new Map();

/* marked / Chart.js 均为 CDN 依赖，缺失时降级但不能让整个脚本挂掉 */
if (window.marked && typeof marked.setOptions === 'function') {
    marked.setOptions({ breaks: true, gfm: true });
}

/* ===== 通用工具 ===== */
/** 本地日期（yyyy-MM-dd），不能用 toISOString：那是 UTC，东八区 0-8 点会差一天 */
function localToday() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
}

function isDarkMode() {
    return document.documentElement.classList.contains('dark');
}

/** 最小化 HTML 清洗：移除脚本类标签、内联事件与 javascript: 协议 */
function sanitizeHtml(html) {
    const tpl = document.createElement('template');
    tpl.innerHTML = String(html == null ? '' : html);
    const forbidden = new Set(['SCRIPT', 'IFRAME', 'OBJECT', 'EMBED', 'LINK', 'META', 'BASE', 'FORM']);
    tpl.content.querySelectorAll('*').forEach((el) => {
        if (forbidden.has(el.tagName)) { el.remove(); return; }
        Array.from(el.attributes).forEach((attr) => {
            const name = attr.name.toLowerCase();
            if (name.startsWith('on') || name === 'srcdoc' || name === 'formaction') {
                el.removeAttribute(attr.name);
            } else if ((name === 'href' || name === 'src' || name === 'xlink:href')
                && /^\s*(javascript|data|vbscript):/i.test(attr.value)) {
                el.removeAttribute(attr.name);
            } else if (name === 'style') {
                el.removeAttribute('style');
            }
        });
    });
    return tpl.innerHTML;
}

/** Markdown 渲染（统一做 XSS 清洗）；marked 不可用时退回安全纯文本 */
function renderMarkdown(text) {
    const source = text == null ? '' : String(text);
    if (window.marked && typeof marked.parse === 'function') {
        try { return sanitizeHtml(marked.parse(source)); } catch (e) { /* fall through */ }
    }
    return escHtml(source).replace(/\r?\n/g, '<br>');
}

/** 统一请求：超时、401 处理、错误信息提取 */
async function apiFetch(url, options = {}) {
    const opts = Object.assign({ credentials: 'same-origin' }, options);
    let res;
    try {
        res = await fetch(url, opts);
    } catch (e) {
        throw new Error('网络连接失败，请检查网络后重试');
    }
    if (res.status === 401) {
        currentUser = null;
        throw new Error('请先登录');
    }
    return res;
}

async function readJson(res) {
    try { return await res.json(); }
    catch (e) { return { code: res.status, message: '服务器返回了无法解析的内容' }; }
}

/* ===== 2026 Editorial UI：页面框架 / 侧栏 / 移动导航 / PWA ===== */
const PAGE_TITLES = {
    assistant: 'AI 助手', board: '我的看板', diary: '食物日记', profile: '画像 & 推荐',
    weight: '体重追踪', history: '推荐历史', favorites: '收藏食谱', recipebook: '我的食谱本'
};

function updatePageChrome(name) {
    const title = document.getElementById('pageTitle');
    if (title && PAGE_TITLES[name]) title.textContent = PAGE_TITLES[name];
    const date = document.getElementById('pageDate');
    if (date) {
        try {
            date.textContent = new Intl.DateTimeFormat('zh-CN', {
                month: 'long', day: 'numeric', weekday: 'long'
            }).format(new Date());
        } catch (e) { date.textContent = localToday(); }
    }
}

function renderSideUser() {
    const box = document.getElementById('sideUser');
    if (!box) return;
    if (!currentUser) { box.innerHTML = ''; return; }
    const displayName = currentUser.displayName || currentUser.username || '我';
    const initial = String(displayName).trim().charAt(0).toUpperCase();
    box.innerHTML = `
        <div class="nav-user">
            <span class="nav-avatar">${escHtml(initial)}</span>
            <span class="user-name">${escHtml(displayName)}</span>
            <button class="btn-icon" title="修改昵称" aria-label="修改昵称" onclick="editNickname()">✏</button>
            <button class="btn-icon" title="退出登录" aria-label="退出登录" onclick="logout()">↩</button>
        </div>`;
}

function openMoreSheet() {
    if (!document.body.classList.contains('logged-in')) {
        openAuthModal('login');
        return;
    }
    const sheet = document.getElementById('moreSheet');
    if (!sheet) return;
    sheet.classList.add('open');
    sheet.setAttribute('aria-hidden', 'false');
    document.body.classList.add('sheet-open');
}
function closeMoreSheet() {
    const sheet = document.getElementById('moreSheet');
    if (!sheet) return;
    sheet.classList.remove('open');
    sheet.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('sheet-open');
}
function moreGo(name) {
    closeMoreSheet();
    switchMainTab(name);
}

function initPwa() {
    // 套壳 APK：隐藏「安装到主屏幕」入口与提示条（WebView 里无法安装 PWA，也没这个必要）
    if (/NutriAIApp/i.test(navigator.userAgent)) {
        const installBtn = document.querySelector('.more-foot .more-ghost[onclick*="installPwa"]');
        if (installBtn) installBtn.style.display = 'none';
        const bannerEl = document.getElementById('pwaBanner');
        if (bannerEl) bannerEl.hidden = true;
        return;
    }
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(() => { /* 离线增强失败不影响使用 */ });
    }
    const banner = document.getElementById('pwaBanner');
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone;
    const dismissed = localStorage.getItem('nutriai_pwa_dismissed') === '1';
    const mobile = window.matchMedia('(max-width: 760px)').matches;
    const showBanner = banner && !isStandalone && !dismissed && mobile;

    // 安装事件无条件注册：安卓上即使提示条已关闭，也能从「更多」抽屉直接安装
    window.addEventListener('beforeinstallprompt', (e) => {
        e.preventDefault();
        window.__deferredPwaPrompt = e;
        if (showBanner) setTimeout(() => { banner.hidden = false; }, 2600);
    });

    // iOS Safari 不支持 beforeinstallprompt，用提示代替
    const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
    if (isIos && showBanner) setTimeout(() => { banner.hidden = false; }, 4200);
}

async function installPwa() {
    const banner = document.getElementById('pwaBanner');
    const promptEvent = window.__deferredPwaPrompt;
    if (promptEvent) {
        promptEvent.prompt();
        try { await promptEvent.userChoice; } catch (e) { /* ignore */ }
        window.__deferredPwaPrompt = null;
        if (banner) banner.hidden = true;
        return;
    }
    showToast('请点浏览器菜单 →「添加到主屏幕」', 'info', 4200);
    if (banner) banner.hidden = true;
}

function dismissPwa() {
    const banner = document.getElementById('pwaBanner');
    if (banner) banner.hidden = true;
    localStorage.setItem('nutriai_pwa_dismissed', '1');
}

document.addEventListener('DOMContentLoaded', () => {
    initForms();
    initAuthState();
    initGlobalHandlers();
    initPwa();
    updatePageChrome('assistant');
});

function initForms() {
    document.getElementById('profileForm').addEventListener('submit', onProfileSubmit);
    document.getElementById('loginForm').addEventListener('submit', onLogin);
    document.getElementById('registerForm').addEventListener('submit', onRegister);
    document.getElementById('forgotForm').addEventListener('submit', onForgot);

    const today = localToday();
    document.getElementById('newWeightDate').value = today;
    document.getElementById('fdDate').value = today;
    document.getElementById('fdFilterDate').value = today;

    const resetBtn = document.querySelector('button[type="reset"]');
    if (resetBtn) {
        resetBtn.addEventListener('click', () => {
            document.getElementById('regionSelect').value = '';
        });
    }
}

function initAuthState() {
    fetch(`${API}/auth/me`, { credentials: 'same-origin' })
        .then(r => r.ok ? r.json() : null)
        .then(data => {
            if (data && data.data) {
                currentUser = data.data;
                showLoggedInUI();
                loadUserProfile();
                loadWeightData();
                loadFoodDiaryData();
                loadHistory(0);
            }
        })
        .catch(() => { /* 未登录或离线：保持游客界面 */ });
}

function showLoggedInUI() {
    guestMode = false;
    document.body.classList.remove('guest-mode');
    document.querySelectorAll('.app-tab').forEach(b => { b.style.display = ''; });
    const displayName = currentUser.displayName || currentUser.username;
    const initial = (displayName || 'U').trim().charAt(0).toUpperCase();
    const actions = document.getElementById('navActions');
    actions.innerHTML = `
        <div class="nav-user">
            <span class="nav-avatar">${escHtml(initial)}</span>
            <span class="user-name" id="userNameDisplay">${escHtml(displayName)}</span>
            <button class="btn-icon" title="修改昵称" aria-label="修改昵称" onclick="editNickname()">✏️</button>
            <button class="btn-icon" onclick="logout()">登出</button>
        </div>
    `;
    document.body.classList.add('logged-in');
    document.getElementById('saveProfileBtn').style.display = 'inline-block';
    document.getElementById('appTabs').style.display = 'flex';
    renderSideUser();
    switchMainTab('assistant');
    try {
        const deepTab = new URLSearchParams(location.search).get('tab');
        if (deepTab && PAGE_TITLES[deepTab]) switchMainTab(deepTab);
    } catch (e) { /* ignore */ }
    loadFoodPreferences();
    loadFavorites(0);
    if (typeof loadProactiveTip === 'function') loadProactiveTip();
}

/* 游客模式：无需登录，仅开放「画像 & 推荐」 */
function enterGuestMode() {
    guestMode = true;
    currentUser = null;
    document.body.classList.remove('logged-in');
    document.body.classList.add('guest-mode');

    // 只保留「画像 & 推荐」一个标签
    document.getElementById('appTabs').style.display = 'flex';
    document.querySelectorAll('.app-tab').forEach(b => {
        b.style.display = (b.dataset.target === 'profile') ? '' : 'none';
    });

    document.getElementById('navActions').innerHTML = `
        <span class="guest-badge">👤 游客模式</span>
        <button class="btn-ghost" onclick="openAuthModal('login')">登录</button>
        <button class="btn-ghost-primary" onclick="openAuthModal('register')">免费注册</button>
    `;

    const saveBtn = document.getElementById('saveProfileBtn');
    if (saveBtn) saveBtn.style.display = 'none';

    switchMainTab('profile');
}
/* 主视图切换（App Shell 标签导航） */
function switchMainTab(name) {
    document.querySelectorAll('.app-tab').forEach(b => {
        b.classList.toggle('active', b.dataset.target === name);
    });
    document.querySelectorAll('.app-view').forEach(v => {
        v.classList.toggle('active', v.dataset.view === name);
    });
    updatePageChrome(name);
    closeMoreSheet();
    const innerMap = {
        assistant: null,
        board: null,
        weight: 'weightSection',
        diary: 'foodDiarySection',
        history: 'historySection',
        favorites: 'favoritesSection'
    };
    if (innerMap[name]) document.getElementById(innerMap[name]).style.display = '';
    if (name === 'diary') {
        document.getElementById('coachPanel').style.display = '';
        loadCoachPanel();
    } else if (document.getElementById('coachPanel')) {
        document.getElementById('coachPanel').style.display = 'none';
    }
    if (name === 'assistant' || name === 'board' || name === 'favorites' || name === 'recipebook') {
        if (typeof refreshStickers === 'function') refreshStickers();
    }
    if (name === 'assistant') {
        // 不在切换 Tab 时自动聚焦：移动端会弹出键盘，桌面端会把页面滚离标题
        const inp = document.getElementById('chatInput');
        if (inp && document.activeElement === inp) inp.blur();
    }
    if (name === 'board') {
        if (typeof refreshBoard === 'function') refreshBoard();
    }
    if (name === 'weight') loadWeightData();
    else if (name === 'diary') loadFoodDiaryData();
    else if (name === 'history') loadHistory((typeof historyPage === 'number' ? historyPage : 0));
    else if (name === 'favorites') {
        document.getElementById('favoritesSection').style.display = '';
        if (typeof migrateLocalFavorites === 'function') {
            migrateLocalFavorites().finally(() => loadFavorites((typeof favPage === 'number' ? favPage : 0)));
        } else {
            loadFavorites((typeof favPage === 'number' ? favPage : 0));
        }
    }
    else if (name === 'recipebook') { document.getElementById('recipebookSection').style.display = ''; loadRecipeBook((typeof rcpPage === 'number' ? rcpPage : 0)); }
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
    document.documentElement.scrollTop = 0;
    if (document.body) document.body.scrollTop = 0;
    renderSectionNav(name);
}

/* ---------------- 页面内快速跳转导航 ---------------- */
const SECTION_NAV = {
    board: [
        { sel: '[data-board="profileSummary"]', label: '基础信息', ico: '🧬' },
        { sel: '[data-board="health"]', label: '身体状态', ico: '🩺' },
        { sel: '[data-board="likes"]', label: '喜欢', ico: '💚' },
        { sel: '[data-board="dislikes"]', label: '忌口', ico: '🚫' },
        { sel: '[data-board="fridge"]', label: '冰箱', ico: '🧊' },
        { sel: '[data-board="persona"]', label: '角色', ico: '🎭' },
        { sel: '[data-board="gap"]', label: 'AI 提醒', ico: '🤖' }
    ],
    profile: [
        { sel: '#profileForm .form-grid', label: '基本信息', ico: '🧬' },
        { sel: '#profileForm .form-section-title', label: '营养目标', ico: '🥗' },
        { sel: '#foodPrefs', label: '饮食偏好', ico: '🍚' }
    ],
    weight: [
        { sel: '#weightStats', label: '概览', ico: '📊' },
        { sel: '[data-view="weight"] .chart-container', label: '趋势', ico: '📈' },
        { sel: '#weightList', label: '记录', ico: '📋' }
    ],
    diary: [
        { sel: '#coachPanel', label: 'AI 教练', ico: '💬' },
        { sel: '#foodDiarySection', label: '食物日记', ico: '🍽️' }
    ]
};

let sectionNavTargets = [];

function renderSectionNav(view) {
    document.querySelectorAll('.section-nav-mobile').forEach(n => n.remove());
    sectionNavTargets = [];

    const cfg = SECTION_NAV[view];
    const viewEl = document.querySelector('.app-view[data-view="' + view + '"]');
    if (!cfg || !cfg.length || !viewEl) return;

    // 页面内横向跳转标签（仅移动端显示，桌面端不改动）
    const mnav = document.createElement('nav');
    mnav.className = 'section-nav-mobile';
    mnav.setAttribute('aria-label', '页面内导航');
    cfg.forEach(item => {
        const target = viewEl.querySelector(item.sel);
        if (!target) return;
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'section-nav-chip';
        btn.innerHTML = '<span class="sni-ico">' + item.ico + '</span>' + item.label;
        btn.addEventListener('click', () => scrollToSection(target));
        mnav.appendChild(btn);
        sectionNavTargets.push({ target, btn });
    });

    if (!mnav.childElementCount) return;
    viewEl.insertBefore(mnav, viewEl.firstChild);
    updateSectionNavActive();
}

function scrollToSection(target) {
    const isMobile = window.innerWidth <= 1024;
    const mnav = document.querySelector('.section-nav-mobile');
    let offset = 76; // 顶部导航栏高度 + 余量
    if (isMobile && mnav) offset += mnav.offsetHeight;
    const top = target.getBoundingClientRect().top + (window.scrollY || document.documentElement.scrollTop) - offset;
    window.scrollTo({ top: Math.max(top, 0), behavior: 'smooth' });
}

let sectionSpyTicking = false;
function updateSectionNavActive() {
    if (!sectionNavTargets.length) return;
    const line = 88; // 判定线：越过该线即视为当前区块
    let current = -1;
    sectionNavTargets.forEach((t, i) => {
        if (t.target.getBoundingClientRect().top <= line) current = i;
    });
    if (current === -1) current = 0; // 页面在顶部时默认高亮第一项
    // 滚到底部时强制高亮最后一项
    const doc = document.documentElement;
    if (window.innerHeight + (window.scrollY || doc.scrollTop) >= doc.scrollHeight - 4) {
        current = sectionNavTargets.length - 1;
    }
    sectionNavTargets.forEach((t, i) => t.btn.classList.toggle('active', i === current));
}

window.addEventListener('scroll', () => {
    if (sectionSpyTicking) return;
    sectionSpyTicking = true;
    requestAnimationFrame(() => {
        updateSectionNavActive();
        sectionSpyTicking = false;
    });
}, { passive: true });

function editNickname() {
    const name = (currentUser.displayName || currentUser.username);
    showPrompt('修改昵称', '请输入新的昵称', name, (newName) => {
        if (newName && newName.trim() && newName.trim() !== name) {
            updateNickname(newName.trim());
        }
    });
}

async function updateNickname(displayName) {
    try {
        const res = await fetch(`${API}/auth/me/display-name`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ displayName })
        });
        const data = await res.json();
        if (data.code === 200) {
            currentUser.displayName = data.data.displayName;
            const nameEl = document.getElementById('userNameDisplay');
            if (nameEl) nameEl.textContent = data.data.displayName;
            renderSideUser();
            showToast('昵称已更新', 'success');
        } else {
            showToast(data.message || '更新失败', 'error');
        }
    } catch (e) { showToast('网络错误', 'error'); }
}

async function logout() {
    await fetch(`${API}/auth/logout`, { method: 'POST' });
    currentUser = null;
    location.reload();
}

let currentHealthConditions = [];

async function loadUserProfile() {
    try {
        const res = await fetch(`${API}/user/profile`);
        if (!res.ok) return;
        const data = await res.json();
        if (!data.data) return;
        const p = data.data;
        const form = document.getElementById('profileForm');
        if (p.gender) form.gender.value = p.gender;
        if (p.age) form.age.value = p.age;
        if (p.heightCm) form.heightCm.value = p.heightCm;
        if (p.weightKg) form.weightKg.value = p.weightKg;
        if (p.activityLevel) form.activityLevel.value = p.activityLevel;
        if (p.healthGoal) form.healthGoal.value = p.healthGoal;
        if (p.tastePreferences && p.tastePreferences.length) {
            document.querySelectorAll('input[name="tastePreference"]').forEach(cb => {
                cb.checked = p.tastePreferences.includes(cb.value);
            });
        }
        if (p.region) document.getElementById('regionSelect').value = p.region;
        if (p.foodPreferences) document.getElementById('foodPrefs').value = p.foodPreferences.join(', ');
        if (p.foodAllergies) document.getElementById('foodAllergies').value = p.foodAllergies.join(', ');
        if (p.dailyCalorieGoal) document.getElementById('dailyCalorieGoal').value = p.dailyCalorieGoal;
        if (p.proteinGoal) document.getElementById('proteinGoal').value = p.proteinGoal;
        if (p.carbsGoal) document.getElementById('carbsGoal').value = p.carbsGoal;
        if (p.fatGoal) document.getElementById('fatGoal').value = p.fatGoal;
        currentHealthConditions = p.healthConditions || [];
    } catch (e) { showToast('加载画像失败', 'error'); }
}

async function loadRecommendedGoals() {
    const btn = document.getElementById('calcGoalsBtn');
    const status = document.getElementById('goalCalcStatus');
    btn.disabled = true; btn.textContent = '计算中...';
    status.style.display = 'none';
    try {
        let data;
        if (currentUser) {
            // 登录用户：先保存当前表单，再按服务端已存画像计算
            const payload = buildPayload();
            const saved = await apiFetch(`${API}/user/profile`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!saved.ok) {
                const err = await readJson(saved);
                throw new Error(err.message || '保存画像失败');
            }
            const res = await apiFetch(`${API}/user/profile/recommend-goals`);
            data = await readJson(res);
        } else {
            // 游客：直接按当前表单数据计算，无需登录
            const res = await apiFetch(`${API}/recommend/goals`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildPayload())
            });
            data = await readJson(res);
        }
        if (!data || data.code !== 200) throw new Error((data && data.message) || '计算失败');
        const g = data.data;
        document.getElementById('dailyCalorieGoal').value = g.recommendedCalories;
        document.getElementById('proteinGoal').value = g.recommendedProtein;
        document.getElementById('carbsGoal').value = g.recommendedCarbs;
        document.getElementById('fatGoal').value = g.recommendedFat;
        status.textContent = `基于 ${g.formula} 公式 · BMR ${g.bmr} kcal · TDEE ${g.tdee} kcal · BMI ${g.bmi} · ${(g.explanation || '').replace(/\*\*/g, '')}`;
        status.style.display = 'block';
        status.style.color = 'var(--accent)';
    } catch (e) {
        status.textContent = '计算失败：' + (e.message || '未知错误');
        status.style.display = 'block';
        status.style.color = 'var(--danger)';
    } finally {
        btn.disabled = false; btn.textContent = '计算推荐目标';
    }
}

function collectTastes() {
    return Array.from(document.querySelectorAll('input[name="tastePreference"]:checked')).map(cb => cb.value);
}

function buildPayload() {
    const form = document.getElementById('profileForm');
    const region = document.getElementById('regionSelect').value;
    const prefs = document.getElementById('foodPrefs').value.split(',').map(s => s.trim()).filter(Boolean);
    const allergies = document.getElementById('foodAllergies').value.split(',').map(s => s.trim()).filter(Boolean);
    const calGoal = document.getElementById('dailyCalorieGoal').value;
    const protGoal = document.getElementById('proteinGoal').value;
    const carbGoal = document.getElementById('carbsGoal').value;
    const fatGoal = document.getElementById('fatGoal').value;
    return {
        gender: form.gender.value,
        age: parseInt(form.age.value),
        heightCm: parseFloat(form.heightCm.value),
        weightKg: parseFloat(form.weightKg.value),
        activityLevel: form.activityLevel.value,
        healthGoal: form.healthGoal.value,
        tastePreferences: collectTastes(),
        region: region || null,
        foodPreferences: prefs,
        foodAllergies: allergies,
        dailyCalorieGoal: calGoal ? parseFloat(calGoal) : null,
        proteinGoal: protGoal ? parseFloat(protGoal) : null,
        carbsGoal: carbGoal ? parseFloat(carbGoal) : null,
        fatGoal: fatGoal ? parseFloat(fatGoal) : null,
        healthConditions: currentHealthConditions
    };
}

/* ===== AI 教练 ===== */
function coachProfile() {
    let p;
    try { p = buildPayload(); } catch (e) { p = {}; }
    return {
        gender: p.gender || 'MALE',
        age: (!p.age || isNaN(p.age)) ? 30 : p.age,
        heightCm: (!p.heightCm || isNaN(p.heightCm)) ? 170 : p.heightCm,
        weightKg: (!p.weightKg || isNaN(p.weightKg)) ? 65 : p.weightKg,
        activityLevel: p.activityLevel || 'MODERATE',
        healthGoal: p.healthGoal || 'MAINTAIN',
        tastePreferences: (p.tastePreferences && p.tastePreferences.length) ? p.tastePreferences : ['LIGHT'],
        region: p.region || null,
        foodPreferences: (p.foodPreferences && p.foodPreferences.length) ? p.foodPreferences : [],
        foodAllergies: (p.foodAllergies && p.foodAllergies.length) ? p.foodAllergies : []
    };
}

async function loadCoachPanel() {
    const gapBox = document.getElementById('coachGap');
    const fbBox = document.getElementById('coachFeedback');
    if (!gapBox && !fbBox) return;
    if (gapBox) gapBox.innerHTML = '<p class="muted">加载中…</p>';
    if (fbBox) fbBox.innerHTML = '<p class="muted">加载中…</p>';
    computeProfileCompletion();
    const profile = coachProfile();
    await Promise.all([
        loadCoachGap(profile),
        loadCoachFeedback(profile),
        loadGoalProgress()
    ]);
}

/* B：渐进式画像 · 完成度仪表 */
function computeProfileCompletion() {
    const pill = document.getElementById('profileCompletion');
    if (!pill) return;
    const form = document.getElementById('profileForm');
    if (!form) return;
    const region = document.getElementById('regionSelect');
    const allergies = document.getElementById('foodAllergies');
    const checks = [
        form.age && form.age.value,
        form.heightCm && form.heightCm.value,
        form.weightKg && form.weightKg.value,
        form.gender && form.gender.value,
        form.activityLevel && form.activityLevel.value,
        form.healthGoal && form.healthGoal.value,
        document.querySelectorAll('input[name="tastePreference"]:checked').length > 0,
        region && region.value,
        allergies && allergies.value
    ];
    const filled = checks.filter(Boolean).length;
    const pct = Math.round(filled / checks.length * 100);
    const cls = pct < 50 ? 'low' : (pct < 80 ? 'mid' : 'ok');
    pill.style.display = 'inline-flex';
    pill.className = 'completion-pill ' + cls;
    pill.textContent = '画像完成度 ' + pct + '%';
    pill.title = pct < 100 ? '去「AI 健康方案」补全资料，缺口分析与推荐会更精准' : '画像已完整，推荐已达最优';
}

/* F：语音记餐 */
function speechErrorText(error) {
    switch (error) {
        case 'not-allowed':
        case 'service-not-allowed':
            return '麦克风权限被拒绝，请在浏览器地址栏允许麦克风访问';
        case 'no-speech':
            return '没听清，请对着麦克风再说一次';
        case 'audio-capture':
            return '找不到可用的麦克风设备';
        case 'network':
            return '语音识别网络异常，请检查网络后重试';
        default:
            return '语音识别失败，请重试';
    }
}

function speakToDiary() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) { showToast('当前浏览器不支持语音识别，请用 Chrome / Edge', 'error'); return; }
    const rec = new SR();
    rec.lang = 'zh-CN';
    rec.interimResults = false;
    rec.maxAlternatives = 1;
    const btn = document.querySelector('.btn-mic');
    if (btn) btn.classList.add('listening');
    const finish = () => { if (btn) btn.classList.remove('listening'); };
    let timeoutId = setTimeout(() => {
        try { rec.stop(); } catch (e) {}
        showToast('聆听超时，请再试一次', 'warning');
    }, 12000);
    rec.onresult = (e) => {
        clearTimeout(timeoutId);
        const text = ((e.results[0] && e.results[0][0] && e.results[0][0].transcript) || '').trim();
        finish();
        if (!text) { showToast('没听清，请再试一次', 'warning'); return; }
        const input = document.getElementById('fdAiInput');
        if (input) input.value = text;
        showToast('已识别，正在分析营养…', 'success');
        recognizeFood();
    };
    rec.onend = () => { clearTimeout(timeoutId); finish(); };
    rec.onerror = (e) => {
        clearTimeout(timeoutId);
        finish();
        if (e.error === 'aborted') return;
        showToast(speechErrorText(e.error), 'error');
    };
    rec.start();
}

function gapBar(label, percent, target, avg, unit) {
    const pct = Math.round(percent || 0);
    const cls = pct < 85 ? 'low' : (pct > 115 ? 'high' : 'ok');
    return `<div class="gap-row">
        <div class="gap-label"><span>${label}</span><b>${pct}%</b></div>
        <div class="gap-track"><div class="gap-fill ${cls}" style="width:${Math.min(pct, 100)}%"></div></div>
        <div class="gap-meta">实际 ${avg || 0}${unit || ''} / 目标 ${target || '—'}${unit || ''}</div>
    </div>`;
}

async function loadCoachGap(profile) {
    const box = document.getElementById('coachGap');
    if (!box) return;
    try {
        const res = await fetch(`${API}/coach/gap`);
        const data = await res.json();
        if (data.code !== 200 || !data.data) {
            box.innerHTML = '<p class="muted">暂无数据，先去记几餐。</p>';
            return;
        }
        const g = data.data;
        let html = `<div class="gap-stats">
            <span>记录天数 <b>${g.trackedDays}</b></span>
        </div>`;
        html += gapBar('热量', g.calPercent, g.targetCalories, g.avgCalories, ' kcal');
        html += gapBar('蛋白质', g.proteinPercent, g.targetProtein, g.avgProtein, ' g');
        html += gapBar('碳水', g.carbsPercent, g.targetCarbs, g.avgCarbs, ' g');
        html += gapBar('脂肪', g.fatPercent, g.targetFat, g.avgFat, ' g');
        if (g.aiInsight) html += `<div class="gap-insight">🤖 ${escHtml(g.aiInsight)}</div>`;
        box.innerHTML = html;
    } catch (e) {
        box.innerHTML = '<p class="muted">加载缺口数据失败</p>';
    }
}

async function loadCoachFeedback(profile) {
    const box = document.getElementById('coachFeedback');
    if (!box) return;
    try {
        const res = await fetch(`${API}/coach/feedback`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ profile, latestMeal: null })
        });
        const data = await res.json();
        if (data.code !== 200 || !data.data) {
            box.innerHTML = '<p class="muted">先记录饮食，教练才能给出反馈。</p>';
            return;
        }
        const f = data.data;
        let pct = (f.today && f.today.caloriePercent) ? Math.round(f.today.caloriePercent) : 0;
        if (pct > 100) pct = 100;
        let html = `<div class="progress-line"><span>今日热量进度</span><b>${f.remainingSummary || ''}</b></div>`;
        html += `<div class="sum-bar"><div class="sum-fill" style="width:${pct}%"></div></div>`;
        html += `<div class="fb-message">🤖 ${escHtml(f.message || '')}</div>`;
        box.innerHTML = html;
    } catch (e) {
        box.innerHTML = '<p class="muted">加载教练反馈失败</p>';
    }
}

async function askCoach() {
    const input = document.getElementById('coachQuickMeal');
    const latest = (input && input.value.trim()) || '';
    const box = document.getElementById('coachFeedback');
    if (!box) return;
    box.innerHTML = '<p class="muted">教练正在思考…</p>';
    try {
        const res = await fetch(`${API}/coach/feedback`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ profile: coachProfile(), latestMeal: latest })
        });
        const data = await res.json();
        if (data.code !== 200 || !data.data) {
            box.innerHTML = '<p class="muted">教练暂时无法回答，请稍后再试。</p>';
            return;
        }
        const f = data.data;
        let pct = (f.today && f.today.caloriePercent) ? Math.round(f.today.caloriePercent) : 0;
        if (pct > 100) pct = 100;
        box.innerHTML = `<div class="progress-line"><span>今日热量进度</span><b>${f.remainingSummary || ''}</b></div>` +
            `<div class="sum-bar"><div class="sum-fill" style="width:${pct}%"></div></div>` +
            `<div class="fb-message">🤖 ${escHtml(f.message || '')}</div>`;
    } catch (e) {
        box.innerHTML = '<p class="muted">网络错误，请稍后再试。</p>';
    }
}

async function generateFridge() {
    const input = document.getElementById('coachFridgeInput');
    const text = (input && input.value.trim()) || '';
    const box = document.getElementById('coachFridgeResult');
    if (!box) return;
    if (!text) { box.innerHTML = '<p class="muted">先输入家里有哪些食材吧。</p>'; return; }
    const ingredients = text.split(/[,，、]/).map(s => s.trim()).filter(Boolean);
    box.innerHTML = '<p class="muted">AI 正在对着你的冰箱设计晚餐…</p>';
    try {
        const res = await fetch(`${API}/coach/fridge`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ profile: coachProfile(), ingredients, notes: '' })
        });
        const data = await res.json();
        if (data.code !== 200 || !data.data) {
            box.innerHTML = '<p class="muted">冰箱推荐失败，请稍后再试。</p>';
            return;
        }
        box.innerHTML = renderFridgeResult(data.data);
    } catch (e) {
        box.innerHTML = '<p class="muted">网络错误，请稍后再试。</p>';
    }
}

function renderFridgeResult(r) {
    let html = '';
    if (r.summary) html += `<div class="fridge-summary">🤖 ${escHtml(r.summary)}</div>`;
    if (r.recipes && r.recipes.length) {
        html += r.recipes.map(rc => `
            <div class="fridge-dish">
                <div class="fridge-dish-head">
                    <b>${escHtml(rc.name)}</b>
                    ${rc.estimatedTime ? `<span class="tag">${escHtml(rc.estimatedTime)}</span>` : ''}
                    ${rc.difficulty ? `<span class="tag">${escHtml(rc.difficulty)}</span>` : ''}
                </div>
                ${rc.description ? `<p class="fridge-why">💡 ${escHtml(rc.description)}</p>` : ''}
                ${rc.ingredients && rc.ingredients.length ? `<div class="fridge-ings">${rc.ingredients.map(i => `<span class="chip">${escHtml(i)}</span>`).join('')}</div>` : ''}
                ${rc.steps && rc.steps.length ? `<ol class="fridge-steps">${rc.steps.map(s => `<li>${escHtml(s)}</li>`).join('')}</ol>` : ''}
            </div>`).join('');
    }
    if (r.generalTips && r.generalTips.length) {
        html += `<div class="fridge-tips"><b>通用建议</b><ul>${r.generalTips.map(t => `<li>${escHtml(t)}</li>`).join('')}</ul></div>`;
    }
    return html || '<p class="muted">暂无推荐</p>';
}

function escHtml(s) {
    if (!s) return '';
    return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

async function onProfileSubmit(e) {
    e.preventDefault();
    const payload = buildPayload();
    const useStream = document.getElementById('streamToggle').checked;
    const btn = document.getElementById('submitBtn');
    btn.disabled = true;

    if (useStream) {
        await streamRecommend(payload);
    } else {
        await recommend(payload);
    }
    btn.disabled = false;
}

async function saveProfile() {
    if (!currentUser) return showToast('请先登录', 'error');
    const payload = buildPayload();
    try {
        const res = await fetch(`${API}/user/profile`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (data.code === 200) showToast('画像已保存 ✅', 'success');
        else showToast(data.message || '保存失败', 'error');
    } catch (e) { showToast('网络错误', 'error'); }
}

/* 统一事件委托：推荐卡片 / 日记 / 收藏 / 食谱本按钮 */
function initGlobalHandlers() {
    document.addEventListener('click', async (e) => {
        const target = e.target.closest('[data-action]');
        if (target) {
            const action = target.dataset.action;
            const idx = target.dataset.idx;
            if (action === 'save-profile') { saveProfile(); return; }
            if (action === 'pref-like' || action === 'pref-dislike') {
                const recipe = recipeRenderCache.get(idx);
                if (!recipe) return;
                const name = recipe.name || '食谱';
                const current = foodPreferences[name] || '';
                const wanted = action === 'pref-like' ? 'LIKE' : 'DISLIKE';
                const next = current === wanted ? 'NEUTRAL' : wanted;
                await toggleFoodPreference(name, 'recipe', next);
                return;
            }
            if (action === 'fav') {
                const recipe = recipeRenderCache.get(idx);
                if (recipe) await toggleFavorite(recipe);
                return;
            }
            if (action === 'recipebook') {
                const recipe = recipeRenderCache.get(idx);
                if (recipe) await saveToRecipeBook(recipe);
                return;
            }
            if (action === 'reco-tag') {
                const recipe = recipeRenderCache.get(idx);
                if (recipe && typeof addToPicks === 'function') addToPicks('custom', '🍳 食谱「' + (recipe.name || '') + '」');
                return;
            }
            if (action === 'edit-diary') {
                const record = diaryRenderCache.get(target.dataset.id);
                if (record) editFoodDiary(record);
                return;
            }
            if (action === 'delete-diary') { deleteFoodDiary(target.dataset.id); return; }
            if (action === 'weight-delete') { deleteWeight(target.dataset.id); return; }
            if (action === 'history-detail') { showHistoryDetail(target.dataset.id); return; }
            if (action === 'history-delete') { deleteHistory(target.dataset.id); return; }
            if (action === 'fav-view') {
                const fav = favoriteRenderCache.get(target.dataset.id);
                if (fav) viewFavoriteDetail(fav);
                return;
            }
            if (action === 'fav-delete') { deleteFavorite(target.dataset.id); return; }
            if (action === 'fav-tag') {
                const f = favoriteRenderCache.get(target.dataset.id);
                if (f && typeof addToPicks === 'function') addToPicks('custom', '🍳 食谱「' + (f.recipeName || '') + '」');
                return;
            }
            if (action === 'recipe-use') { useRecipe(target.dataset.id); return; }
            if (action === 'recipe-delete') { deleteRecipe(target.dataset.id); return; }
            if (action === 'recipe-tag') {
                const r = recipeBookRenderCache.get(target.dataset.id);
                if (r && typeof addToPicks === 'function') addToPicks('custom', '🍳 食谱「' + (r.name || '') + '」');
                return;
            }
            if (action === 'diary-tag') {
                const r = diaryRenderCache.get(target.dataset.id);
                if (r && typeof addToPicks === 'function') addToPicks('custom', '🥗 我今天吃了「' + (r.foodName || '') + '」');
                return;
            }
        }
        if (e.target.id === 'saveProfileBtn') saveProfile();
    });

    // 键盘可操作：history-item 等 role=button 的元素支持 Enter / Space
    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        const target = e.target.closest('[data-action][role="button"]');
        if (!target) return;
        // 输入框内的空格不应触发
        if (/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName)) return;
        e.preventDefault();
        target.click();
    });

    // Esc 关闭弹窗
    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        closeDetailModal();
        closeConfirmModal();
        closePromptModal();
        closeAuthModal();
        closeForgotModal();
        closeMoreSheet();
    });
}

async function recommend(payload) {
    showLoading(true);
    hideStreamSection();
    hideResultSection();
    try {
        const usePrefs = document.getElementById('prefToggle').checked;
        const url = `${API}/recommend?usePreferences=${usePrefs}`;
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        showLoading(false);
        if (!res.ok) { const t = await res.json(); showToast(t.message || '生成失败', 'error'); return; }
        const data = await res.json();
        if (data.code === 200) renderResult(data.data);
        else showToast(data.message || '生成失败', 'error');
    } catch (e) { showLoading(false); showToast('网络错误', 'error'); }
}

async function streamRecommend(payload) {
    showLoading(false);
    showStreamSection();
    hideResultSection();
    currentRecPayload = { ...payload };
    document.getElementById('btnStructView').style.display = 'none';
    const content = document.getElementById('streamContent');
    const status = document.getElementById('streamStatus');
    content.innerHTML = '';
    content.classList.remove('done');
    status.textContent = '连接中...';
    status.className = 'stream-status connecting';

    try {
        const usePrefs = document.getElementById('prefToggle').checked;
        const url = `${API}/recommend/stream?usePreferences=${usePrefs}`;
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            status.textContent = '连接失败';
            status.className = 'stream-status error';
            const t = await res.json().catch(() => ({}));
            showToast(t.message || '生成失败', 'error');
            return;
        }
        status.textContent = 'AI 正在回复...';
        status.className = 'stream-status connecting';

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let rawBuffer = '';
        let consecutiveEmpty = 0;

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            const chunk = decoder.decode(value, { stream: true });
            rawBuffer += chunk;

            let sepIdx;
            while ((sepIdx = rawBuffer.indexOf('\n\n')) >= 0) {
                const event = rawBuffer.substring(0, sepIdx);
                rawBuffer = rawBuffer.substring(sepIdx + 2);

                const lines = event.split('\n');
                let eventData = null;
                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        const text = line.substring(5);
                        if (text === '[DONE]') {
                            status.textContent = '完成';
                            status.className = 'stream-status done';
                            content.classList.add('done');
                            document.getElementById('btnStructView').style.display = '';
                            return;
                        }
                        if (eventData === null) eventData = '';
                        eventData += text;
                    }
                }

                if (eventData === null) continue;

                if (eventData === '') {
                    consecutiveEmpty++;
                    if (consecutiveEmpty >= 2) {
                        buffer += '\n\n';
                        consecutiveEmpty = 0;
                    } else {
                        buffer += '\n';
                    }
                } else {
                    consecutiveEmpty = 0;
                    buffer += eventData;
                }
                content.innerHTML = marked.parse(buffer);
                content.parentElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }
        }
        if (buffer.length > 0) {
            content.innerHTML = marked.parse(buffer);
        }
        status.textContent = '完成';
        status.className = 'stream-status done';
        content.classList.add('done');
        document.getElementById('btnStructView').style.display = '';
    } catch (e) {
        status.textContent = '出错了';
        status.className = 'stream-status error';
        content.classList.add('done');
        content.textContent += '\n\n连接中断: ' + e.message;
    }
}

function showStreamSection() { document.getElementById('streamSection').style.display = ''; }
function hideStreamSection() { document.getElementById('streamSection').style.display = 'none'; }
function showResultSection() { document.getElementById('resultSection').style.display = ''; }
function hideResultSection() { document.getElementById('resultSection').style.display = 'none'; }

async function loadStructuredResult() {
    if (!currentRecPayload) return;
    const btn = document.getElementById('btnStructView');
    btn.disabled = true;
    btn.textContent = '加载中...';
    showLoading(true);
    try {
        const usePrefs = document.getElementById('prefToggle').checked;
        const url = `${API}/recommend?usePreferences=${usePrefs}`;
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(currentRecPayload)
        });
        const data = await res.json();
        if (data.code === 200) {
            renderResult(data.data);
            document.getElementById('resultSection').scrollIntoView({ behavior: 'smooth' });
        } else {
            showToast(data.message || '获取结构化结果失败', 'error');
        }
    } catch (e) { showToast('网络错误', 'error'); }
    finally { showLoading(false); btn.disabled = false; btn.textContent = '查看结构化食谱'; }
}

function renderResult(r) {
    showResultSection();
    hideStreamSection();
    document.getElementById('aiBadge').textContent = r.aiProvider || 'AI';

    // 概要：纯文本写入，避免 AI 输出中的 HTML 被执行
    const sc = document.getElementById('summaryCard');
    sc.textContent = '';
    const summaryStrong = document.createElement('strong');
    summaryStrong.textContent = r.summary || '';
    sc.appendChild(summaryStrong);

    const nc = document.getElementById('nutritionCard');
    if (r.nutritionalAdvice) {
        const na = r.nutritionalAdvice;
        nc.innerHTML = `
            <h4>每日营养参考</h4>
            <div class="nutrition-grid">
                <div class="nutri-item"><div class="nutri-value">${escHtml(na.dailyCalories || 0)}</div><div class="nutri-label">热量 kcal</div></div>
                <div class="nutri-item"><div class="nutri-value">${escHtml(na.proteinGrams || 0)}g</div><div class="nutri-label">蛋白质</div></div>
                <div class="nutri-item"><div class="nutri-value">${escHtml(na.carbsGrams || 0)}g</div><div class="nutri-label">碳水</div></div>
                <div class="nutri-item"><div class="nutri-value">${escHtml(na.fatGrams || 0)}g</div><div class="nutri-label">脂肪</div></div>
            </div>
            ${na.bmiCategory ? `<div class="nutrition-note">BMI 分类: ${escHtml(na.bmiCategory)}${na.metabolismNote ? ' · ' + escHtml(na.metabolismNote) : ''}</div>` : ''}`;
    } else {
        nc.innerHTML = '';
    }

    const rc = document.getElementById('recipesContainer');
    recipeRenderCache.clear();
    const recipes = Array.isArray(r.recipes) ? r.recipes : [];
    rc.innerHTML = recipes.length > 0 ? `<h3>推荐食谱</h3>` + recipes.map((recipe, idx) => {
        recipeRenderCache.set(String(idx), recipe);
        const name = recipe.name || '食谱';
        const pref = foodPreferences[name] || '';
        const fav = favoriteRecipeNames.has(name);
        return `
        <div class="recipe-card" data-recipe-name="${escHtml(name)}">
            <h3>${escHtml(name)}</h3>
            ${recipe.description ? `<p class="desc">${escHtml(recipe.description)}</p>` : ''}
            <div class="recipe-meta">
                ${recipe.nutritionalInfo && recipe.nutritionalInfo.calories ? `<span>${escHtml(recipe.nutritionalInfo.calories)} kcal</span>` : ''}
                ${recipe.estimatedTime ? `<span>⏱ ${escHtml(recipe.estimatedTime)}</span>` : ''}
                ${recipe.difficulty ? `<span>${escHtml(recipe.difficulty)}</span>` : ''}
            </div>
            ${recipe.ingredients && recipe.ingredients.length ? `
                <div class="recipe-section"><h4>食材</h4><ul>${recipe.ingredients.map(i => `<li>${escHtml(i)}</li>`).join('')}</ul></div>` : ''}
            ${recipe.steps && recipe.steps.length ? `
                <div class="recipe-section"><h4>做法</h4><ol>${recipe.steps.map(s => `<li>${escHtml(s)}</li>`).join('')}</ol></div>` : ''}
            ${recipe.tags && recipe.tags.length ? `<div class="tags">${recipe.tags.map(t => `<span class="tag">${escHtml(t)}</span>`).join('')}</div>` : ''}
            <div class="recipe-actions">
                <button type="button" class="btn-pref like ${pref === 'LIKE' ? 'active' : ''}" data-action="pref-like" data-idx="${idx}">${pref === 'LIKE' ? '已喜欢' : '喜欢'}</button>
                <button type="button" class="btn-pref dislike ${pref === 'DISLIKE' ? 'active' : ''}" data-action="pref-dislike" data-idx="${idx}">${pref === 'DISLIKE' ? '已避开' : '避开'}</button>
                <button type="button" class="btn-fav ${fav ? 'active' : ''}" data-action="fav" data-idx="${idx}">${fav ? '已收藏' : '收藏'}</button>
                <button type="button" class="btn-recipebook" data-action="recipebook" data-idx="${idx}">📖 加入食谱本</button>
                <button type="button" class="btn-tag" data-action="reco-tag" data-idx="${idx}">🔖 便利贴</button>
            </div>
        </div>`;
    }).join('') : '';

    const tc = document.getElementById('tipsCard');
    tc.innerHTML = r.generalTips && r.generalTips.length > 0
        ? `<h4>饮食建议</h4><ul>${r.generalTips.map(t => `<li>${escHtml(t)}</li>`).join('')}</ul>`
        : '';

    refreshFavoriteStates();
}

/** 按名称查询收藏状态，保证“已收藏”按钮初始态正确 */
async function refreshFavoriteStates() {
    if (!currentUser) return;
    const entries = Array.from(recipeRenderCache.entries());
    await Promise.all(entries.map(async ([idx, recipe]) => {
        const name = recipe.name;
        if (!name) return;
        try {
            const res = await apiFetch(`${API}/favorite-recipes/check?recipeName=${encodeURIComponent(name)}`);
            const data = await readJson(res);
            if (data && data.data) favoriteRecipeNames.add(name);
            else favoriteRecipeNames.delete(name);
            document.querySelectorAll(`.btn-fav[data-idx="${idx}"]`).forEach(btn => {
                btn.classList.toggle('active', favoriteRecipeNames.has(name));
                btn.textContent = favoriteRecipeNames.has(name) ? '已收藏' : '收藏';
            });
        } catch (e) { /* 收藏状态非关键 */ }
    }));
}

function showLoading(show) {
    document.getElementById('loadingOverlay').style.display = show ? 'flex' : 'none';
}

function showToast(msg, type = 'info', duration = 3200) {
    const t = document.getElementById('toast');
    if (!t) return;
    t.textContent = msg;
    t.className = `toast ${type}`;
    t.style.display = 'block';
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        t.style.display = 'none';
        toastTimer = null;
    }, duration);
}

function openAuthModal(tab) {
    document.getElementById('authModal').style.display = 'flex';
    switchAuthTab(tab);
    const content = document.getElementById('authModal').querySelector('.modal-content');
    content.style.animation = 'none';
    requestAnimationFrame(() => { content.style.animation = ''; });
}
function closeAuthModal() { document.getElementById('authModal').style.display = 'none'; }

function openDetailModal(title, html) {
    const modal = document.getElementById('detailModal');
    if (!modal) return;
    document.getElementById('detailModalTitle').textContent = title || '';
    document.getElementById('detailModalBody').innerHTML = html || '';
    modal.style.display = 'flex';
    const closeBtn = modal.querySelector('.modal-close');
    if (closeBtn) closeBtn.focus();
}
function closeDetailModal() {
    const modal = document.getElementById('detailModal');
    if (modal) modal.style.display = 'none';
}

let confirmCallback = null;
function showConfirm(title, message, onConfirm) {
    const modal = document.getElementById('confirmModal');
    if (!modal) return;
    document.getElementById('confirmModalTitle').textContent = title || '确认操作';
    document.getElementById('confirmModalBody').textContent = message || '';
    confirmCallback = onConfirm || null;
    modal.style.display = 'flex';
    const ok = document.getElementById('confirmModalOk');
    if (ok) ok.focus();
}
function closeConfirmModal() {
    const modal = document.getElementById('confirmModal');
    if (modal) modal.style.display = 'none';
    confirmCallback = null;
}
function confirmOk() {
    const cb = confirmCallback;
    closeConfirmModal();
    if (typeof cb === 'function') cb();
}

let promptCallback = null;
function showPrompt(title, placeholder, defaultValue, onConfirm) {
    const modal = document.getElementById('promptModal');
    if (!modal) return;
    document.getElementById('promptModalTitle').textContent = title || '请输入';
    const input = document.getElementById('promptModalInput');
    input.value = defaultValue || '';
    input.placeholder = placeholder || '';
    promptCallback = onConfirm || null;
    modal.style.display = 'flex';
    setTimeout(() => { input.focus(); input.select(); }, 0);
}
function closePromptModal() {
    const modal = document.getElementById('promptModal');
    if (modal) modal.style.display = 'none';
    promptCallback = null;
}
function promptOk() {
    const input = document.getElementById('promptModalInput');
    const val = input ? input.value : '';
    const cb = promptCallback;
    closePromptModal();
    if (typeof cb === 'function') cb(val);
}
function switchAuthTab(tab) {
    document.querySelectorAll('.modal-tab').forEach(t => t.classList.toggle('active', t.dataset.tab === tab));
    document.getElementById('loginForm').style.display = tab === 'login' ? 'flex' : 'none';
    document.getElementById('registerForm').style.display = tab === 'register' ? 'flex' : 'none';
}

async function onLogin(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const errBox = document.getElementById('loginError');
    errBox.textContent = '';
    const btn = e.target.querySelector('button[type="submit"]');
    if (btn) { btn.disabled = true; btn.textContent = '登录中…'; }
    try {
        const res = await fetch(`${API}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ username: String(fd.get('username') || '').trim(), password: fd.get('password') })
        });
        const data = await readJson(res);
        if (data.code === 200) {
            currentUser = data.data;
            closeAuthModal();
            e.target.reset();
            showToast(`欢迎回来, ${currentUser.displayName || currentUser.username}!`, 'success');
            showLoggedInUI();
            loadUserProfile(); loadWeightData(); loadFoodDiaryData(); loadHistory(0);
            if (typeof syncRailFromServer === 'function') syncRailFromServer();
        } else {
            errBox.textContent = data.message || '登录失败';
        }
    } catch (err) {
        errBox.textContent = err.message || '网络错误';
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = '登录'; }
    }
}

async function onRegister(e) {
    e.preventDefault();
    const fd = new FormData(e.target);
    const errBox = document.getElementById('registerError');
    errBox.textContent = '';
    const btn = e.target.querySelector('button[type="submit"]');
    if (btn) { btn.disabled = true; btn.textContent = '注册中…'; }
    try {
        const res = await fetch(`${API}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({
                username: String(fd.get('username') || '').trim(),
                email: String(fd.get('email') || '').trim(),
                password: fd.get('password')
            })
        });
        const data = await readJson(res);
        if (data.code === 200) {
            currentUser = data.data;
            closeAuthModal();
            e.target.reset();
            showToast(`注册成功！欢迎, ${currentUser.displayName || currentUser.username}!`, 'success');
            showLoggedInUI();
            loadUserProfile(); loadWeightData(); loadFoodDiaryData(); loadHistory(0);
            if (typeof syncRailFromServer === 'function') syncRailFromServer();
        } else {
            errBox.textContent = data.message || '注册失败';
        }
    } catch (err) {
        errBox.textContent = err.message || '网络错误';
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = '注册'; }
    }
}

function openForgotPassword() {
    closeAuthModal();
    document.getElementById('forgotModal').style.display = 'flex';
}
function closeForgotModal() { document.getElementById('forgotModal').style.display = 'none'; }

async function onForgot(e) {
    e.preventDefault();
    const email = document.getElementById('forgotEmail').value;
    try {
        const res = await fetch(`${API}/auth/forgot-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email })
        });
        const data = await res.json();
        const err = document.getElementById('forgotError');
        if (data.code === 200) { err.className = 'form-error success'; err.textContent = '✅ 重置链接已发送（演示环境链接已打印在后端控制台）'; }
        else { err.textContent = data.message || '发送失败'; }
    } catch (err) { document.getElementById('forgotError').textContent = '网络错误'; }
}

async function loadWeightData() {
    if (!currentUser) return;
    try {
        const [statsRes, chartRes, listRes] = await Promise.all([
            apiFetch(`${API}/weight/stats`),
            apiFetch(`${API}/weight/chart?days=180`),
            apiFetch(`${API}/weight?page=0&size=10`)
        ]);
        const statsData = await readJson(statsRes);
        const chartData = await readJson(chartRes);
        const listData = await readJson(listRes);
        renderWeightStats(statsData.data);
        renderWeightChart(chartData.data || []);
        renderWeightList(listData.data || []);
    } catch (e) { showToast(e.message || '加载体重数据失败', 'error'); }
}

function renderWeightStats(s) {
    if (!s || s.totalRecords === 0) {
        document.getElementById('weightStats').innerHTML = '<div class="stat-item" style="grid-column:1/-1;color:#888;padding:20px;">暂无体重记录，请添加第一条</div>';
        return;
    }
    const changeClass = s.changeKg > 0 ? 'change-positive' : 'change-negative';
    const changePrefix = s.changeKg > 0 ? '+' : '';
    document.getElementById('weightStats').innerHTML = `
        <div class="stat-item"><div class="stat-value">${s.totalRecords}</div><div class="stat-label">总记录数</div></div>
        <div class="stat-item"><div class="stat-value">${s.currentWeight}kg</div><div class="stat-label">当前体重</div></div>
        <div class="stat-item"><div class="stat-value ${changeClass}">${changePrefix}${s.changeKg}kg</div><div class="stat-label">变化量</div></div>
        <div class="stat-item"><div class="stat-value">${s.changePercent}%</div><div class="stat-label">变化率</div></div>
        <div class="stat-item"><div class="stat-value">${s.avgWeight}kg</div><div class="stat-label">平均体重</div></div>
        <div class="stat-item"><div class="stat-value">${s.minWeight}kg / ${s.maxWeight}kg</div><div class="stat-label">最低 / 最高</div></div>
    `;
}

function renderWeightChart(items) {
    const canvas = document.getElementById('weightChart');
    if (!canvas) return;
    if (!window.Chart) {
        const box = canvas.parentElement;
        if (box) box.innerHTML = '<div class="chart-fallback">图表库加载失败（离线或 CDN 被拦截），请稍后刷新重试。</div>';
        return;
    }
    const ctx = canvas.getContext('2d');
    if (weightChart) weightChart.destroy();
    const box = canvas.parentElement;
    const oldEmpty = box ? box.querySelector('.chart-empty') : null;
    if (!items || items.length === 0) {
        if (oldEmpty) return;
        canvas.style.display = 'none';
        if (box) {
            const div = document.createElement('div');
            div.className = 'chart-empty';
            div.innerHTML = '<strong>还没有体重曲线</strong><span>添加第一条体重记录后，这里会显示趋势。</span>';
            box.appendChild(div);
        }
        return;
    }
    if (oldEmpty) oldEmpty.remove();
    canvas.style.display = 'block';
    const dark = isDarkMode();
    const gridColor = dark ? 'rgba(255,255,255,.08)' : '#ece7db';
    const tickColor = dark ? '#8ea79a' : '#8b978d';
    const labels = items.map(i => i.recordDate);
    const data = items.map(i => i.weightKg);
    weightChart = new Chart(ctx, {
        type: 'line',
        data: { labels, datasets: [{
            label: '体重 (kg)',
            data,
            borderColor: '#245b43',
            backgroundColor: 'rgba(36,91,67,0.10)',
            fill: true,
            borderWidth: 2.5,
            tension: 0.35,
            pointRadius: 4,
            pointHoverRadius: 7,
            pointBackgroundColor: dark ? '#1b211a' : '#fffdf8',
            pointBorderColor: '#245b43',
            pointBorderWidth: 2
        }]},
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { display: false }, ticks: { color: tickColor } },
                y: { beginAtZero: false, grid: { color: gridColor }, ticks: { color: tickColor } }
            }
        }
    });
}

function renderWeightList(page) {
    const container = document.getElementById('weightList');
    if (!page || !page.content || page.content.length === 0) {
        container.innerHTML = '<div class="weight-list-empty">暂无记录</div>';
        return;
    }
    container.innerHTML = page.content.map(r => `
        <div class="weight-list-item">
            <span class="weight-list-date">${escHtml(r.recordDate)}</span>
            <span class="weight-list-weight">${escHtml(r.weightKg)} kg</span>
            <span class="weight-list-note">${escHtml(r.note || '')}</span>
            <button type="button" class="btn-delete" data-action="weight-delete" data-id="${escHtml(r.id)}">删除</button>
        </div>
    `).join('');
}

function showWeightForm() { document.getElementById('weightAddForm').style.display = 'block'; }
function hideWeightForm() { document.getElementById('weightAddForm').style.display = 'none'; }

async function addWeightRecord() {
    const weight = parseFloat(document.getElementById('newWeight').value);
    const date = document.getElementById('newWeightDate').value;
    const note = document.getElementById('newWeightNote').value;
    if (!weight || weight <= 0) return showToast('请输入有效的体重', 'error');
    try {
        const res = await fetch(`${API}/weight`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ weightKg: weight, recordDate: date, note })
        });
        const data = await res.json();
        if (data.code === 200) {
            showToast('记录已保存 ✅', 'success');
            hideWeightForm();
            document.getElementById('newWeight').value = '';
            loadWeightData();
        } else showToast(data.message || '保存失败', 'error');
    } catch (e) { showToast('网络错误', 'error'); }
}

async function deleteWeight(id) {
    showConfirm('删除体重记录', '确定要删除这条体重记录吗？', async () => {
        try {
            const res = await fetch(`${API}/weight/${id}`, { method: 'DELETE' });
            const data = await res.json();
            if (data.code === 200) { showToast('已删除', 'success'); loadWeightData(); }
        } catch (e) { showToast('删除失败', 'error'); }
    });
}

let diaryPage = 0;
let diaryTotalPages = 1;
let diaryRequestSeq = 0;

async function loadFoodDiaryData() {
    if (!currentUser) return;
    const seq = ++diaryRequestSeq;
    try {
        diaryPage = 0;
        const filterDate = document.getElementById('fdFilterDate').value || localToday();
        const [summaryRes, listRes, weeklyRes] = await Promise.all([
            apiFetch(`${API}/food-diary/daily?date=${encodeURIComponent(filterDate)}`),
            apiFetch(`${API}/food-diary?page=0&size=20&date=${encodeURIComponent(filterDate)}`),
            apiFetch(`${API}/food-diary/weekly`)
        ]);
        const [summaryData, listData, weeklyData] = await Promise.all([
            readJson(summaryRes), readJson(listRes), readJson(weeklyRes)
        ]);
        if (seq !== diaryRequestSeq) return; // 丢弃过期响应
        diaryTotalPages = listData.data ? listData.data.totalPages || 1 : 1;
        renderDiarySummary(summaryData.data);
        renderDiaryList(listData.data || []);
        renderWeeklyReport(weeklyData.data || []);
        loadWeeklyCompare();
    } catch (e) {
        if (seq === diaryRequestSeq) showToast(e.message || '加载食物日记失败', 'error');
    }
}

async function loadMoreDiary() {
    if (diaryPage + 1 >= diaryTotalPages) return;
    diaryPage++;
    try {
        const filterDate = document.getElementById('fdFilterDate').value || localToday();
        const res = await apiFetch(`${API}/food-diary?page=${diaryPage}&size=20&date=${encodeURIComponent(filterDate)}`);
        const data = await readJson(res);
        if (data.data && data.data.content && data.data.content.length > 0) {
            appendDiaryList(data.data.content);
            diaryTotalPages = data.data.totalPages || diaryTotalPages;
        }
    } catch (e) { showToast(e.message || '加载更多失败', 'error'); }
}

function renderDiarySummary(d) {
    const container = document.getElementById('diarySummary');
    if (!d || d.totalItems === 0) {
        container.innerHTML = `
            <div class="diary-summary-item"><div class="diary-summary-value">今日暂无</div><div class="diary-summary-label">共 0 项</div></div>
            <div class="diary-summary-item"><div class="diary-summary-value">0 kcal</div><div class="diary-summary-label">总热量</div></div>
            <div class="diary-summary-item"><div class="diary-summary-value">0g</div><div class="diary-summary-label">蛋白质</div></div>
            <div class="diary-summary-item"><div class="diary-summary-value">0g</div><div class="diary-summary-label">碳水</div></div>
            <div class="diary-summary-item"><div class="diary-summary-value">0g</div><div class="diary-summary-label">脂肪</div></div>
        `;
        return;
    }

    const pct = (val) => val != null ? Math.min(Math.round(val), 200) : 0;
    const barColor = (val) => {
        if (val == null || val === 0) return '#ccc';
        if (val >= 90 && val <= 110) return '#22c55e';
        if (val > 110) return '#f97316';
        return '#ef4444';
    };

    container.innerHTML = `
        <div class="diary-summary-item"><div class="diary-summary-value">${d.totalItems}</div><div class="diary-summary-label">今日 ${d.totalItems} 项 · ${d.date}</div></div>
        <div class="diary-summary-item">
            <div class="diary-summary-value">${d.totalCalories || 0} kcal</div>
            <div class="diary-summary-label">总热量</div>
            ${d.calorieGoal ? `<div class="goal-bar"><div class="goal-bar-fill" style="width:${pct(d.caloriePercent)}%;background:${barColor(d.caloriePercent)}"></div></div><div class="goal-text">目标 ${d.calorieGoal} kcal · 达成 ${pct(d.caloriePercent)}%</div>` : ''}
        </div>
        <div class="diary-summary-item">
            <div class="diary-summary-value">${d.totalProtein || 0}g</div>
            <div class="diary-summary-label">蛋白质</div>
            ${d.proteinGoal ? `<div class="goal-bar"><div class="goal-bar-fill" style="width:${pct(d.proteinPercent)}%;background:${barColor(d.proteinPercent)}"></div></div><div class="goal-text">目标 ${d.proteinGoal}g · 达成 ${pct(d.proteinPercent)}%</div>` : ''}
        </div>
        <div class="diary-summary-item">
            <div class="diary-summary-value">${d.totalCarbs || 0}g</div>
            <div class="diary-summary-label">碳水</div>
            ${d.carbsGoal ? `<div class="goal-bar"><div class="goal-bar-fill" style="width:${pct(d.carbsPercent)}%;background:${barColor(d.carbsPercent)}"></div></div><div class="goal-text">目标 ${d.carbsGoal}g · 达成 ${pct(d.carbsPercent)}%</div>` : ''}
        </div>
        <div class="diary-summary-item">
            <div class="diary-summary-value">${d.totalFat || 0}g</div>
            <div class="diary-summary-label">脂肪</div>
            ${d.fatGoal ? `<div class="goal-bar"><div class="goal-bar-fill" style="width:${pct(d.fatPercent)}%;background:${barColor(d.fatPercent)}"></div></div><div class="goal-text">目标 ${d.fatGoal}g · 达成 ${pct(d.fatPercent)}%</div>` : ''}
        </div>
    `;
}

function renderDiaryList(data) {
    const container = document.getElementById('diaryList');
    const content = Array.isArray(data) ? data : (data && data.content ? data.content : []);
    if (content.length === 0) {
        container.innerHTML = '<div class="diary-empty">还没有记录食物哦，点右上角「添加食物」开始记录吧</div>';
        return;
    }
    container.innerHTML = buildDiaryItems(content);
    if (diaryPage + 1 < diaryTotalPages) {
        container.innerHTML += `<div style="text-align:center;margin-top:12px"><button class="btn-secondary" onclick="loadMoreDiary()">加载更多</button></div>`;
    }
}

function appendDiaryList(content) {
    const container = document.getElementById('diaryList');
    const btn = container.querySelector('.btn-secondary');
    if (btn) btn.parentElement.remove();
    container.innerHTML += buildDiaryItems(content);
    if (diaryPage + 1 < diaryTotalPages) {
        container.innerHTML += `<div style="text-align:center;margin-top:12px"><button class="btn-secondary" onclick="loadMoreDiary()">加载更多</button></div>`;
    }
}

function buildDiaryItems(items) {
    const mealClass = (t) => {
        const map = { '早餐': 'meal-breakfast', '午餐': 'meal-lunch', '晚餐': 'meal-dinner', '加餐': 'meal-snack' };
        return map[t] || 'meal-snack';
    };
    return items.map(r => {
        diaryRenderCache.set(String(r.id), r);
        const nutri = [
            r.amountGram ? `${escHtml(r.amountGram)}g` : '',
            r.calories ? `${escHtml(r.calories)}kcal` : '',
            r.protein ? '蛋白 ' + escHtml(r.protein) + 'g' : '',
            r.carbs ? '碳水 ' + escHtml(r.carbs) + 'g' : '',
            r.fat ? '脂肪 ' + escHtml(r.fat) + 'g' : ''
        ].filter(Boolean).join(' · ');
        return `
        <div class="diary-item">
            <span class="diary-date">${escHtml(r.recordDate)}</span>
            <span class="diary-item-meal ${mealClass(r.mealType)}">${escHtml(r.mealType || '—')}</span>
            <span class="diary-item-food">${escHtml(r.foodName)}</span>
            <span class="diary-item-nutri">${nutri}</span>
            <div class="diary-item-actions">
                <button type="button" class="btn-tag" data-action="diary-tag" data-id="${escHtml(r.id)}">🔖 便利贴</button>
                <button type="button" class="btn-edit" data-action="edit-diary" data-id="${escHtml(r.id)}">编辑</button>
                <button type="button" class="btn-delete" data-action="delete-diary" data-id="${escHtml(r.id)}">删除</button>
            </div>
        </div>`;
    }).join('');
}

function showFoodDiaryForm() {
    document.getElementById('fdEditId').value = '';
    document.getElementById('foodDiaryFormTitle').textContent = '➕ 添加食物';
    clearFdForm();
    document.getElementById('foodDiaryAddForm').style.display = 'block';
}

function cancelFoodDiary() {
    document.getElementById('foodDiaryAddForm').style.display = 'none';
    clearFdForm();
}

function clearFdForm() {
    ['fdFoodName','fdAmount','fdCalories','fdProtein','fdCarbs','fdFat','fdEditId'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
}

function editFoodDiary(record) {
    document.getElementById('fdEditId').value = record.id;
    document.getElementById('foodDiaryFormTitle').textContent = '编辑食物';
    document.getElementById('fdFoodName').value = record.foodName || '';
    document.getElementById('fdMealType').value = record.mealType || '早餐';
    document.getElementById('fdAmount').value = record.amountGram || '';
    document.getElementById('fdCalories').value = record.calories || '';
    document.getElementById('fdProtein').value = record.protein || '';
    document.getElementById('fdCarbs').value = record.carbs || '';
    document.getElementById('fdFat').value = record.fat || '';
    document.getElementById('fdDate').value = record.recordDate || '';
    document.getElementById('foodDiaryAddForm').style.display = 'block';
    document.getElementById('foodDiaryAddForm').scrollIntoView({ behavior: 'smooth' });
}

async function saveFoodDiary() {
    const name = document.getElementById('fdFoodName').value.trim();
    if (!name) return showToast('请输入食物名称', 'error');
    const editId = document.getElementById('fdEditId').value;
    const num = (id) => {
        const raw = document.getElementById(id).value.trim();
        if (raw === '') return null;
        const v = Number(raw);
        return Number.isFinite(v) && v >= 0 ? v : NaN;
    };
    const amountGram = num('fdAmount');
    const calories = num('fdCalories');
    const protein = num('fdProtein');
    const carbs = num('fdCarbs');
    const fat = num('fdFat');
    if ([amountGram, calories, protein, carbs, fat].some(v => Number.isNaN(v))) {
        return showToast('营养数值必须是非负数字', 'error');
    }
    const payload = {
        foodName: name,
        mealType: document.getElementById('fdMealType').value,
        amountGram,
        calories,
        protein,
        carbs,
        fat,
        recordDate: document.getElementById('fdDate').value || localToday()
    };
    try {
        const url = editId ? `${API}/food-diary/${editId}` : `${API}/food-diary`;
        const method = editId ? 'PUT' : 'POST';
        const res = await apiFetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await readJson(res);
        if (data.code === 200) {
            showToast(editId ? '已更新 ✅' : '已添加 ✅', 'success');
            cancelFoodDiary();
            loadFoodDiaryData();
        } else showToast(data.message || '保存失败', 'error');
    } catch (e) { showToast(e.message || '网络错误', 'error'); }
}

function renderWeeklyReport(weeklyData) {
    const section = document.getElementById('weeklyReportSection');
    if (!section) return;

    if (!window.Chart) {
        section.style.display = 'block';
        section.innerHTML = '<div class="chart-fallback">图表库加载失败（离线或 CDN 被拦截），请稍后刷新重试。</div>';
        return;
    }

    if (!weeklyData || weeklyData.length === 0 || weeklyData.every(d => !d.totalCalories)) {
        section.style.display = 'none';
        return;
    }

    section.style.display = 'block';

    const dark = isDarkMode();
    const gridColor = dark ? 'rgba(255,255,255,.08)' : '#eef3f0';
    const tickColor = dark ? '#8ea79a' : '#9db0a8';
    const legendColor = dark ? '#a9c0b4' : '#6b7f77';

    const labels = weeklyData.map(d => {
        const parts = d.date.split('-');
        return parts[1] + '/' + parts[2];
    });

    const dateRange = document.getElementById('weeklyDateRange');
    if (dateRange) {
        const first = weeklyData[0].date;
        const last = weeklyData[weeklyData.length - 1].date;
        dateRange.textContent = first + ' ~ ' + last;
    }

    const calories = weeklyData.map(d => d.totalCalories || 0);
    const protein = weeklyData.map(d => d.totalProtein || 0);
    const carbs = weeklyData.map(d => d.totalCarbs || 0);
    const fat = weeklyData.map(d => d.totalFat || 0);

    const calGoal = weeklyData.find(d => d.calorieGoal)?.calorieGoal;
    const proGoal = weeklyData.find(d => d.proteinGoal)?.proteinGoal;
    const carbsGoal = weeklyData.find(d => d.carbsGoal)?.carbsGoal;
    const fatGoal = weeklyData.find(d => d.fatGoal)?.fatGoal;

    renderWeeklyStats(weeklyData, calGoal, proGoal, carbsGoal, fatGoal);
    renderCalorieChart(labels, calories, calGoal, gridColor, tickColor, legendColor);
    renderNutrientChart(labels, protein, carbs, fat, proGoal, carbsGoal, fatGoal, gridColor, tickColor, legendColor);
}

function renderWeeklyStats(weeklyData, calGoal, proGoal, carbsGoal, fatGoal) {
    const box = document.getElementById('weeklyStats');
    if (!box) return;

    const daysWithData = weeklyData.filter(d => d.totalCalories && d.totalCalories > 0);
    if (daysWithData.length === 0) { box.innerHTML = ''; return; }

    const avgCal = Math.round(daysWithData.reduce((s, d) => s + (d.totalCalories || 0), 0) / daysWithData.length);
    const sorted = [...daysWithData].sort((a, b) => (b.totalCalories || 0) - (a.totalCalories || 0));
    const bestDay = sorted[0];
    const worstDay = sorted[sorted.length - 1];

    let onTargetDays = 0;
    if (calGoal && calGoal > 0) {
        onTargetDays = daysWithData.filter(d => {
            const pct = ((d.totalCalories || 0) / calGoal) * 100;
            return pct >= 85 && pct <= 115;
        }).length;
    }

    const fmtDate = (d) => { const p = d.date.split('-'); return p[0] ? p[1] + '/' + p[2] : d.date; };

    box.innerHTML = `
        <div class="wstat-item">
            <div class="wstat-value">${avgCal}</div>
            <div class="wstat-unit">kcal</div>
            <div class="wstat-label">日均热量</div>
            ${calGoal ? `<div class="wstat-sub">目标 ${Math.round(calGoal)} kcal</div>` : ''}
        </div>
        <div class="wstat-item">
            <div class="wstat-value">${onTargetDays}</div>
            <div class="wstat-unit">/${daysWithData.length} 天</div>
            <div class="wstat-label">热量达标</div>
            <div class="wstat-sub">85%-115% 目标范围</div>
        </div>
        <div class="wstat-item">
            <div class="wstat-value">${bestDay.totalCalories || 0}</div>
            <div class="wstat-unit">kcal</div>
            <div class="wstat-label">最高日 · ${fmtDate(bestDay)}</div>
            ${calGoal ? `<div class="wstat-sub">${Math.round((bestDay.totalCalories || 0) / calGoal * 100)}% 目标</div>` : ''}
        </div>
        <div class="wstat-item">
            <div class="wstat-value">${worstDay.totalCalories || 0}</div>
            <div class="wstat-unit">kcal</div>
            <div class="wstat-label">最低日 · ${fmtDate(worstDay)}</div>
            ${calGoal ? `<div class="wstat-sub">${Math.round((worstDay.totalCalories || 0) / calGoal * 100)}% 目标</div>` : ''}
        </div>
    `;
}

function renderCalorieChart(labels, calories, calGoal, gridColor, tickColor, legendColor) {
    const container = document.getElementById('calorieChartContainer');
    const canvas = document.getElementById('weeklyCalorieChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (weeklyCalorieChart) weeklyCalorieChart.destroy();

    const datasets = [{
        label: '热量摄入 (kcal)',
        data: calories,
        backgroundColor: calories.map(v => {
            if (!calGoal) return 'rgba(18,183,106,0.55)';
            const pct = v / calGoal;
            if (pct >= 0.85 && pct <= 1.15) return 'rgba(34,197,94,0.6)';
            if (pct > 1.15) return 'rgba(249,115,22,0.5)';
            return 'rgba(239,68,68,0.45)';
        }),
        borderColor: calories.map(v => {
            if (!calGoal) return '#12b76a';
            const pct = v / calGoal;
            if (pct >= 0.85 && pct <= 1.15) return '#22c55e';
            if (pct > 1.15) return '#f97316';
            return '#ef4444';
        }),
        borderWidth: 1.5,
        borderRadius: 8,
        borderSkipped: false
    }];

    if (calGoal && calGoal > 0) {
        datasets.push({
            label: '目标 ' + calGoal + ' kcal',
            data: Array(labels.length).fill(calGoal),
            type: 'line',
            borderColor: '#0c9a58',
            borderWidth: 2.5,
            borderDash: [6, 4],
            pointRadius: 0,
            fill: false,
            tension: 0
        });
    }

    weeklyCalorieChart = new Chart(ctx, {
        type: 'bar',
        data: { labels, datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { position: 'bottom', labels: { color: legendColor, usePointStyle: true, padding: 16, boxWidth: 12 } },
                tooltip: {
                    callbacks: {
                        label: function(ctx) { return ctx.dataset.label + ': ' + ctx.parsed.y + ' kcal'; }
                    }
                }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: tickColor, font: { weight: '600' } } },
                y: {
                    beginAtZero: true,
                    grid: { color: gridColor },
                    ticks: { color: tickColor, callback: v => v + ' kcal' }
                }
            }
        }
    });
}

function renderNutrientChart(labels, protein, carbs, fat, proGoal, carbsGoal, fatGoal, gridColor, tickColor, legendColor) {
    const container = document.getElementById('nutrientChartContainer');
    const canvas = document.getElementById('weeklyNutrientChart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (weeklyNutrientChart) weeklyNutrientChart.destroy();

    const datasets = [
        {
            label: '蛋白质 (g)',
            data: protein,
            borderColor: '#10b981',
            backgroundColor: 'rgba(16,185,129,0.08)',
            borderWidth: 2.5,
            tension: 0.35,
            fill: false,
            pointRadius: 5,
            pointHoverRadius: 7,
            pointBackgroundColor: '#fff',
            pointBorderColor: '#10b981',
            pointBorderWidth: 2
        },
        {
            label: '碳水 (g)',
            data: carbs,
            borderColor: '#f59e0b',
            backgroundColor: 'rgba(245,158,11,0.08)',
            borderWidth: 2.5,
            tension: 0.35,
            fill: false,
            pointRadius: 5,
            pointHoverRadius: 7,
            pointBackgroundColor: '#fff',
            pointBorderColor: '#f59e0b',
            pointBorderWidth: 2
        },
        {
            label: '脂肪 (g)',
            data: fat,
            borderColor: '#ef4444',
            backgroundColor: 'rgba(239,68,68,0.08)',
            borderWidth: 2.5,
            tension: 0.35,
            fill: false,
            pointRadius: 5,
            pointHoverRadius: 7,
            pointBackgroundColor: '#fff',
            pointBorderColor: '#ef4444',
            pointBorderWidth: 2
        }
    ];

    if (proGoal && proGoal > 0) {
        datasets.push({
            label: '蛋白质目标 ' + proGoal + 'g',
            data: Array(labels.length).fill(proGoal),
            borderColor: '#10b981',
            borderWidth: 1.5,
            borderDash: [5, 5],
            pointRadius: 0,
            fill: false,
            tension: 0
        });
    }
    if (carbsGoal && carbsGoal > 0) {
        datasets.push({
            label: '碳水目标 ' + carbsGoal + 'g',
            data: Array(labels.length).fill(carbsGoal),
            borderColor: '#f59e0b',
            borderWidth: 1.5,
            borderDash: [5, 5],
            pointRadius: 0,
            fill: false,
            tension: 0
        });
    }
    if (fatGoal && fatGoal > 0) {
        datasets.push({
            label: '脂肪目标 ' + fatGoal + 'g',
            data: Array(labels.length).fill(fatGoal),
            borderColor: '#ef4444',
            borderWidth: 1.5,
            borderDash: [5, 5],
            pointRadius: 0,
            fill: false,
            tension: 0
        });
    }

    weeklyNutrientChart = new Chart(ctx, {
        type: 'line',
        data: { labels, datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { position: 'bottom', labels: { color: legendColor, usePointStyle: true, padding: 16, boxWidth: 12 } }
            },
            scales: {
                x: { grid: { display: false }, ticks: { color: tickColor, font: { weight: '600' } } },
                y: {
                    beginAtZero: true,
                    grid: { color: gridColor },
                    ticks: { color: tickColor, callback: v => v + 'g' }
                }
            }
        }
    });
}

async function deleteFoodDiary(id) {
    showConfirm('删除饮食记录', '确定要删除这条饮食记录吗？', async () => {
        try {
            const res = await fetch(`${API}/food-diary/${id}`, { method: 'DELETE' });
            const data = await res.json();
            if (data.code === 200) { showToast('已删除', 'success'); loadFoodDiaryData(); }
            else showToast(data.message || '删除失败', 'error');
        } catch (e) { showToast('删除失败', 'error'); }
    });
}

function changeDiaryDate() {
    const filterDate = document.getElementById('fdFilterDate').value;
    if (filterDate) {
        document.getElementById('fdDate').value = filterDate;
    }
    loadFoodDiaryData();
}

async function downloadCsv(url, filename) {
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    try {
        const res = await apiFetch(url);
        if (!res.ok) {
            const data = await readJson(res);
            showToast(data.message || '导出失败', 'error');
            return;
        }
        const blob = await res.blob();
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = objectUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(objectUrl), 2000);
        showToast('导出成功', 'success');
    } catch (e) { showToast(e.message || '导出失败', 'error'); }
}

function downloadDiaryCsv() {
    downloadCsv(`${API}/food-diary/export`, 'food_diary.csv');
}

function downloadWeightCsv() {
    downloadCsv(`${API}/weight/export`, 'weight_records.csv');
}

async function loadWeeklyCompare() {
    const box = document.getElementById('weeklyCompare');
    const card = document.getElementById('weeklyCompareCard');
    if (!box || !card) return;
    try {
        const res = await fetch(`${API}/food-diary/weekly-compare`);
        const data = await res.json();
        if (data.code !== 200 || !data.data) { card.style.display = 'none'; return; }
        const d = data.data;
        const tw = d.thisWeek;
        const lw = d.lastWeek;
        const trendLabel = { up: '⬆ 热量较上周增加', down: '⬇ 热量较上周减少', stable: '➡ 热量与上周持平' }[d.calTrend] || '';
        box.innerHTML = `<div class="weekly-compare">
            <div class="wc-week">
                <div class="wc-label">本周 (${tw.days}天有记录)</div>
                <div class="wc-row"><span>总热量</span><b>${tw.totalCal} kcal</b></div>
                <div class="wc-row"><span>蛋白质</span><b>${tw.totalProtein}g</b></div>
                <div class="wc-row"><span>碳水</span><b>${tw.totalCarbs}g</b></div>
                <div class="wc-row"><span>脂肪</span><b>${tw.totalFat}g</b></div>
            </div>
            <div class="wc-week">
                <div class="wc-label">上周 (${lw.days}天有记录)</div>
                <div class="wc-row"><span>总热量</span><b>${lw.totalCal} kcal</b></div>
                <div class="wc-row"><span>蛋白质</span><b>${lw.totalProtein}g</b></div>
                <div class="wc-row"><span>碳水</span><b>${lw.totalCarbs}g</b></div>
                <div class="wc-row"><span>脂肪</span><b>${lw.totalFat}g</b></div>
            </div>
        </div>
        <div class="wc-trend ${d.calTrend}">${trendLabel} ${d.calDiff} kcal</div>`;
        card.style.display = 'block';
    } catch (e) {
        card.style.display = 'none';
    }
}

async function loadGoalProgress() {
    const box = document.getElementById('goalProgress');
    if (!box) return;
    if (!currentUser) { box.innerHTML = '<p class="muted">请先登录</p>'; return; }
    try {
        const [gapRes, profileRes] = await Promise.all([
            fetch(`${API}/coach/gap`),
            fetch(`${API}/user/profile`)
        ]);
        const gapData = await gapRes.json();
        const profileData = await profileRes.json();
        const g = (gapData.code === 200 && gapData.data) ? gapData.data : null;
        const p = (profileData.code === 200 && profileData.data) ? profileData.data : null;

        if (!p || (!p.healthGoal && !p.weightKg && !p.heightCm)) {
            box.innerHTML = '<p class="muted">尚未设置健康目标与身体数据，请先完善画像。</p>';
            return;
        }

        const goalKey = String(p.healthGoal || 'MAINTAIN').toUpperCase();
        const goalLabel = {
            LOSE_WEIGHT: '减脂', GAIN_WEIGHT: '增重', BUILD_MUSCLE: '增肌',
            IMPROVE_HEALTH: '改善健康', MAINTAIN: '维持'
        }[goalKey] || '均衡饮食';
        const bmi = (p.heightCm && p.weightKg) ? (p.weightKg / ((p.heightCm / 100) ** 2)).toFixed(1) : null;
        const bmiLabel = bmi ? (bmi < 18.5 ? '偏瘦' : bmi < 24 ? '标准' : bmi < 28 ? '偏重' : '肥胖') : null;

        let html = '';
        html += `<div class="goal-stat"><div class="goal-stat-val">${escHtml(goalLabel)}</div><div class="goal-stat-label">当前目标</div></div>`;
        if (bmi) {
            const warnCls = (bmi >= 24) ? ' goal-stat-warn' : '';
            html += `<div class="goal-stat${warnCls}"><div class="goal-stat-val">${escHtml(bmi)}</div><div class="goal-stat-label">BMI · ${escHtml(bmiLabel)}</div><div class="goal-stat-bar"><div class="goal-stat-fill" style="width:${Math.min(bmi * 3.5, 100)}%"></div></div></div>`;
        }
        if (g) {
            html += `<div class="goal-stat"><div class="goal-stat-val">${escHtml(g.trackedDays)}</div><div class="goal-stat-label">已追踪天数</div></div>`;
        }
        box.innerHTML = html || '<p class="muted">暂无目标数据。</p>';
    } catch (e) {
        box.innerHTML = '<p class="muted">加载失败</p>';
    }
}

let historyPage = 0;
let historySearchTerm = '';
let historySearchTimer = null;

async function loadHistory(page) {
    if (!currentUser) return;
    historyPage = page;
    try {
        const params = new URLSearchParams({ page: String(page), size: '5' });
        if (historySearchTerm) params.set('q', historySearchTerm);
        const res = await apiFetch(`${API}/history?${params}`);
        const data = await readJson(res);
        if (data.code === 200) renderHistory(data.data);
        else renderHistory(null);
    } catch (e) { renderHistory(null); }
}

function filterHistory() {
    const term = document.getElementById('historySearch').value.trim();
    if (historySearchTimer) clearTimeout(historySearchTimer);
    historySearchTimer = setTimeout(() => {
        historySearchTerm = term;
        loadHistory(0);
    }, 300);
}

function renderHistory(page) {
    const list = document.getElementById('historyList');
    const pagination = document.getElementById('historyPagination');
    if (!page || !page.content || page.content.length === 0) {
        list.innerHTML = historySearchTerm
            ? '<div class="history-empty">没有找到匹配的记录</div>'
            : '<div class="history-empty">还没有推荐历史哦，填写信息生成第一条推荐吧</div>';
        pagination.innerHTML = '';
        return;
    }
    const items = page.content;
    list.innerHTML = items.map(h => `
        <div class="history-item" role="button" tabindex="0" data-action="history-detail" data-id="${escHtml(h.id)}">
            <div class="history-info">
                <div class="history-summary">${escHtml(h.summary || 'AI 推荐记录')}</div>
                <div class="history-meta">
                    <span>${escHtml(new Date(h.createdAt).toLocaleString('zh-CN'))}</span>
                    <span>🍴 ${escHtml(h.recipesCount || 0)} 个食谱</span>
                    <span>🤖 ${escHtml(h.aiProvider || 'AI')}</span>
                </div>
            </div>
            <div class="history-actions">
                <button type="button" class="btn-delete" data-action="history-delete" data-id="${escHtml(h.id)}">删除</button>
            </div>
        </div>
    `).join('');
    const totalPages = Math.ceil(page.totalElements / page.size);
    if (totalPages > 1) {
        let html = '';
        for (let i = 0; i < totalPages; i++) {
            html += `<button type="button" class="page-btn ${i === historyPage ? 'active' : ''}" onclick="loadHistory(${i})">${i + 1}</button>`;
        }
        pagination.innerHTML = html;
    } else pagination.innerHTML = '';
}

async function showHistoryDetail(id) {
    try {
        const res = await apiFetch(`${API}/history/${id}`);
        const data = await readJson(res);
        if (data.code === 200 && data.data && data.data.recommendation) {
            // 结果区位于「画像 & 推荐」视图内，必须先切换视图，否则渲染在隐藏容器里
            switchMainTab('profile');
            renderResult(data.data.recommendation);
            setTimeout(() => document.getElementById('resultSection').scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
        } else {
            showToast(data.message || '加载详情失败', 'error');
        }
    } catch (e) { showToast(e.message || '加载详情失败', 'error'); }
}

async function deleteHistory(id) {
    showConfirm('删除历史记录', '确定要删除这条历史记录吗？', async () => {
        try {
            const res = await apiFetch(`${API}/history/${id}`, { method: 'DELETE' });
            const data = await readJson(res);
            if (data.code === 200) { showToast('已删除', 'success'); loadHistory(historyPage); }
        } catch (e) { showToast(e.message || '删除失败', 'error'); }
    });
}

let foodPreferences = {};

async function loadFoodPreferences() {
    try {
        const res = await fetch(`${API}/food-preference`);
        const data = await res.json();
        if (data.code === 200 && data.data) {
            foodPreferences = {};
            data.data.forEach(p => { foodPreferences[p.foodName] = p.preference; });
        }
    } catch (e) { /* 非关键 */ }
}

async function recognizeFood() {
    const input = document.getElementById('fdAiInput');
    const description = input.value.trim();
    if (!description) { showToast('请输入食物描述', 'error'); return; }

    const btn = document.querySelector('.btn-ai-recognize');
    btn.disabled = true;
    btn.textContent = '识别中...';

    try {
        const res = await fetch(`${API}/food-diary/recognize`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ description })
        });
        const data = await res.json();
        if (data.code === 200 && data.data) {
            const r = data.data;
            document.getElementById('fdFoodName').value = r.foodName || description;
            document.getElementById('fdCalories').value = r.calories || '';
            document.getElementById('fdProtein').value = r.proteinGrams || '';
            document.getElementById('fdCarbs').value = r.carbsGrams || '';
            document.getElementById('fdFat').value = r.fatGrams || '';
            document.getElementById('fdAmount').value = r.servingSize || '';
            if (r.mealType) {
                const mealMap = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' };
                const mt = mealMap[r.mealType] || r.mealType;
                const sel = document.getElementById('fdMealType');
                for (let i = 0; i < sel.options.length; i++) {
                    if (sel.options[i].value === mt) { sel.selectedIndex = i; break; }
                }
            }
            showToast('AI 识别成功！请核对后保存', 'success');
        } else {
            showToast(data.message || 'AI 识别失败，请手动填写', 'error');
        }
    } catch (e) { showToast('AI 识别网络错误', 'error'); }
    finally { btn.disabled = false; btn.textContent = 'AI 识别'; }
}

async function toggleFoodPreference(foodName, category, preference) {
    if (!currentUser) { showToast('请先登录再标记偏好', 'error'); return; }
    try {
        const res = await apiFetch(`${API}/food-preference`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ foodName, category: category || '', preference })
        });
        const data = await readJson(res);
        if (data.code === 200) {
            if (preference === 'NEUTRAL') {
                delete foodPreferences[foodName];
            } else {
                foodPreferences[foodName] = preference;
            }
            refreshRecipePreferenceButtons();
            showToast(preference === 'LIKE' ? `已标记喜欢: ${foodName}` :
                       preference === 'DISLIKE' ? `已标记避开: ${foodName}` :
                       `已取消标记: ${foodName}`, 'success');
        } else {
            showToast(data.message || '操作失败', 'error');
        }
    } catch (e) { showToast(e.message || '网络错误', 'error'); }
}

function refreshRecipePreferenceButtons() {
    document.querySelectorAll('.recipe-card').forEach(card => {
        const name = card.dataset.recipeName;
        if (!name) return;
        const pref = foodPreferences[name] || '';
        const likeBtn = card.querySelector('.btn-pref.like');
        const dislikeBtn = card.querySelector('.btn-pref.dislike');
        if (likeBtn) {
            likeBtn.classList.toggle('active', pref === 'LIKE');
            likeBtn.textContent = pref === 'LIKE' ? '已喜欢' : '喜欢';
        }
        if (dislikeBtn) {
            dislikeBtn.classList.toggle('active', pref === 'DISLIKE');
            dislikeBtn.textContent = pref === 'DISLIKE' ? '已避开' : '避开';
        }
    });
}

async function toggleFavorite(recipe) {
    if (!currentUser) { showToast('请先登录再收藏食谱', 'error'); return; }
    const recipeName = recipe.name || '未命名食谱';
    const isFav = favoriteRecipeNames.has(recipeName);
    try {
        let res;
        if (isFav) {
            res = await apiFetch(`${API}/favorite-recipes?recipeName=${encodeURIComponent(recipeName)}`, { method: 'DELETE' });
        } else {
            res = await apiFetch(`${API}/favorite-recipes`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    recipeName: recipeName,
                    recipeJson: JSON.stringify(recipe),
                    summary: recipe.description || ''
                })
            });
        }
        const data = await readJson(res);
        if (data.code === 200) {
            if (isFav) favoriteRecipeNames.delete(recipeName);
            else favoriteRecipeNames.add(recipeName);
            document.querySelectorAll('.recipe-card').forEach(card => {
                if (card.dataset.recipeName !== recipeName) return;
                const btn = card.querySelector('.btn-fav');
                if (!btn) return;
                btn.classList.toggle('active', !isFav);
                btn.textContent = !isFav ? '已收藏' : '收藏';
            });
            showToast(isFav ? '已取消收藏' : '已收藏食谱', 'success');
            if (!isFav) loadFavorites(0);
        } else {
            showToast(data.message || '收藏失败', 'error');
        }
    } catch (e) { showToast(e.message || '网络错误', 'error'); }
}

let favPage = 0;

async function loadFavorites(page) {
    if (!currentUser) return;
    favPage = page;
    try {
        const res = await apiFetch(`${API}/favorite-recipes?page=${page}&size=10`);
        const data = await readJson(res);
        const result = data.data || { content: [], totalPages: 0, number: 0 };
        (result.content || []).forEach(f => favoriteRecipeNames.add(f.recipeName));
        renderFavorites(result);
    } catch (e) { showToast(e.message || '加载收藏失败', 'error'); }
}

function renderFavorites(page) {
    const container = document.getElementById('favoritesList');
    const pagination = document.getElementById('favoritesPagination');

    if (!page || !page.content || page.content.length === 0) {
        container.innerHTML = '<div class="fav-empty">还没有收藏的食谱哦，在推荐结果中点击收藏即可保存</div>';
        pagination.innerHTML = '';
        return;
    }

    favoriteRenderCache.clear();
    container.innerHTML = page.content.map(f => {
        favoriteRenderCache.set(String(f.id), f);
        return `
        <div class="fav-item">
            <div class="fav-info">
                <div class="fav-name">${escHtml(f.recipeName)}</div>
                <div class="fav-summary">${escHtml(f.summary || '')}</div>
                <div class="fav-meta">${escHtml(new Date(f.createdAt).toLocaleDateString('zh-CN'))}</div>
            </div>
            <div class="fav-actions">
                <button type="button" class="btn-tag" data-action="fav-tag" data-id="${escHtml(f.id)}">🔖 便利贴</button>
                <button type="button" class="btn-link" data-action="fav-view" data-id="${escHtml(f.id)}">查看</button>
                <button type="button" class="btn-delete" data-action="fav-delete" data-id="${escHtml(f.id)}">删除</button>
            </div>
        </div>`;
    }).join('');

    let html = '';
    if (page.totalPages > 1) {
        html += `<button type="button" class="page-btn" onclick="loadFavorites(${favPage - 1})" ${favPage === 0 ? 'disabled' : ''}>‹</button>`;
        html += `<span>${page.number + 1} / ${page.totalPages}</span>`;
        html += `<button type="button" class="page-btn" onclick="loadFavorites(${favPage + 1})" ${favPage >= page.totalPages - 1 ? 'disabled' : ''}>›</button>`;
    }
    pagination.innerHTML = html;
}

function viewFavoriteDetail(fav) {
    let recipe = null;
    try { recipe = JSON.parse(fav.recipeJson || '{}'); } catch (e) { recipe = null; }
    recipe = recipe || {};

    const desc = recipe.description || recipe.desc || fav.summary || '';
    const ingredients = recipe.ingredients || recipe.ingredient || [];
    const steps = recipe.steps || recipe.method || recipe.step || [];
    const nutrition = recipe.nutritionalInfo || recipe.nutrition || {};
    const nutritionNote = recipe.nutritionNote || '';
    const time = recipe.estimatedTime || recipe.time || '';
    const difficulty = recipe.difficulty || '';
    const tags = recipe.tags || [];
    const source = recipe.source || '';

    let html = '';

    if (desc) html += `<p class="detail-desc">${escHtml(desc)}</p>`;

    // 顶部元信息：耗时 / 难度 / 来源
    const metaParts = [];
    if (time) metaParts.push('⏱️ ' + escHtml(time));
    if (difficulty) metaParts.push('🔥 难度 ' + escHtml(difficulty));
    if (source) metaParts.push('📎 ' + escHtml(source));
    if (metaParts.length) html += `<div class="recipe-meta">${metaParts.join(' · ')}</div>`;

    // 营养信息（对象或纯文本）
    const nutKeys = [
        ['calories', '热量'],
        ['protein', '蛋白质'],
        ['carbs', '碳水'],
        ['fat', '脂肪'],
        ['fiber', '膳食纤维'],
        ['sodium', '钠']
    ];
    const nutParts = [];
    if (nutrition && typeof nutrition === 'object') {
        nutKeys.forEach(([k, label]) => {
            const v = nutrition[k];
            if (v != null && v !== '') nutParts.push(label + ' ' + v);
        });
    } else if (typeof nutrition === 'string' && nutrition.trim()) {
        nutParts.push(nutrition.trim());
    }
    if (nutritionNote) {
        html += `<div class="recipe-nutrition-note">💡 ${escHtml(nutritionNote)}</div>`;
    } else if (nutParts.length) {
        html += `<div class="recipe-nutrition">🥗 ${nutParts.map(escHtml).join(' · ')}</div>`;
    }

    // 食材
    if (Array.isArray(ingredients) && ingredients.length) {
        html += `<div class="recipe-section"><h4>食材</h4><ul>${ingredients.map(i => `<li>${escHtml(i)}</li>`).join('')}</ul></div>`;
    }

    // 做法：支持数组或字符串
    if (Array.isArray(steps) && steps.length) {
        html += `<div class="recipe-section"><h4>做法</h4><ol>${steps.map(s => `<li>${escHtml(s)}</li>`).join('')}</ol></div>`;
    } else if (typeof steps === 'string' && steps.trim()) {
        html += `<div class="recipe-section"><h4>做法</h4><p>${escHtml(steps)}</p></div>`;
    }

    // 标签
    if (Array.isArray(tags) && tags.length) {
        html += `<div class="tags">${tags.map(t => `<span class="tag">${escHtml(t)}</span>`).join('')}</div>`;
    }

    // 兜底：实在没有内容
    if (!html) {
        html = `<p class="detail-desc">${escHtml(fav.summary || '暂无更多详情')}</p>`;
    }

    openDetailModal(fav.recipeName || '食谱详情', html);
}

async function deleteFavorite(id) {
    showConfirm('删除收藏', '确定要删除这条收藏的食谱吗？', async () => {
        try {
            const res = await apiFetch(`${API}/favorite-recipes/${id}`, { method: 'DELETE' });
            const data = await readJson(res);
            if (data.code === 200) {
                const fav = favoriteRenderCache.get(String(id));
                if (fav) favoriteRecipeNames.delete(fav.recipeName);
                showToast('已删除', 'success');
                loadFavorites(favPage);
            } else {
                showToast(data.message || '删除失败', 'error');
            }
        } catch (e) { showToast(e.message || '删除失败', 'error'); }
    });
}

// ========== Recipe Book ==========

let rcpPage = 0;

async function saveToRecipeBook(recipe) {
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    const recipeName = recipe.name || '未命名食谱';
    try {
        const payload = {
            name: recipeName,
            description: recipe.description || '',
            mealType: recipe.mealType || recipe.type || 'lunch',
            ingredientsText: Array.isArray(recipe.ingredients) ? recipe.ingredients.join('\n') : (recipe.ingredients || ''),
            stepsText: Array.isArray(recipe.steps) ? recipe.steps.join('\n') : (recipe.steps || ''),
            calories: recipe.nutritionalInfo?.calories || recipe.calories || null,
            proteinGrams: recipe.nutritionalInfo?.protein || recipe.proteinGrams || null,
            carbsGrams: recipe.nutritionalInfo?.carbs || recipe.carbsGrams || null,
            fatGrams: recipe.nutritionalInfo?.fat || recipe.fatGrams || null,
            tags: Array.isArray(recipe.tags) ? recipe.tags.join(',') : (recipe.tags || '')
        };
        const res = await apiFetch(`${API}/recipe-book`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await readJson(res);
        if (data.code === 200) {
            showToast('已加入食谱本', 'success');
            if (document.querySelector('.app-tab.active')?.dataset.target === 'recipebook') {
                loadRecipeBook(rcpPage);
            }
        } else {
            showToast(data.message || '保存失败', 'error');
        }
    } catch (e) { showToast(e.message || '保存失败', 'error'); }
}

async function loadRecipeBook(page) {
    if (!currentUser) return;
    rcpPage = page;
    const mealType = document.getElementById('recipebookMealFilter')?.value || '';
    const params = new URLSearchParams({ page: String(page), size: '12' });
    if (mealType) params.set('mealType', mealType);
    try {
        const res = await apiFetch(`${API}/recipe-book?${params}`);
        const data = await readJson(res);
        renderRecipeBook(data.data || { content: [], totalPages: 0, number: 0 });
    } catch (e) { showToast(e.message || '加载食谱本失败', 'error'); }
}

function renderRecipeBook(page) {
    const container = document.getElementById('recipebookList');
    const pagination = document.getElementById('recipebookPagination');

    if (!page || !page.content || page.content.length === 0) {
        container.innerHTML = '<div class="fav-empty">食谱本是空的。在 AI 推荐结果中点击「📖 加入食谱本」即可保存</div>';
        pagination.innerHTML = '';
        return;
    }

    recipeBookRenderCache.clear();
    container.innerHTML = page.content.map(r => {
        recipeBookRenderCache.set(String(r.id), r);
        return `
        <div class="recipebook-card">
            <div class="recipebook-card-head">
                <span class="recipebook-meal-badge ${escHtml(r.mealType || 'lunch')}">${escHtml(mealLabel(r.mealType))}</span>
                <span class="recipebook-name">${escHtml(r.name)}</span>
                ${r.tags ? `<span class="recipebook-tags">${r.tags.split(',').map(t => `<span class="tag">${escHtml(t.trim())}</span>`).join('')}</span>` : ''}
            </div>
            ${r.description ? `<p class="recipebook-desc">${escHtml(r.description)}</p>` : ''}
            <div class="recipebook-nutrition">
                ${r.calories != null ? `<span>🔥 ${escHtml(Math.round(r.calories))} kcal</span>` : ''}
                ${r.proteinGrams != null ? `<span>🥩 蛋白 ${escHtml(r.proteinGrams)}g</span>` : ''}
                ${r.carbsGrams != null ? `<span>🍚 碳水 ${escHtml(r.carbsGrams)}g</span>` : ''}
                ${r.fatGrams != null ? `<span>🧈 脂肪 ${escHtml(r.fatGrams)}g</span>` : ''}
            </div>
            ${r.ingredientsText ? `<div class="recipebook-section"><strong>食材</strong><p>${escHtml(r.ingredientsText)}</p></div>` : ''}
            ${r.stepsText ? `<div class="recipebook-section"><strong>做法</strong><p>${escHtml(r.stepsText)}</p></div>` : ''}
            <div class="recipebook-actions">
                <button type="button" class="btn-primary btn-sm" data-action="recipe-use" data-id="${escHtml(r.id)}">🥗 今天就吃这个</button>
                <button type="button" class="btn-tag" data-action="recipe-tag" data-id="${escHtml(r.id)}">🔖 加到便利贴</button>
                <button type="button" class="btn-delete" data-action="recipe-delete" data-id="${escHtml(r.id)}">删除</button>
            </div>
        </div>`;
    }).join('');

    let html = '';
    if (page.totalPages > 1) {
        html += `<button type="button" class="page-btn" onclick="loadRecipeBook(${rcpPage - 1})" ${rcpPage === 0 ? 'disabled' : ''}>‹</button>`;
        html += `<span>${page.number + 1} / ${page.totalPages}</span>`;
        html += `<button type="button" class="page-btn" onclick="loadRecipeBook(${rcpPage + 1})" ${rcpPage >= page.totalPages - 1 ? 'disabled' : ''}>›</button>`;
    }
    pagination.innerHTML = html;
}

function mealLabel(type) {
    return { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' }[type] || type || '午餐';
}

async function useRecipe(id) {
    if (!currentUser) return;
    try {
        const res = await apiFetch(`${API}/recipe-book/${id}/use`, { method: 'POST' });
        const data = await readJson(res);
        if (data.code === 200) {
            showToast('已记入今日饮食', 'success');
            loadFoodDiaryData();
        } else {
            showToast(data.message || '操作失败', 'error');
        }
    } catch (e) { showToast(e.message || '网络错误', 'error'); }
}

async function deleteRecipe(id) {
    showConfirm('删除食谱', '确定要从食谱本中删除这条食谱吗？', async () => {
        try {
            const res = await apiFetch(`${API}/recipe-book/${id}`, { method: 'DELETE' });
            const data = await readJson(res);
            if (data.code === 200) { showToast('已删除', 'success'); loadRecipeBook(rcpPage); }
        } catch (e) { showToast(e.message || '删除失败', 'error'); }
    });
}