/* ================================================================
   NutriAI · AI 对话助手 + 便利贴看板
   依赖: app.js (currentUser, API, showToast, escHtml)
   ================================================================ */
let assistantBoardCache = null;
const PICKS_KEY = 'nutriai_rail_picks_v2';
const SESSION_TAGS_KEY = 'nutriai_session_tags_v1';
const CHAT_HISTORY_KEY = 'nutriai_chat_history_v1';
const CHAT_MAX_MESSAGES = 50;
let sessionTags = [];
let conversationMemory = [];
let recommendedRecipes = [];
let aiRecipeCache = [];
let voiceRec = null;

function loadSessionTags() {
    try { return JSON.parse(localStorage.getItem(SESSION_TAGS_KEY)) || []; }
    catch { return []; }
}
function saveSessionTags() { localStorage.setItem(SESSION_TAGS_KEY, JSON.stringify(sessionTags)); }

function loadChatHistory() {
    try { return JSON.parse(localStorage.getItem(CHAT_HISTORY_KEY)) || []; }
    catch { return []; }
}
function saveChatHistory(history) {
    if (history.length > CHAT_MAX_MESSAGES) history = history.slice(-CHAT_MAX_MESSAGES);
    localStorage.setItem(CHAT_HISTORY_KEY, JSON.stringify(history));
}
function stripHtmlTags(html) {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    return tmp.textContent || tmp.innerText || '';
}

function buildConversationMemory(history) {
    const mem = [];
    for (const msg of history) {
        if (msg.role === 'user') {
            mem.push({ role: 'user', content: msg.text || '' });
        } else {
            mem.push({ role: 'assistant', content: stripHtmlTags(msg.html || '') });
        }
    }
    if (mem.length > 20) return mem.slice(-20);
    return mem;
}

function restoreChatHistory() {
    const history = loadChatHistory();
    const wrap = document.getElementById('chatMessages');
    if (!wrap || history.length === 0) return;
    wrap.innerHTML = '';
    history.forEach(msg => {
        const cls = msg.role === 'user' ? 'me' : 'ai';
        const headText = msg.role === 'user' ? (currentUser ? (currentUser.displayName || currentUser.username) : '我') : '🤖 NutriAI';
        const el = document.createElement('div');
        el.className = 'chat-bubble ' + cls;
        el.innerHTML = '<div class="bubble-head">' + escHtml(headText) + '</div><div class="bubble-body"></div>';
        const body = el.querySelector('.bubble-body');
        if (msg.role === 'user') {
            body.textContent = msg.text != null ? msg.text : '';
        } else {
            body.innerHTML = sanitizeHtml(msg.html || '');
        }
        wrap.appendChild(el);
    });
    conversationMemory = buildConversationMemory(history);
}
function clearChatHistory() {
    localStorage.removeItem(CHAT_HISTORY_KEY);
    conversationMemory = [];
    const wrap = document.getElementById('chatMessages');
    if (wrap) {
        wrap.innerHTML = '<div class="chat-bubble ai"><div class="bubble-head">🤖 NutriAI</div><div class="bubble-body">在呢 👋 今天想干嘛？直接说人话就行～</div></div>';
    }
    showToast('聊天历史已清空', 'info');
}

function exportChatHistory() {
    const history = loadChatHistory();
    if (history.length === 0) { showToast('还没有对话记录', 'info'); return; }
    let txt = 'NutriAI 聊天记录\n' + new Date().toLocaleString() + '\n' + '='.repeat(40) + '\n\n';
    history.forEach(msg => {
        txt += (msg.role === 'user' ? '🧑 我' : '🤖 NutriAI') + ':\n';
        const div = document.createElement('div');
        if (msg.role === 'user') {
            txt += (msg.text || '') + '\n\n';
        } else {
            div.innerHTML = sanitizeHtml(msg.html || '');
            txt += div.textContent + '\n\n';
        }
    });
    const blob = new Blob([txt], { type: 'text/plain;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'NutriAI_chat_' + new Date().toISOString().slice(0, 10) + '.txt';
    a.click();
    URL.revokeObjectURL(a.href);
    showToast('聊天记录已导出 📥', 'success');
}

function toggleDarkMode() {
    const isDark = document.documentElement.classList.toggle('dark');
    localStorage.setItem('nutriai_dark_mode', isDark ? '1' : '0');
    const btn = document.getElementById('themeToggle');
    if (btn) btn.textContent = isDark ? '☀' : '◐';
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', isDark ? '#12160F' : '#F7F4EC');
    // 图表颜色依赖主题，切换后重绘
    if (typeof weightChart !== 'undefined' && weightChart) { try { loadWeightData(); } catch (e) {} }
}

function applyDarkMode() {
    if (localStorage.getItem('nutriai_dark_mode') === '1') {
        document.documentElement.classList.add('dark');
    }
    const btn = document.getElementById('themeToggle');
    if (btn) btn.textContent = document.documentElement.classList.contains('dark') ? '☀' : '◐';
}

function escAttr(v) {
    // 用于 HTML 属性中的 JS 参数：必须同时转义双引号/单引号/&/<，否则属性会被截断
    return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

function renderSessionTags() {
    const bar = document.getElementById('sessionTagsBar');
    const chips = document.getElementById('sessionTagsChips');
    const clearBtn = document.querySelector('#sessionTagsBar .st-clear');
    if (!bar) return;
    if (sessionTags.length === 0) {
        bar.classList.remove('has-tags');
        if (clearBtn) clearBtn.style.display = 'none';
        chips.innerHTML = '<span style="font-size:11px;color:var(--muted);font-weight:500;">📌 点「便利贴」里的标签挂到这里，作为本次对话的上下文</span>';
        return;
    }
    bar.classList.add('has-tags');
    if (clearBtn) clearBtn.style.display = '';
    chips.innerHTML = sessionTags.map((t, i) => {
        const emoji = stickerEmoji(t.kind);
        const label = t.kind === 'gap' ? t.key.replace(/ · \d+%$/, '') : t.key;
        return '<span class="st-chip" draggable="true" ondragstart="onSessionTagDragStart(event, ' + i + ')">' +
            '<span class="stc-emoji">' + emoji + '</span>' +
            '<span class="stc-text">' + escHtml(label) + '</span>' +
            '<button class="stc-remove" onclick="removeSessionTag(' + i + ')">×</button>' +
            '</span>';
    }).join('');
}

function addSessionTag(kind, key) {
    if (sessionTags.some(t => t.kind === kind && t.key === key)) {
        showToast('已经挂着啦', 'info');
        return;
    }
    sessionTags.push({ kind, key });
    saveSessionTags();
    renderSessionTags();
}

function removeSessionTag(i) {
    sessionTags.splice(i, 1);
    saveSessionTags();
    renderSessionTags();
}

function clearSessionTags() {
    sessionTags = [];
    saveSessionTags();
    renderSessionTags();
}

/** 快捷标签点击 → 挂载为对话挂件（不直接发送） */
function mountQuickTag(el) {
    const kind = el.dataset.kind || 'custom';
    const key = el.dataset.key || (el.textContent || '').trim();
    if (!key) return;
    addSessionTag(kind, key);
}

/** 快捷标签拖拽开始 */
function onQuickTagDragStart(e) {
    const el = e.currentTarget;
    e.dataTransfer.setData('kind', el.dataset.kind || 'custom');
    e.dataTransfer.setData('key', el.dataset.key || (el.textContent || '').trim());
    e.dataTransfer.setData('source', 'quicktag');
    e.dataTransfer.effectAllowed = 'copy';
    el.classList.add('dragging');
}

/** 对话区 / 输入框的拖放目标：拖到对话栏直接发送，拖到输入框则挂载 */
function setupChatDropzones() {
    const msgs = document.getElementById('chatMessages');
    const input = document.getElementById('chatInput');
    if (msgs && !msgs._chatDropInit) {
        msgs._chatDropInit = true;
        msgs.addEventListener('dragover', e => { e.preventDefault(); msgs.classList.add('chat-drop-over'); });
        msgs.addEventListener('dragleave', () => msgs.classList.remove('chat-drop-over'));
        msgs.addEventListener('drop', e => {
            e.preventDefault();
            msgs.classList.remove('chat-drop-over');
            const kind = e.dataTransfer.getData('kind');
            const key = e.dataTransfer.getData('key');
            const source = e.dataTransfer.getData('source');
            if (kind && key) {
                sendQuick(stickerToPayload({ kind, key }));
            } else if (source === 'quicktag' && key) {
                sendQuick(key);
            }
        });
    }
    if (input && !input._chatDropInit) {
        input._chatDropInit = true;
        input.addEventListener('dragover', e => { e.preventDefault(); input.classList.add('input-drop-over'); });
        input.addEventListener('dragleave', () => input.classList.remove('input-drop-over'));
        input.addEventListener('drop', e => {
            e.preventDefault();
            input.classList.remove('input-drop-over');
            const kind = e.dataTransfer.getData('kind');
            const key = e.dataTransfer.getData('key');
            if (kind && key) addSessionTag(kind, key);
        });
    }
}

function onSessionTagDragStart(e, i) {
    const t = sessionTags[i];
    e.dataTransfer.setData('kind', t.kind);
    e.dataTransfer.setData('key', t.key);
    e.dataTransfer.setData('source', 'session');
}

function setupSessionTagsDropzone() {
    const bar = document.getElementById('sessionTagsBar');
    if (!bar || bar._dropInit) return;
    bar._dropInit = true;
    bar.addEventListener('dragover', e => {
        e.preventDefault();
        bar.classList.add('rail-drop-over');
    });
    bar.addEventListener('dragleave', e => bar.classList.remove('rail-drop-over'));
    bar.addEventListener('drop', e => {
        e.preventDefault();
        bar.classList.remove('rail-drop-over');
        const kind = e.dataTransfer.getData('kind');
        const key = e.dataTransfer.getData('key');
        const source = e.dataTransfer.getData('source');
        if (kind && key) {
            if (source === 'session') return;
            addSessionTag(kind, key);
        }
    });
}

function loadPicks() {
    try { return JSON.parse(localStorage.getItem(PICKS_KEY)) || { likes: [], dislikes: [], fridge: [], gap: [], custom: [] }; }
    catch { return { likes: [], dislikes: [], fridge: [], gap: [], custom: [] }; }
}
let railSyncTimer = null;
function savePicks(picks) {
    localStorage.setItem(PICKS_KEY, JSON.stringify(picks));
    // 登录后自动同步到后端（跨设备），防抖避免频繁请求
    if (currentUser) {
        clearTimeout(railSyncTimer);
        railSyncTimer = setTimeout(() => pushRailToServer(picks), 800);
    }
}

async function pushRailToServer(picks) {
    try {
        await fetch(API + '/assistant/rail', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(picks)
        });
    } catch (e) { /* 本地已保存，后端失败静默降级 */ }
}

async function syncRailFromServer() {
    if (!currentUser) return;
    try {
        const res = await fetch(API + '/assistant/rail');
        const data = await res.json();
        if (data.code === 200 && data.data) {
            const server = data.data;
            const KEYS = ['likes', 'dislikes', 'fridge', 'gap', 'custom'];
            KEYS.forEach(k => { if (!Array.isArray(server[k])) server[k] = []; });
            const local = loadPicks();
            const serverEmpty = KEYS.every(k => (server[k] || []).length === 0);
            const localEmpty = KEYS.every(k => (local[k] || []).length === 0);
            if (serverEmpty && !localEmpty) {
                // 后端还没有数据，把本地搬上去
                savePicks(local);
            } else if (!serverEmpty) {
                localStorage.setItem(PICKS_KEY, JSON.stringify(server));
            }
            refreshStickers();
        }
    } catch (e) { /* 离线或未登录，保持本地 */ }
}

function stickerToPayload(st) {
    if (st.kind === 'likes') return '我喜欢 ' + st.key;
    if (st.kind === 'dislikes') return '我不喜欢 ' + st.key;
    if (st.kind === 'fridge') return '冰箱里有 ' + st.key;
    if (st.kind === 'gap') return '营养缺口：' + st.key + '，给个建议';
    if (st.kind === 'custom') return st.key;
    return st.key;
}

function stickerEmoji(kind) {
    return kind === 'likes' ? '💚'
         : kind === 'dislikes' ? '🚫'
         : kind === 'fridge' ? '🧊'
         : kind === 'gap' ? '🎯'
         : '📌';
}

function formatReplyText(text, isPartial) {
    if (!text) return '';

    // 流式阶段：逐字输出时文本不完整，不能用 marked 解析（会炸）
    if (isPartial) return formatTextSimple(text);

    // 完整阶段：用 marked 全量渲染（renderMarkdown 内部已做 XSS 清洗）
    try {
        let md = renderMarkdown(text);
        md = md.replace(/<table>/g, '<div class="md-table-wrap"><table class="md-table">');
        md = md.replace(/<\/table>/g, '</table></div>');
        md = md.replace(/<blockquote>/g, '<blockquote class="md-quote">');
        md = md.replace(/<pre><code/g, '<pre class="md-pre"><code');
        md = md.replace(/<code>/g, '<code class="md-code">');
        md = md.replace(/<hr/g, '<hr class="md-hr"');
        md = md.replace(/<h1/g, '<h1 class="md-h1"');
        md = md.replace(/<h2/g, '<h2 class="md-h2"');
        md = md.replace(/<h3/g, '<h3 class="md-h3"');
        md = md.replace(/<ul>/g, '<ul class="md-list">');
        md = md.replace(/<ol>/g, '<ol class="md-list md-ol">');
        return md;
    } catch {
        return formatTextSimple(text);
    }
}

/** 简单渲染：逐个换行处理，不会因不完整 markdown 炸掉 */
function formatTextSimple(text) {
    const escaped = escHtml(text);
    let html = escaped
        .replace(/\*\*(.+?)\*\*/g, '<span class="md-strong">$1</span>')
        .replace(/\*(?!\*)(.+?)\*/g, '<span class="md-em">$1</span>')
        .replace(/`([^`]+)`/g, '<code class="md-code">$1</code>');

    const lines = html.split(/\r?\n/);
    let result = '';
    let inCode = false;
    let codeBuf = [];

    for (let i = 0; i < lines.length; i++) {
        const raw = lines[i];
        const line = raw.trim();

        if (line.startsWith('```')) {
            if (inCode) {
                result += '<pre class="md-pre"><code>' + codeBuf.join('\n') + '</code></pre>';
                codeBuf = [];
                inCode = false;
            } else {
                inCode = true;
            }
            continue;
        }
        if (inCode) { codeBuf.push(raw); continue; }
        if (!line) { result += '<div class="bubble-gap"></div>'; continue; }

        if (/^###\s/.test(line)) { result += '<div class="md-h3">' + line.replace(/^###\s+/, '') + '</div>'; continue; }
        if (/^##\s/.test(line)) { result += '<div class="md-h2">' + line.replace(/^##\s+/, '') + '</div>'; continue; }
        if (/^>\s/.test(line)) { result += '<blockquote class="md-quote">' + line.replace(/^>\s+/, '') + '</blockquote>'; continue; }
        if (/^[\u{1F300}-\u{1FAFF}]/u.test(line) && line.length < 50) { result += '<div class="bubble-sub">' + line + '</div>'; continue; }
        if (/^[·\-•]\s/.test(line)) { result += '<div class="bubble-item">' + line.replace(/^[·\-•]\s/, '') + '</div>'; continue; }
        result += '<p class="bubble-line">' + line + '</p>';
    }
    if (inCode && codeBuf.length) {
        result += '<pre class="md-pre"><code>' + codeBuf.join('\n') + '</code></pre>';
    }
    return result;
}

function onChatKey(e) {
    // 中文输入法选词时的回车不能当作发送
    if (e.isComposing || e.keyCode === 229) return;
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendChat(); }
}

function sendQuick(text) {
    const el = document.getElementById('chatInput');
    if (el) el.value = text;
    sendChat();
}

let chatSending = false;

function sendChat() {
    const input = document.getElementById('chatInput');
    if (!input) return;
    let text = (input.value || '').trim();
    // 挂了功能挂件（快捷标签）时，输入框为空也允许发送，用挂件内容拼成一句话
    if (!text && sessionTags.length > 0) {
        text = sessionTags.map(t => stickerToPayload(t)).join('；');
    }
    if (!text) return;
    if (chatSending) { showToast('上一条还在回复中，请稍候…', 'info'); return; }
    if (!currentUser) {
        showToast('请先登录后再和 AI 助手对话', 'error');
        openAuthModal('login');
        return;
    }
    chatSending = true;

    const tagCount = sessionTags.length;
    const tagPayload = sessionTags.map(t => ({ kind: t.kind, name: t.key }));

    appendBubble('user', text + (tagCount > 0 ? ' 🏷️×' + tagCount : ''));
    input.value = '';
    // 一次对话：挂载发出后自动清空，避免下一轮重复携带
    clearSessionTags();
    conversationMemory.push({ role: 'user', content: text });
    typingBubble(true);
    const typingTimer = setTimeout(() => typingBubble(false), 30000);

    // 优先走流式；不支持 ReadableStream 的浏览器降级走普通接口
    if (window.ReadableStream) {
        askAssistantStream(text, tagPayload).then(r => {
            clearTimeout(typingTimer);
            typingBubble(false);
            conversationMemory.push({ role: 'assistant', content: r.reply || '' });
            if (conversationMemory.length > 20) conversationMemory = conversationMemory.slice(-20);
            if (r.data && r.data.recipes && r.data.recipes.length) {
                r.data.recipes.forEach(rc => {
                    if (!recommendedRecipes.find(x => x.name === rc.name)) {
                        recommendedRecipes.push({ name: rc.name, cnt: 1, calories: estimateRecipeKcal(rc) });
                    } else {
                        recommendedRecipes.find(x => x.name === rc.name).cnt++;
                    }
                });
            }
            // 流式AI回复文本中提取食谱名，避免重复推荐
            if (r.reply && (!r.data || !r.data.recipes || !r.data.recipes.length)) {
                const extracted = extractRecipeNames(r.reply);
                extracted.forEach(name => {
                    if (!recommendedRecipes.find(x => x.name === name)) {
                        recommendedRecipes.push({ name: name, cnt: 1 });
                    }
                });
            }
            refreshStickers();
            refreshBoard();
        }).catch(err => {
            clearTimeout(typingTimer);
            typingBubble(false);
            const msg = err.message || '';
            if (msg.includes('401') || msg.includes('请先登录')) {
                appendBubble('ai', '需要先登录才能和 AI 聊天哦 🙋');
            } else {
                appendBubble('ai', '<span class="err-text">哎呀，连接失败了 🤪 稍后再试试~</span>');
            }
        }).finally(() => { chatSending = false; });
    } else {
        // 降级：传统非流式
        askAssistant(text, tagPayload).then(r => {
            clearTimeout(typingTimer);
            typingBubble(false);
            renderAssistantReply(r);
            conversationMemory.push({ role: 'assistant', content: r.reply || '' });
            if (conversationMemory.length > 20) conversationMemory = conversationMemory.slice(-20);
            if (r.data && r.data.recipes && r.data.recipes.length) {
                r.data.recipes.forEach(rc => {
                    if (!recommendedRecipes.find(x => x.name === rc.name)) {
                        recommendedRecipes.push({ name: rc.name, cnt: 1, calories: estimateRecipeKcal(rc) });
                    } else {
                        recommendedRecipes.find(x => x.name === rc.name).cnt++;
                    }
                });
            }
            refreshStickers();
            refreshBoard();
        }).catch(err => {
            clearTimeout(typingTimer);
            typingBubble(false);
            const msg = err.message || '';
            if (msg.includes('401') || msg.includes('请先登录')) {
                appendBubble('ai', '需要先登录才能和 AI 聊天哦 🙋');
            } else {
                appendBubble('ai', '<span class="err-text">哎呀，连接失败了 🤪 稍后再试试~</span>');
            }
        }).finally(() => { chatSending = false; });
    }
}

/** 流式 SSE 对话 —— 逐 token 渲染，体验大幅提升 */
async function askAssistantStream(text, emphasizeTags) {
    const body = { message: text };
    if (emphasizeTags && emphasizeTags.length > 0) body.emphasizeTags = emphasizeTags;
    if (conversationMemory.length > 0) body.history = conversationMemory.map(m => ({ role: m.role, content: m.content }));
    if (recommendedRecipes.length > 0) body.suggestedRecipes = recommendedRecipes;

    let res;
    try {
        res = await fetch(API + '/assistant/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
    } catch (fetchErr) {
        // 网络完全不通 → 降级显示错误
        const errMsg = '🤕 网络连接失败，请检查网络后重试';
        appendBubble('ai', '<span class="err-text">' + errMsg + '</span>');
        conversationMemory.push({ role: 'assistant', content: errMsg });
        if (conversationMemory.length > 20) conversationMemory = conversationMemory.slice(-20);
        return { reply: errMsg, data: null };
    }

    if (!res.ok) {
        const errMsg = '🤕 服务异常 (HTTP ' + res.status + ')，请稍后重试';
        appendBubble('ai', '<span class="err-text">' + errMsg + '</span>');
        conversationMemory.push({ role: 'assistant', content: errMsg });
        if (conversationMemory.length > 20) conversationMemory = conversationMemory.slice(-20);
        return { reply: errMsg, data: null };
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let fullReply = '';
    let bubbleEl = null;
    let metadata = null;  // 意图匹配一次性返回时，含 actions / data / tips
    let buffer = '';
    let streamError = null;

    const wrap = document.getElementById('chatMessages');

    try {
        let sseBuffer = '';
        let firstToken = true;
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            sseBuffer += decoder.decode(value, { stream: true });
            // SSE 以空行分帧；兼容 \r\n\r\n
            let sepIdx;
            while ((sepIdx = sseBuffer.search(/\r?\n\r?\n/)) >= 0) {
                const frame = sseBuffer.slice(0, sepIdx);
                sseBuffer = sseBuffer.slice(sepIdx + sseBuffer.match(/\r?\n\r?\n/)[0].length);

                const dataLines = [];
                for (const line of frame.split(/\r?\n/)) {
                    if (!line.startsWith('data:')) continue;
                    dataLines.push(line.slice(5).replace(/^ /, ''));
                }
                if (dataLines.length === 0) continue;
                const payload = dataLines.join('\n');
                if (payload === '[DONE]') continue;
                if (!payload) continue;

                try {
                    const json = JSON.parse(payload);
                    if (json.token !== undefined && json.token !== null) {
                        // AI 流式 token — 简易渲染避免不完整 markdown 炸掉
                        fullReply += json.token;
                        if (!bubbleEl) bubbleEl = appendBubbleStream('ai');
                        if (firstToken) {
                            typingBubble(false);
                            firstToken = false;
                        }
                        bubbleEl.querySelector('.bubble-body').innerHTML = formatReplyText(fullReply, true);
                        if (wrap) wrap.scrollTop = wrap.scrollHeight;
                    } else if (json.reply !== undefined) {
                        // 意图匹配一次性返回
                        fullReply = json.reply || '';
                        metadata = json;
                    }
                } catch (e) {
                    // 非 JSON 行跳过
                }
            }
        }
    } catch (streamErr) {
        streamError = streamErr;
        reader.cancel().catch(() => {});
    }

    // 流式中断恢复：有部分内容就保留，加个中断提示
    if (streamError) {
        if (bubbleEl && fullReply) {
            fullReply += '\n\n⚠️ 回复连接中断，请重试或继续对话';
            const partial = formatReplyText(fullReply, false);
            bubbleEl.querySelector('.bubble-body').innerHTML = partial;
            bubbleEl.classList.remove('streaming');
            const history = loadChatHistory();
            history.push({ role: 'ai', html: partial });
            saveChatHistory(history);
            conversationMemory.push({ role: 'assistant', content: fullReply });
            if (conversationMemory.length > 20) conversationMemory = conversationMemory.slice(-20);
            return { reply: fullReply, data: null };
        } else if (!bubbleEl) {
            const errMsg = '🤕 回复中断了，再试一次？';
            appendBubble('ai', '<span class="err-text">' + errMsg + '</span>');
            conversationMemory.push({ role: 'assistant', content: errMsg });
            return { reply: errMsg, data: null };
        }
        // 有 bubbleEl 但没有 fullReply → 不处理
    }

    // 流式结束：最终渲染 & 存入历史
    if (bubbleEl) {
        const formatted = formatReplyText(fullReply, false);
        bubbleEl.querySelector('.bubble-body').innerHTML = formatted;
        bubbleEl.classList.remove('streaming');

        // 从 AI 回复中自动解析食谱卡片
        parseAndRenderRecipes(fullReply, bubbleEl);

        // 智能追问建议
        renderSmartFollowups(fullReply);

        const history = loadChatHistory();
        history.push({ role: 'ai', html: formatted });
        saveChatHistory(history);
    }

    // 意图匹配返回：渲染 actions / data / tips
    if (metadata) {
        typingBubble(false);
        if (bubbleEl) {
            // 已有流式气泡，直接填内容
            const formatted = formatReplyText(fullReply, false);
            bubbleEl.querySelector('.bubble-body').innerHTML = formatted;
            const history = loadChatHistory();
            history.push({ role: 'ai', html: formatted });
            saveChatHistory(history);
        } else {
            // 没有流式气泡（意图瞬间命中）—— 走原有渲染逻辑
            const formatted = formatReplyText(fullReply, false);
            appendBubble('ai', formatted);
        }
        if (metadata.pendingActions && metadata.pendingActions.length) {
            renderPendingActions(metadata.pendingActions);
        }
        if (metadata.actions && metadata.actions.length) {
            const card = document.createElement('div');
            card.className = 'chat-bubble ai action-card';
            let html = '<div class="bubble-head">✅ 已为你执行</div><div class="bubble-body"><ul class="action-list">';
            metadata.actions.forEach(a => html += '<li>' + escHtml(a) + '</li>');
            html += '</ul>';
            if (metadata.remainingSummary) html += '<div class="action-extra">' + escHtml(metadata.remainingSummary) + '</div>';
            html += '</div>';
            card.innerHTML = html;
            if (wrap) { wrap.appendChild(card); wrap.scrollTop = wrap.scrollHeight; }
        }
        if (metadata.data) renderAssistantData(metadata.data);
        if (metadata.tips && metadata.tips.length) {
            const el = document.createElement('div');
            el.className = 'chat-bubble ai tips-card';
            el.innerHTML = '<div class="bubble-head">💡 建议</div><div class="bubble-body">' +
                metadata.tips.map(t => '• ' + escHtml(t)).join('<br>') + '</div>';
            if (wrap) { wrap.appendChild(el); wrap.scrollTop = wrap.scrollHeight; }
        }
        // 意图匹配回复也加追问
        renderSmartFollowups(fullReply);
    }

    return { reply: fullReply, data: metadata ? metadata.data : null };
}

/** 创建一个空的 AI 气泡用于流式填充，返回气泡 DOM */
function appendBubbleStream(role) {
    const wrap = document.getElementById('chatMessages');
    if (!wrap) return null;
    const cls = role === 'user' ? 'me' : 'ai';
    const headText = role === 'user' ? (currentUser ? (currentUser.displayName || currentUser.username) : '我') : '🤖 NutriAI';
    const el = document.createElement('div');
    el.className = 'chat-bubble ' + cls + ' streaming';
    el.innerHTML = '<div class="bubble-head">' + escHtml(headText) + '</div><div class="bubble-body"><span class="stream-cursor"></span></div>';
    wrap.appendChild(el);
    wrap.scrollTop = wrap.scrollHeight;
    return el;
}

/** 传统非流式对话（降级用） */
async function askAssistant(text, emphasizeTags) {
    const body = { message: text };
    if (emphasizeTags && emphasizeTags.length > 0) body.emphasizeTags = emphasizeTags;
    if (conversationMemory.length > 0) body.history = conversationMemory.map(m => ({ role: m.role, content: m.content }));
    if (recommendedRecipes.length > 0) body.suggestedRecipes = recommendedRecipes;
    const res = await fetch(API + '/assistant/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const j = await res.json();
    if (j.code !== 200) throw new Error(j.message || 'assistant error');
    return j.data;
}

function appendBubble(role, body) {
    const wrap = document.getElementById('chatMessages');
    if (!wrap) return;
    const cls = role === 'user' ? 'me' : 'ai';
    const headText = role === 'user' ? (currentUser ? (currentUser.displayName || currentUser.username) : '我') : '🤖 NutriAI';
    const el = document.createElement('div');
    el.className = 'chat-bubble ' + cls;
    el.innerHTML = '<div class="bubble-head">' + escHtml(headText) + '</div><div class="bubble-body"></div>';
    const bodyEl = el.querySelector('.bubble-body');
    if (role === 'user') {
        // 用户输入一律按纯文本渲染，绝不能当 HTML 执行
        bodyEl.textContent = body;
    } else {
        bodyEl.innerHTML = sanitizeHtml(body);
    }
    wrap.appendChild(el);
    wrap.scrollTop = wrap.scrollHeight;

    const history = loadChatHistory();
    history.push(role === 'user' ? { role: role, text: String(body) } : { role: role, html: body });
    saveChatHistory(history);

    return el;
}

function typingBubble(show) {
    const wrap = document.getElementById('chatMessages');
    if (!wrap) return;
    let el = document.getElementById('typingBubble');
    if (show) {
        if (el) return;
        el = document.createElement('div');
        el.id = 'typingBubble';
        el.className = 'chat-bubble ai typing';
        el.innerHTML = '<div class="bubble-head">🤖 NutriAI</div><div class="bubble-body"><span class="typing-dots"><span></span><span></span><span></span></span></div>';
        wrap.appendChild(el);
        wrap.scrollTop = wrap.scrollHeight;
    } else {
        if (el) el.remove();
    }
}

function renderAssistantReply(r) {
    if (!r) { appendBubble('ai', 'AI 助手暂时离线。'); return; }
    if (r.pendingActions && r.pendingActions.length) renderPendingActions(r.pendingActions);
    if (r.reply) {
        const formatted = formatReplyText(r.reply);
        appendBubble('ai', formatted);
    }
    if (r.data) renderAssistantData(r.data);
    if (r.tips && r.tips.length) {
        const wrap = document.getElementById('chatMessages');
        if (wrap) {
            const el = document.createElement('div');
            el.className = 'chat-bubble ai tips-card';
            el.innerHTML = '<div class="bubble-head">💡 建议</div><div class="bubble-body">' +
                r.tips.map(t => '• ' + escHtml(t)).join('<br>') + '</div>';
            wrap.appendChild(el); wrap.scrollTop = wrap.scrollHeight;
        }
    }
}

/** 渲染待确认动作卡（写入类操作必须经用户确认） */
function renderPendingActions(actions) {
    const wrap = document.getElementById('chatMessages');
    if (!wrap || !actions || !actions.length) return;
    const card = document.createElement('div');
    card.className = 'chat-bubble ai pending-card';
    let html = '<div class="bubble-head">📋 待确认</div><div class="bubble-body">';
    actions.forEach(a => {
        html += '<div class="pending-item" data-action-id="' + escAttr(a.id) + '">' +
            '<span class="pending-label">' + escHtml(a.label || a.type) + '</span>' +
            '<button type="button" class="btn-confirm" onclick=\'confirmPendingAction(' +
            escAttr(JSON.stringify(a)) + ')\'>' + escHtml(a.confirmText || '确认') + '</button>' +
            '</div>';
    });
    html += '</div>';
    card.innerHTML = html;
    wrap.appendChild(card);
    wrap.scrollTop = wrap.scrollHeight;
}

async function confirmPendingAction(actionData) {
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    const item = document.querySelector('.pending-item[data-action-id="' + actionData.id + '"]');
    if (item) {
        const btn = item.querySelector('.btn-confirm');
        if (btn) { btn.disabled = true; btn.textContent = '处理中...'; }
    }
    try {
        const headers = { 'Content-Type': 'application/json' };
        let body = actionData.payload;
        if (actionData.method === 'DELETE') body = undefined;
        // 后端返回的 endpoint 可能已带 /api 前缀，避免拼成 /api/api/...
        const endpoint = actionData.endpoint || '';
        const url = endpoint.startsWith('/api/') || endpoint.startsWith(API + '/')
            ? endpoint
            : API + endpoint;
        const res = await fetch(url, {
            method: actionData.method || 'POST',
            headers: body ? headers : undefined,
            body: body ? JSON.stringify(body) : undefined
        });
        const data = await res.json();
        if (data.code === 200) {
            if (item) {
                const labelSpan = item.querySelector('.pending-label');
                const btnEl = item.querySelector('.btn-confirm');
                if (labelSpan) labelSpan.textContent = '✅ ' + (labelSpan.textContent || '');
                if (btnEl) { btnEl.textContent = '已确认'; btnEl.style.opacity = '0.6'; }
            }
            showToast('已确认并保存', 'success');
            refreshBoard();
        } else {
            if (item) {
                const btn = item.querySelector('.btn-confirm');
                if (btn) { btn.disabled = false; btn.textContent = actionData.confirmText || '确认'; }
            }
            showToast(data.message || '操作失败', 'error');
        }
    } catch (e) {
        if (item) {
            const btn = item.querySelector('.btn-confirm');
            if (btn) { btn.disabled = false; btn.textContent = actionData.confirmText || '确认'; }
        }
        showToast('网络错误', 'error');
    }
}

function renderAssistantData(d) {
    const wrap = document.getElementById('chatMessages');
    if (!wrap) return;
    const el = document.createElement('div');
    el.className = 'chat-bubble ai data-card';
    let html = '';
    if (d.recipes && d.recipes.length) {
        html += '<div class="data-title">🍽️ 为你生成的菜</div>';
        d.recipes.forEach((r, idx) => {
            aiRecipeCache.push(r);
            const ri = aiRecipeCache.length - 1;
            const estKcal = estimateRecipeKcal(r);
            const mealMap = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' };
            const mealTag = mealMap[r.mealType || r.type] || '';
            const nutri = (r.nutritionalInfo && typeof r.nutritionalInfo === 'object') ? r.nutritionalInfo : {};
            const nutriParts = [];
            if (nutri.calories != null) nutriParts.push('热量 ' + nutri.calories + ' kcal');
            if (nutri.protein != null) nutriParts.push('蛋白 ' + nutri.protein + ' g');
            if (nutri.carbs != null) nutriParts.push('碳水 ' + nutri.carbs + ' g');
            if (nutri.fat != null) nutriParts.push('脂肪 ' + nutri.fat + ' g');
            const nutriText = r.nutritionNote || nutriParts.join(' · ');
            const stepsText = Array.isArray(r.steps) ? r.steps.join('；') : (r.method || '');
            html += '<div class="data-recipe" onclick="toggleRecipeDetail(this)" draggable="true" ondragstart="onRecipeDragStart(event,' + ri + ')" title="点开看详情 · 可拖到左侧便利贴或对话挂载">' +
                '<div class="dr-head">' +
                    '<b class="dr-name">' + escHtml(r.name || '') + '</b>' +
                    (mealTag ? '<span class="dr-meal">' + mealTag + '</span>' : '') +
                    (estKcal ? '<span class="dr-kcal">~' + estKcal + ' kcal</span>' : '') +
                    '<span class="dr-expand">▾</span>' +
                '</div>' +
                '<div class="dr-body">' +
                    '<div class="dr-actions">' +
                        '<button class="fav-btn" onclick="event.stopPropagation();favoriteRecipe(' + ri + ')" title="收藏 ❤️">❤️ 收藏</button>' +
                        '<button class="fav-btn book-btn" onclick="event.stopPropagation();addRecipeToBook(' + ri + ')" title="加入食谱本">📖 加入食谱本</button>' +
                        '<button class="fav-btn diary-btn" onclick="event.stopPropagation();addRecipeToDiary(' + ri + ')" title="计入食物日记">🥗 计入食物日记</button>' +
                    '</div>' +
                    (r.description ? '<div class="why">💡 ' + escHtml(r.description) + '</div>' : '') +
                    (r.ingredients && r.ingredients.length ? '<div class="ings"><span class="dr-label">🥬 食材：</span>' +
                        r.ingredients.map(i => '<span class="chip">' + escHtml(i) + '</span>').join('') + '</div>' : '') +
                    (stepsText ? '<div class="dr-method"><span class="dr-label">👨‍🍳 做法：</span>' + escHtml(stepsText) + '</div>' : '') +
                    (nutriText ? '<div class="dr-nutrition"><span class="dr-label">📊 营养：</span>' + escHtml(nutriText) + '</div>' : '') +
                    (r.tags && r.tags.length ? '<div class="ings"><span class="dr-label">🏷️ 标签：</span>' +
                        r.tags.map(t => '<span class="chip">' + escHtml(t) + '</span>').join('') + '</div>' : '') +
                '</div>' +
                '</div>';
        });
    } else if (d.avgCalories != null) {
        html += '<div class="data-title">📊 近 7 日营养</div>';
        const fmt = (v) => v == null ? '—' : Math.round(v);
        html += '<div class="data-gap">' +
            '热量 ' + fmt(d.avgCalories) + ' kcal · ' +
            '蛋白质 ' + fmt(d.avgProtein) + ' g · ' +
            '碳水 ' + fmt(d.avgCarbs) + ' g · ' +
            '脂肪 ' + fmt(d.avgFat) + ' g</div>';
        if (d.aiInsight) html += '<div class="why">🤖 ' + escHtml(d.aiInsight) + '</div>';
    } else if (d.remainingSummary) {
        html += '<div class="data-title">🎯 今日反馈</div>';
        html += '<div class="why">' + escHtml(d.remainingSummary) + '</div>';
    }
    if (!html) return;
    el.innerHTML = html;
    wrap.appendChild(el); wrap.scrollTop = wrap.scrollHeight;
}

function onRecipeDragStart(e, idx) {
    const r = aiRecipeCache[idx];
    if (!r) return;
    e.dataTransfer.setData('kind', 'custom');
    e.dataTransfer.setData('key', '🍳 食谱「' + (r.name || '') + '」');
    e.dataTransfer.setData('source', 'recipe');
    e.dataTransfer.effectAllowed = 'copy';
    if (e.currentTarget) e.currentTarget.classList.add('dragging');
}

async function favoriteRecipe(idx) {
    const recipe = aiRecipeCache[idx];
    if (!recipe) { showToast('食谱数据已失效，请重新生成', 'error'); return; }
    if (!currentUser) { showToast('请先登录才能收藏', 'error'); return; }
    try {
        const res = await apiFetch(API + '/favorite-recipes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                recipeName: recipe.name || '未命名食谱',
                recipeJson: JSON.stringify(recipe),
                summary: recipe.description || ''
            })
        });
        const j = await readJson(res);
        if (j.code === 200) showToast('❤️ 已收藏「' + (recipe.name || '') + '」', 'success');
        else showToast(j.message || '收藏失败', 'error');
    } catch (e) {
        showToast('收藏失败：' + e.message, 'error');
    }
}

async function addRecipeToBook(idx) {
    const recipe = aiRecipeCache[idx];
    if (!recipe) { showToast('食谱数据已失效，请重新生成', 'error'); return; }
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    try {
        const nutri = (recipe.nutritionalInfo && typeof recipe.nutritionalInfo === 'object') ? recipe.nutritionalInfo : {};
        const payload = {
            name: recipe.name || '未命名食谱',
            description: recipe.description || '',
            mealType: recipe.mealType || recipe.type || 'lunch',
            ingredientsText: Array.isArray(recipe.ingredients) ? recipe.ingredients.join('\n') : (recipe.ingredients || ''),
            stepsText: Array.isArray(recipe.steps) ? recipe.steps.join('\n') : (recipe.method || recipe.steps || ''),
            calories: nutri.calories != null ? nutri.calories : (recipe.calories != null ? recipe.calories : null),
            proteinGrams: nutri.protein != null ? nutri.protein : (recipe.proteinGrams != null ? recipe.proteinGrams : null),
            carbsGrams: nutri.carbs != null ? nutri.carbs : (recipe.carbsGrams != null ? recipe.carbsGrams : null),
            fatGrams: nutri.fat != null ? nutri.fat : (recipe.fatGrams != null ? recipe.fatGrams : null),
            tags: Array.isArray(recipe.tags) ? recipe.tags.join(',') : (recipe.tags || '')
        };
        const res = await apiFetch(API + '/recipe-book', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await readJson(res);
        if (data.code === 200) showToast('📖 已加入食谱本', 'success');
        else showToast(data.message || '保存失败', 'error');
    } catch (e) {
        showToast('保存失败：' + e.message, 'error');
    }
}

async function addRecipeToDiary(idx) {
    const recipe = aiRecipeCache[idx];
    if (!recipe) { showToast('食谱数据已失效，请重新生成', 'error'); return; }
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    try {
        const nutri = (recipe.nutritionalInfo && typeof recipe.nutritionalInfo === 'object') ? recipe.nutritionalInfo : {};
        const mealMap = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' };
        const payload = {
            foodName: recipe.name || '未命名食谱',
            mealType: mealMap[recipe.mealType || recipe.type] || '午餐',
            calories: nutri.calories != null ? nutri.calories : estimateRecipeKcal(recipe),
            protein: nutri.protein != null ? nutri.protein : null,
            carbs: nutri.carbs != null ? nutri.carbs : null,
            fat: nutri.fat != null ? nutri.fat : null,
            recordDate: localToday()
        };
        const res = await apiFetch(API + '/food-diary', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await readJson(res);
        if (data.code === 200) showToast('🥗 已计入食物日记', 'success');
        else showToast(data.message || '计入失败', 'error');
    } catch (e) {
        showToast('计入失败：' + e.message, 'error');
    }
}

function chatVoice() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) { showToast('当前浏览器不支持语音，请用 Chrome/Edge', 'error'); return; }

    if (voiceRec) { stopVoiceRecording(); return; }

    const rec = new SR();
    rec.lang = 'zh-CN';
    rec.continuous = false;
    rec.interimResults = false;
    voiceRec = rec;

    const vr = document.getElementById('voiceRecording');
    const micBtn = document.getElementById('btnMic');
    if (vr) vr.style.display = 'flex';
    if (micBtn) micBtn.style.background = 'linear-gradient(135deg,#ef4444,#dc2626)';

    let timeoutId = setTimeout(() => {
        try { rec.stop(); } catch (e) {}
        showToast('聆听超时，请再试一次', 'warning');
    }, 12000);

    rec.onresult = e => {
        clearTimeout(timeoutId);
        const text = ((e.results[0] && e.results[0][0] && e.results[0][0].transcript) || '').trim();
        resetVoiceUI();
        if (!text) { showToast('没听清，请再试一次', 'warning'); return; }
        const input = document.getElementById('chatInput');
        if (input) input.value = text;
        sendChat();
    };

    rec.onerror = (e) => {
        clearTimeout(timeoutId);
        resetVoiceUI();
        if (e.error === 'aborted') return;
        showToast(speechErrorText(e.error), 'error');
    };

    rec.onend = () => {
        clearTimeout(timeoutId);
        resetVoiceUI();
    };

    rec.start();
}

function stopVoiceRecording() {
    if (voiceRec) {
        try { voiceRec.stop(); } catch (e) {}
        voiceRec = null;
    }
    resetVoiceUI();
}

function resetVoiceUI() {
    voiceRec = null;
    const vr = document.getElementById('voiceRecording');
    const micBtn = document.getElementById('btnMic');
    if (vr) vr.style.display = 'none';
    if (micBtn) micBtn.style.background = '';
}

/* ================================================================
   便利贴素材条 + 便利贴看板
   ================================================================ */
/** 点击便利贴标签 → 挂载为对话挂件（移动端无拖拽，靠点击） */
function mountRailChip(el) {
    const kind = el.dataset.kind;
    const key = el.dataset.key;
    if (!kind || !key) return;
    addSessionTag(kind, key);
    closeStickerSheet();
}

/** 移动端便利贴底部抽屉：开 / 关 */
function toggleStickerSheet() {
    const rail = document.getElementById('stickerRail');
    const backdrop = document.getElementById('railBackdrop');
    if (!rail) return;
    const open = rail.classList.toggle('open');
    if (backdrop) backdrop.classList.toggle('open', open);
    document.body.classList.toggle('sheet-open', open);
}
function closeStickerSheet() {
    const rail = document.getElementById('stickerRail');
    const backdrop = document.getElementById('railBackdrop');
    if (rail) rail.classList.remove('open');
    if (backdrop) backdrop.classList.remove('open');
    document.body.classList.remove('sheet-open');
}

function refreshStickers() {
    setupRailDropzone();

    const picks = loadPicks();
    const all = [];
    ['likes', 'dislikes', 'fridge', 'gap', 'custom'].forEach(k => {
        (picks[k] || []).forEach(item => {
            all.push({ kind: k, key: item });
        });
    });

    const chipsHtml = all.length === 0 ? '' : all.map(st => {
        const emoji = stickerEmoji(st.kind);
        const label = st.kind === 'gap' ? st.key.replace(/ · \d+%$/, '') : st.key;
        return '<div class="chip rail-chip" draggable="true"' +
            ' data-kind="' + st.kind + '" data-key="' + escHtml(st.key) + '"' +
            ' ondragstart="onRailChipDragStart(event)"' +
            ' onclick="mountRailChip(this)"' +
            ' title="点击挂载到输入框 · 拖到对话区直接发送 · 拖到输入框挂载">' +
            '<span class="rc-emoji">' + emoji + '</span>' +
            '<span class="rc-text">' + escHtml(label) + '</span>' +
            '<button class="rc-remove" onclick=\'event.stopPropagation();removeFromPicks(' + escAttr(JSON.stringify(st.kind)) + ',' + escAttr(JSON.stringify(st.key)) + ')\'>×</button>' +
            '</div>';
    }).join('');

    ['railChips', 'railChipsBoard', 'railChipsFavorites', 'railChipsRecipebook'].forEach(id => {
        const rail = document.getElementById(id);
        const empty = document.getElementById(id.replace('Chips', 'Empty'));
        if (!rail) return;
        rail.innerHTML = chipsHtml;
        if (empty) empty.style.display = all.length === 0 ? 'block' : 'none';
    });
}

function setupRailDropzone() {
    ['stickerRail', 'stickerRailBoard', 'stickerRailFavorites', 'stickerRailRecipebook'].forEach(id => {
        const rail = document.getElementById(id);
        if (!rail || rail._dropInit) return;
        rail._dropInit = true;

        rail.addEventListener('dragover', e => {
            e.preventDefault();
            rail.classList.add('rail-drop-over');
        });
        rail.addEventListener('dragleave', e => {
            rail.classList.remove('rail-drop-over');
        });
        rail.addEventListener('drop', e => {
            e.preventDefault();
            rail.classList.remove('rail-drop-over');
            const kind = e.dataTransfer.getData('kind');
            const key = e.dataTransfer.getData('key');
            if (kind && key) addToPicks(kind, key);
        });
    });
}

function onRailChipDragStart(e) {
    const chip = e.currentTarget;
    e.dataTransfer.setData('kind', chip.dataset.kind);
    e.dataTransfer.setData('key', chip.dataset.key);
    e.dataTransfer.effectAllowed = 'move';
    chip.classList.add('dragging');
}

function addToPicks(kind, key) {
    const picks = loadPicks();
    if (!picks[kind]) picks[kind] = [];
    if (!picks[kind].includes(key)) {
        picks[kind].push(key);
        savePicks(picks);
        refreshStickers();
        const label = kind === 'gap' ? key.replace(/ · \d+%$/, '') : key;
        showToast('📌 已加入便利贴：' + label, 'success');
    } else {
        showToast('已经在便利贴里啦', 'info');
    }
}

function removeFromPicks(kind, key) {
    const picks = loadPicks();
    if (picks[kind]) {
        picks[kind] = picks[kind].filter(k => k !== key);
        savePicks(picks);
        refreshStickers();
    }
}

function clearRail() {
    const picks = loadPicks();
    const total = Object.values(picks).reduce((s, arr) => s + arr.length, 0);
    if (total === 0) { showToast('已经是空的啦', 'info'); return; }
    showConfirm('清空便利贴', '确定清空所有便利贴吗？（不会删除看板里的原始数据）', () => {
        savePicks({ likes: [], dislikes: [], fridge: [], gap: [], custom: [] });
        refreshStickers();
        showToast('便利贴已清空', 'success');
    });
}

function addAllPicksToSession() {
    const picks = loadPicks();
    let added = 0;
    ['likes', 'dislikes', 'fridge', 'gap', 'custom'].forEach(k => {
        (picks[k] || []).forEach(item => {
            if (!sessionTags.some(t => t.kind === k && t.key === item)) {
                sessionTags.push({ kind: k, key: item });
                added++;
            }
        });
    });
    if (added === 0) {
        if (sessionTags.length > 0) showToast('这些都已经挂着啦', 'info');
        else showToast('便利贴还是空的', 'info');
        return;
    }
    saveSessionTags();
    renderSessionTags();
    closeStickerSheet();
    showToast('📎 已挂 ' + added + ' 个标签到对话', 'success');
}

function addCustomSticker(inputId) {
    const input = document.getElementById(inputId);
    if (!input) return;
    const val = input.value.trim();
    if (!val) { input.focus(); return; }
    addToPicks('custom', val);
    input.value = '';
    input.focus();
}

function submitCustomSticker() {
    addCustomSticker('customInput');
}

function submitCustomStickerBoard() {
    addCustomSticker('customInputBoard');
}

function dropSticker(text) {
    sendQuick(text);
}

async function refreshBoard() {
    if (!currentUser) return;
    try {
        const [res, wres] = await Promise.all([
            fetch(API + '/assistant/board'),
            fetch(API + '/weight/stats')
        ]);
        if (!res.ok) return;
        const data = await res.json();
        if (data.code !== 200 || !data.data) return;
        assistantBoardCache = data.data;
        let weightStats = null;
        if (wres.ok) {
            try { const wd = await wres.json(); if (wd && wd.code === 200) weightStats = wd.data; } catch (e) {}
        }
        renderBoard(data.data, weightStats);
    } catch (e) {}
}

/** 清洗历史脏数据：空值/问号串/替换字符统一显示为“未填” */
function cleanProfileText(value, fallback = '未填') {
    if (value == null) return fallback;
    const s = String(value).trim();
    if (!s || /^\?+$/.test(s) || /[\uFFFD]/.test(s) || s.includes('锟')) return fallback;
    return s;
}

function renderBoard(data, weightStats) {
    const likes = (data.preferences || []).filter(p => p.preference === 'LIKE');
    const dislikes = (data.preferences || []).filter(p => p.preference === 'DISLIKE');
    // /assistant/board 的 fridge 是对象数组（含数量/单位），看板只需要名称
    const fridge = (data.fridge || [])
        .map(f => (typeof f === 'string' ? f : (f && f.ingredient) ? f.ingredient : null))
        .filter(Boolean);
    const gap = data.gap;
    const personaTags = data.personaTags || [];

    const likesBox = document.getElementById('boardLikes');
    const dislikesBox = document.getElementById('boardDislikes');
    const fridgeBox = document.getElementById('boardFridge');
    const gapBox = document.getElementById('boardGap');
    const personaBox = document.getElementById('boardPersona');

    if (likesBox) {
        likesBox.innerHTML = likes.length
            ? likes.map(p => stickerBoardHtml('💚', p.foodName, 'likes', p.foodName)).join('')
            : '<div class="empty-note">还没有 —— 告诉 AI「我喜欢番茄」即可</div>';
    }
    if (dislikesBox) {
        dislikesBox.innerHTML = dislikes.length
            ? dislikes.map(p => stickerBoardHtml('🚫', p.foodName, 'dislikes', p.foodName)).join('')
            : '<div class="empty-note">还没有 —— 告诉 AI「我讨厌香菜」即可</div>';
    }
    if (fridgeBox) {
        fridgeBox.innerHTML = fridge.length
            ? fridge.map(f => stickerBoardHtml('🧊', f, 'fridge', f)).join('')
            : '<div class="empty-note">冰箱空的 —— 告诉 AI「我家有鸡蛋、西兰花」即可</div>';
    }
    if (personaBox) {
        personaBox.innerHTML = personaTags.length
            ? personaTags.map(t => stickerBoardHtml('🎭', t, 'persona', t)).join('')
            : '<div class="empty-note">还没有 —— 告诉 AI「你扮演温柔的女友」或直接点 + 添加</div>';
    }
    if (gapBox) {
        if (!gap || gap.trackedDays === 0) {
            gapBox.innerHTML = '<div class="empty-note">先去记几餐饮食，AI 才能看到缺口</div>';
        } else {
            let html = '<div class="gap-mini">近 ' + gap.trackedDays + ' 天记录</div>';
            const allGapItems = [];
            (gap.deficiencies || []).forEach(d => allGapItems.push({ nutrient: d.nutrient, issue: '偏低', percent: Math.round(d.percent), cssClass: 'gap-low' }));
            (gap.surpluses || []).forEach(s => allGapItems.push({ nutrient: s.nutrient, issue: '偏高', percent: Math.round(s.percent), cssClass: 'gap-high' }));
            if (allGapItems.length > 0) {
                html += '<div class="gap-nutrients">';
                allGapItems.forEach(g => {
                    const barWidth = Math.min(g.percent, 150);
                    const gapKey = g.nutrient + ' ' + g.issue + ' ' + g.percent + '%';
                    html += '<div class="gap-bar-row" draggable="true" ondragstart="onGapDragStart(event)" data-gap-key="' + escAttr(gapKey) + '" title="可拖到左侧便利贴或对话挂载">' +
                        '<span class="gap-bar-label">' + (g.issue === '偏低' ? '🎯' : '⚠️') + ' ' + g.nutrient + '</span>' +
                        '<span class="gap-bar-val ' + g.cssClass + '">' + g.issue + ' ' + g.percent + '%</span>' +
                        '<div class="gap-bar-track"><div class="gap-bar-fill ' + g.cssClass + '" style="width:' + barWidth + 'px"></div></div>' +
                        '</div>';
                });
                html += '</div>';
            }
            if (gap.aiInsight) {
                html += '<div class="gap-insight">💡 ' + escHtml(gap.aiInsight) + '</div>';
            }
            gapBox.innerHTML = html;
        }
    }

    // ===== 身体状态（疾病/身体状况，整合自画像页） =====
    const healthBox = document.getElementById('boardHealth');
    if (healthBox) {
        const profile = data.profile || {};
        const conds = profile.healthConditions || [];
        if (currentHealthConditions.length === 0 && conds.length > 0) currentHealthConditions = conds.slice();
        healthBox.innerHTML = conds.length
            ? conds.map(c => '<div class="sticker health-sticker"><span class="st-emoji">🩺</span><span class="st-text">' + escHtml(c) + '</span><button class="st-del" onclick=\'removeHealthCondition(' + escAttr(JSON.stringify(c)) + ')\'>×</button></div>').join('')
            : '<div class="empty-note">还没有 —— 点 + 添加疾病或身体状况（如高血压、乳糖不耐受）</div>';
    }

    // ===== 基础信息（画像数据 + 体重记录联动） =====
    const profileBox = document.getElementById('boardProfileSummary');
    if (profileBox) {
        const profile = data.profile || {};
        const upper = (v) => (v == null ? '' : String(v).trim().toUpperCase());
        const genderLabel = { MALE: '男', FEMALE: '女', OTHER: '其他' }[upper(profile.gender)] || '未填';
        const goalLabel = { LOSE_WEIGHT: '减脂', MAINTAIN: '维持', GAIN_WEIGHT: '增重', BUILD_MUSCLE: '增肌', IMPROVE_HEALTH: '改善健康' }[upper(profile.healthGoal)] || '未填';
        const activityLabel = { SEDENTARY: '久坐', LIGHT: '轻度活动', MODERATE: '中度活动', ACTIVE: '活跃', VERY_ACTIVE: '非常活跃' }[upper(profile.activityLevel)] || '未填';
        const age = profile.age;
        const stageLabel = age == null ? '未填'
            : age < 18 ? '未成年'
            : age <= 44 ? '青年'
            : age <= 59 ? '中年'
            : '老年';
        const weightVal = (weightStats && weightStats.currentWeight != null)
            ? weightStats.currentWeight + ' kg'
            : (profile.weightKg ? profile.weightKg + ' kg' : '未填');
        const fmtNum = (v, unit) => (v == null || v === '') ? '未填' : v + ' ' + unit;
        const items = [
            ['👤', '性别', genderLabel],
            ['🎂', '年龄', profile.age ? profile.age + ' 岁' : '未填'],
            ['🧒', '年龄段', stageLabel],
            ['📏', '身高', profile.heightCm ? profile.heightCm + ' cm' : '未填'],
            ['⚖️', '体重', weightVal],
            ['🎯', '目标', goalLabel],
            ['🏃', '活动水平', activityLabel],
            ['📍', '所在地区', cleanProfileText(profile.region)],
            ['🔥', '每日热量', fmtNum(profile.dailyCalorieGoal, 'kcal')],
            ['🍗', '蛋白质目标', fmtNum(profile.proteinGoal, 'g')],
            ['🍚', '碳水目标', fmtNum(profile.carbsGoal, 'g')],
            ['🥑', '脂肪目标', fmtNum(profile.fatGoal, 'g')]
        ];
        profileBox.innerHTML = items.map(it =>
            '<div class="ps-item"><span class="ps-emoji">' + it[0] + '</span>' +
            '<span class="ps-label">' + it[1] + '</span>' +
            '<span class="ps-val">' + escHtml(it[2]) + '</span></div>'
        ).join('');
        const incomplete = !upper(profile.gender) || !upper(profile.healthGoal) || !upper(profile.activityLevel)
            || cleanProfileText(profile.region, '') === '';
        if (incomplete) {
            profileBox.insertAdjacentHTML('beforeend',
                '<button type="button" class="board-complete-btn" onclick="switchMainTab(\'profile\')">完善画像，让推荐更准确 →</button>');
        }
    }

    const countLikes = document.getElementById('countLikes');
    const countDislikes = document.getElementById('countDislikes');
    const countFridge = document.getElementById('countFridge');
    const countPersona = document.getElementById('countPersona');
    if (countLikes) countLikes.textContent = likes.length > 0 ? '· ' + likes.length + ' 个' : '';
    if (countDislikes) countDislikes.textContent = dislikes.length > 0 ? '· ' + dislikes.length + ' 个' : '';
    if (countFridge) countFridge.textContent = fridge.length > 0 ? '· ' + fridge.length + ' 个' : '';
    if (countPersona) countPersona.textContent = personaTags.length > 0 ? '· ' + personaTags.length + ' 个' : '';
}

function stickerBoardHtml(emoji, text, kind, key) {
    let del = '';
    if ((kind === 'likes' || kind === 'dislikes') && key) {
        del = 'deletePrefByName(' + escAttr(JSON.stringify(key)) + ')';
    } else if (kind === 'fridge' && key) {
        del = 'deleteFridge(' + escAttr(JSON.stringify(key)) + ')';
    } else if (kind === 'persona' && key) {
        del = 'deletePersona(' + escAttr(JSON.stringify(key)) + ')';
    }
    return '<div class="sticker" draggable="true"' +
        ' data-kind="' + kind + '" data-key="' + escHtml(key) + '"' +
        ' ondragstart="onBoardStickerDragStart(event)"' +
        ' onclick=\'dropSticker(' + escAttr(JSON.stringify(stickerToPayload({kind, key}))) + ')\'' +
        ' title="拖到左侧便利贴 · 点一下也能直接发 AI">' +
        '<span class="st-emoji">' + emoji + '</span>' +
        '<span class="st-text">' + escHtml(text) + '</span>' +
        (del ? '<button class="st-del" onclick=\'event.stopPropagation();' + del + '\'>×</button>' : '') +
        '</div>';
}

function onBoardStickerDragStart(e) {
    const el = e.currentTarget;
    e.dataTransfer.setData('kind', el.dataset.kind);
    e.dataTransfer.setData('key', el.dataset.key);
    e.dataTransfer.effectAllowed = 'copy';
    el.classList.add('dragging');
}

function onGapDragStart(e) {
    const el = e.currentTarget;
    const key = el.dataset.gapKey;
    if (!key) return;
    e.dataTransfer.setData('kind', 'gap');
    e.dataTransfer.setData('key', key);
    e.dataTransfer.setData('source', 'board-gap');
    e.dataTransfer.effectAllowed = 'copy';
    el.classList.add('dragging');
}

/** 从 AI 回复中自动提取食谱并生成交互卡片 */
function parseAndRenderRecipes(fullText, parentBubble) {
    if (!fullText || !parentBubble) return;

    // 匹配格式：**菜名** — 描述 / **菜名**：描述（按行匹配）
    const recipeRegex = /^\s*\*\*(.+?)\*\*\s*[：:—–-]\s*(.+)$/gm;
    const recipes = [];
    let m;
    while ((m = recipeRegex.exec(fullText)) !== null) {
        const name = m[1].trim();
        const desc = m[2].trim();
        if (name.length < 2 || name.length > 20) continue;
        if (recipes.some(r => r.name === name)) continue;
        recipes.push({ name, desc });
    }
    if (recipes.length === 0) return;

    const wrap = document.getElementById('chatMessages');
    const card = document.createElement('div');
    card.className = 'chat-bubble ai recipe-parse-card';
    let html = '<div class="recipe-parse-head">🍳 检测到 ' + recipes.length + ' 道食谱</div>';
    recipes.forEach((r, i) => {
        html += '<div class="recipe-parse-item" data-index="' + i + '">' +
            '<span class="rpi-name">' + escHtml(r.name) + '</span>' +
            '<span class="rpi-desc">' + escHtml(r.desc) + '</span>' +
            '<button class="rpi-fav" data-name="' + escHtml(r.name) + '" data-desc="' + escHtml(r.desc) + '" title="收藏">⭐ 收藏</button>' +
            '</div>';
    });
    card.innerHTML = html;
    if (wrap) {
        wrap.appendChild(card);
        wrap.scrollTop = wrap.scrollHeight;
    }

    // 收藏按钮事件
    card.querySelectorAll('.rpi-fav').forEach(btn => {
        btn.addEventListener('click', async function(e) {
            e.stopPropagation();
            const name = this.dataset.name;
            const desc = this.dataset.desc || '';
            if (!name) return;
            this.disabled = true;
            const ok = await saveFavoriteRecipe({ name: name, desc: desc });
            if (ok) {
                this.textContent = '✅ 已收藏';
                this.style.opacity = '0.6';
            } else {
                this.disabled = false;
            }
        });
    });
}

/** 从 AI 回复文本中提取食谱名称（仅名称，不包含描述） */
function extractRecipeNames(fullText) {
    const recipeRegex = /^\s*\*\*(.+?)\*\*\s*[：:—–-]\s*(.+)$/gm;
    const names = [];
    let m;
    while ((m = recipeRegex.exec(fullText)) !== null) {
        const name = m[1].trim();
        if (name.length >= 2 && name.length <= 20 && !names.includes(name)) {
            names.push(name);
        }
    }
    return names;
}

/** 智能追问建议：根据 AI 回复内容生成 2-3 个上下文化追问按钮 */
function renderSmartFollowups(replyText) {
    if (!replyText) return;
    const wrap = document.getElementById('chatMessages');
    if (!wrap) return;

    const suggestions = generateFollowups(replyText);
    if (suggestions.length === 0) return;

    // 只保留最新一张追问卡，避免无限堆叠
    wrap.querySelectorAll('.followup-card').forEach(el => el.remove());

    const card = document.createElement('div');
    card.className = 'chat-bubble ai followup-card';
    let html = '<div class="followup-head">🔧 拓展一下 · 就这次回复继续问</div><div class="followup-chips">';
    suggestions.forEach(s => {
        html += '<button class="followup-chip" data-text="' + escHtml(s) + '">' + escHtml(s) + '</button>';
    });
    html += '</div>';
    card.innerHTML = html;
    wrap.appendChild(card);
    wrap.scrollTop = wrap.scrollHeight;

    // 绑定点击事件
    card.querySelectorAll('.followup-chip').forEach(btn => {
        btn.addEventListener('click', function() {
            const text = this.dataset.text;
            if (text) sendQuick(text);
        });
    });
}

/** 根据回复内容智能生成追问 */
function generateFollowups(text) {
    const rules = [
        // 食谱推荐 → 追问烹饪细节 / 换菜
        { test: /推荐|食谱|今晚吃|试试|番茄|牛腩|排骨|鸡胸|鱼|虾|豆腐|炒|炖|蒸|凉拌/i,
          suggestions: ['这道菜怎么做？具体步骤说说', '换一道更清淡的', '材料不够怎么办？'] },
        // 进度/缺口 → 追问补充方案
        { test: /缺口|不够|还差|少|缺|不足|差.*g/i,
          suggestions: ['我该吃什么来补缺口？', '今天还够吃多少热量？', '怎么搭配更均衡？'] },
        // 体重 → 追问趋势
        { test: /体重|公斤|减重|增重|减肥|增肌|BMI/i,
          suggestions: ['本周体重趋势怎么样？', '目标体重还要多久？', '调整一下饮食计划'] },
        // 冰箱 → 追问烹饪
        { test: /冰箱|食材|买了|有.*菜|库存/i,
          suggestions: ['这些食材能做什么菜？', '还缺什么营养？', '帮我推荐今晚菜单'] },
        // 饮食记录 → 追问分析
        { test: /记录|吃了|早餐|午餐|晚餐|kcal|热量/i,
          suggestions: ['今天营养达标了吗？', '今天还能吃什么？', '本周对比怎么样？'] },
        // 偏好 → 追问推荐
        { test: /喜欢|讨厌|偏好|口味|辣|清淡/i,
          suggestions: ['根据我的口味推荐今晚吃什么', '有什么新口味可以试试？'] },
        // 目标 → 追问进度
        { test: /目标|减脂|增肌|维持|保持/i,
          suggestions: ['离目标还差多少？', '本周进度如何？', '调整一下目标？'] },
    ];

    for (const rule of rules) {
        if (rule.test.test(text)) {
            return rule.suggestions;
        }
    }

    // 默认追问
    return ['今天还能吃什么？', '帮我分析今日营养', '推荐今晚菜单'];
}

/** 收藏食谱到数据库（与推荐结果页共用同一套收藏表） */
async function saveFavoriteRecipe(recipe) {
    if (!recipe || !recipe.name) return false;
    if (!currentUser) { showToast('请先登录才能收藏', 'error'); return false; }
    try {
        const res = await fetch(API + '/favorite-recipes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                recipeName: recipe.name,
                recipeJson: JSON.stringify({ name: recipe.name, desc: recipe.desc || '', source: recipe.source || '' }),
                summary: recipe.desc || recipe.source || ''
            })
        });
        const j = await res.json();
        if (j.code === 200) { showToast('⭐ 已收藏「' + recipe.name + '」', 'success'); return true; }
        showToast(j.message || '收藏失败', 'error');
        return false;
    } catch (e) {
        showToast('收藏失败：' + e.message, 'error');
        return false;
    }
}

/** 把旧版本存在 localStorage 的收藏一次性搬进数据库，成功后清掉本地键 */
async function migrateLocalFavorites() {
    let list = [];
    try {
        const raw = localStorage.getItem('nutri_fav_recipes');
        if (raw) list = JSON.parse(raw) || [];
    } catch (e) { return; }
    if (!Array.isArray(list) || list.length === 0) return;
    for (const r of list) {
        if (!r || !r.name) continue;
        try {
            await fetch(API + '/favorite-recipes', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    recipeName: r.name,
                    recipeJson: JSON.stringify({ name: r.name, desc: r.source || '' }),
                    summary: r.source || ''
                })
            });
        } catch (e) {}
    }
    localStorage.removeItem('nutri_fav_recipes');
    showToast('已把 ' + list.length + ' 条本地收藏同步到账号', 'success');
}

document.addEventListener('dragend', e => {
    document.querySelectorAll('.sticker.dragging, .rail-chip.dragging').forEach(el => el.classList.remove('dragging'));
});

async function deletePrefByName(foodName) {
    try {
        const res = await fetch(API + '/food-preference?foodName=' + encodeURIComponent(foodName), { method: 'DELETE' });
        const data = await res.json();
        if (data.code === 200) {
            const picks = loadPicks();
            ['likes', 'dislikes'].forEach(k => {
                if (picks[k]) picks[k] = picks[k].filter(x => x !== foodName);
            });
            savePicks(picks);
            refreshBoard(); refreshStickers();
        }
    } catch (e) {}
}

async function deleteFridge(ingredient) {
    try {
        const res = await fetch(API + '/assistant/fridge/forget', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ingredient })
        });
        const data = await res.json();
        if (data.code === 200) {
            const picks = loadPicks();
            if (picks.fridge) picks.fridge = picks.fridge.filter(x => x !== ingredient);
            savePicks(picks);
            refreshBoard(); refreshStickers();
        }
    } catch (e) {}
}

async function clearAllFridge() {
    showConfirm('清空冰箱', '确定清空冰箱里所有食材吗？', async () => {
        try {
            const res = await fetch(API + '/assistant/fridge/forget', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mode: 'all' })
            });
            const data = await res.json();
            if (data.code === 200) {
                const picks = loadPicks();
                picks.fridge = [];
                savePicks(picks);
                refreshBoard(); refreshStickers(); showToast('冰箱已清空', 'success');
            }
        } catch (e) {}
    });
}

// ========== AI 角色设定管理 ==========

async function addPersonaTag() {
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    showPrompt('添加 AI 角色标签', '如「温柔女友风格」「用古风说话」「像严厉教练」', '', async (tag) => {
        if (!tag || !tag.trim()) return;
        try {
            const res = await fetch(API + '/ai-persona', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ tagName: tag.trim() })
            });
            const data = await res.json();
            if (data.code === 200) {
                refreshBoard();
                showToast('已添加角色标签', 'success');
            } else {
                showToast(data.message || '添加失败', 'error');
            }
        } catch (e) { showToast('网络错误', 'error'); }
    });
}

async function deletePersona(tagName) {
    try {
        await fetch(API + '/ai-persona/by-name/' + encodeURIComponent(tagName), { method: 'DELETE' });
        refreshBoard();
    } catch (e) { /* ignore */ }
}

async function clearAllPersona() {
    showConfirm('清空角色设定', '确定清空所有 AI 角色设定吗？', async () => {
        try {
            const res = await fetch(API + '/ai-persona', { method: 'DELETE' });
            const data = await res.json();
            if (data.code === 200) {
                refreshBoard();
                showToast('角色设定已清空', 'success');
            }
        } catch (e) { showToast('清空失败', 'error'); }
    });
}

// ========== 身体状态管理（整合自画像页） ==========

function removeHealthCondition(cond) {
    currentHealthConditions = currentHealthConditions.filter(c => c !== cond);
    saveHealthConditions();
}

async function saveHealthConditions() {
    try {
        const res = await fetch(API + '/user/profile');
        const data = await res.json();
        const profile = (data && data.data) ? data.data : {};
        profile.healthConditions = currentHealthConditions.slice();
        const putRes = await fetch(API + '/user/profile', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(profile)
        });
        const putData = await putRes.json();
        if (putData.code === 200) { refreshBoard(); showToast('身体状态已更新 ✅', 'success'); }
        else showToast(putData.message || '保存失败', 'error');
    } catch (e) { showToast('网络错误', 'error'); }
}

function addStickerInline(kind) {
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    const board = document.querySelector('.sticker-board[data-board="' + kind + '"]');
    if (!board) return;

    const placeholder = kind === 'likes' ? '加一道喜欢的（如番茄 / 三文鱼）…' :
                        kind === 'dislikes' ? '加一道讨厌的 / 过敏的…' :
                        kind === 'persona' ? '加角色标签（如「温柔女友风格」「用古风说话」）…' :
                        kind === 'health' ? '添加身体状态（如高血压、糖尿病、乳糖不耐受）…' :
                        '加新食材（多个用逗号分隔）…';

    if (board.classList.contains('adding')) return;
    board.classList.add('adding');

    const wrap = document.createElement('div');
    wrap.className = 'inline-add';
    wrap.innerHTML = '<input class="inline-input" placeholder="' + escAttr(placeholder) + '" onkeydown=\'inlineAddKey(event, ' + escAttr(JSON.stringify(kind)) + ')\'>'
        + '<button type="button" class="inline-btn" onclick=\'inlineAddSubmit(' + escAttr(JSON.stringify(kind)) + ')\'>✓</button>'
        + '<button type="button" class="inline-cancel" onclick=\'inlineAddCancel(' + escAttr(JSON.stringify(kind)) + ')\'>×</button>';
    board.appendChild(wrap);
    const input = wrap.querySelector('.inline-input');
    input.focus();
    // 移动端点击有延迟，等焦点稳定后再判断，避免“✓ 点了却先被移除”
    input.onblur = () => setTimeout(() => {
        if (!wrap.contains(document.activeElement)) inlineAddCancel(kind);
    }, 300);
}

function inlineAddKey(e, kind) {
    if (e.isComposing || e.keyCode === 229) return;
    if (e.key === 'Enter') { e.preventDefault(); inlineAddSubmit(kind); }
    if (e.key === 'Escape') inlineAddCancel(kind);
}

function bulkAddStickers() {
    if (!currentUser) { showToast('请先登录', 'error'); return; }
    const input = document.getElementById('bulkAddInput');
    const kindSelect = document.getElementById('bulkAddKind');
    if (!input || !kindSelect) return;
    const raw = (input.value || '').trim();
    if (!raw) return;
    const kind = kindSelect.value;
    const items = raw.split(/[,，、;\s]+/).filter(Boolean).slice(0, 20);
    if (items.length === 0) return;

    const endpointMap = { likes: 'like', dislikes: 'dislike', fridge: 'fridge/remember' };
    const endpoint = endpointMap[kind];
    if (!endpoint) return;

    const body = kind === 'fridge' ? { items: items } : { foodName: items[0], preference: kind === 'likes' ? 'LIKE' : 'DISLIKE' };
    
    if (kind === 'fridge') {
        fetch(API + '/assistant/' + endpoint, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(r => r.json()).then(d => {
            if (d.code === 200) {
                refreshBoard(); refreshStickers();
                const added = typeof d.data === 'number' ? d.data : items.length;
                showToast('✅ 已添加 ' + added + ' 件到冰箱', 'success');
                input.value = '';
            } else showToast(d.message || '失败', 'error');
        }).catch(() => showToast('网络错误', 'error'));
    } else {
        let done = 0, failed = 0;
        const pref = kind === 'likes' ? 'LIKE' : 'DISLIKE';
        const addOne = (i) => {
            if (i >= items.length) {
                refreshBoard(); refreshStickers();
                showToast('✅ 已添加 ' + done + ' 个' + (failed ? '，' + failed + ' 个失败' : ''),
                    failed ? 'info' : 'success');
                if (done > 0) input.value = '';
                return;
            }
            fetch(API + '/food-preference', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ foodName: items[i], category: 'food', preference: pref })
            }).then(async r => {
                const d = await r.json().catch(() => ({}));
                if (r.ok && d.code === 200) done++; else failed++;
                addOne(i + 1);
            }).catch(() => { failed++; addOne(i + 1); });
        };
        addOne(0);
    }
}

function inlineAddCancel(kind) {
    const board = document.querySelector('.sticker-board[data-board="' + kind + '"]');
    if (!board) return;
    board.classList.remove('adding');
    const wrap = board.querySelector('.inline-add');
    if (wrap) wrap.remove();
}

function inlineAddSubmit(kind) {
    const board = document.querySelector('.sticker-board[data-board="' + kind + '"]');
    if (!board) return;
    const input = board.querySelector('.inline-input');
    if (!input) return;
    const val = (input.value || '').trim();
    inlineAddCancel(kind);
    if (!val) return;
    if (kind === 'persona') {
        fetch(API + '/ai-persona', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ tagName: val })
        }).then(r => r.json()).then(d => {
            if (d.code === 200) { refreshBoard(); showToast('🎭 已添加角色标签', 'success'); }
            else showToast(d.message || '失败', 'error');
        });
    } else if (kind === 'health') {
        if (!currentHealthConditions.includes(val)) currentHealthConditions.push(val);
        saveHealthConditions();
    } else if (kind === 'fridge') {
        const items = val.split(/[,，、\s]+/).map(s => s.trim()).filter(Boolean);
        if (!items.length) return;
        fetch(API + '/assistant/fridge/remember', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ items })
        }).then(r => r.json()).then(() => { refreshBoard(); refreshStickers(); showToast('🧊 已记住 ' + items.length + ' 种食材', 'success'); });
    } else {
        const pref = kind === 'likes' ? 'LIKE' : 'DISLIKE';
        fetch(API + '/food-preference', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ foodName: val, preference: pref })
        }).then(r => r.json()).then(d => {
            if (d.code === 200) { refreshBoard(); refreshStickers(); showToast(kind === 'likes' ? '✅ 已加入喜欢' : '❌ 已加入厌恶', 'success'); }
            else showToast(d.message || '失败', 'error');
        });
    }
}

/** 主动建议推送：打开助手页按今日记录推一句确定性建议（同一天只推一次，不写入聊天历史） */
function loadProactiveTip() {
    if (!currentUser) return;
    const d = new Date();
    const today = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    const lastKey = 'nutriai_proactive_tip_date';
    if (localStorage.getItem(lastKey) === today) return;
    fetch(API + '/assistant/proactive-tip')
        .then(res => (res.ok ? res.json() : null))
        .then(j => {
            const tip = j && j.data && j.data.tip;
            if (!tip) return;
            const wrap = document.getElementById('chatMessages');
            if (!wrap) return;
            const el = document.createElement('div');
            el.className = 'chat-bubble ai tips-card';
            el.innerHTML = '<div class="bubble-head">💡 今日建议</div><div class="bubble-body">' + escHtml(tip) + '</div>';
            wrap.appendChild(el);
            wrap.scrollTop = wrap.scrollHeight;
            localStorage.setItem(lastKey, today);
        })
        .catch(() => {});
}

document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        applyDarkMode();
        sessionTags = loadSessionTags();
        syncRailFromServer();
        refreshStickers();
        refreshBoard();
        setupSessionTagsDropzone();
        setupChatDropzones();
        renderSessionTags();
        restoreChatHistory();
        loadProactiveTip();
    }, 500);
});

function toggleRecipeDetail(el) {
    el.classList.toggle('expanded');
    const arrow = el.querySelector('.dr-expand');
    if (arrow) arrow.textContent = el.classList.contains('expanded') ? '▴' : '▾';
}

function estimateRecipeKcal(recipe) {
    if (!recipe) return null;
    if (recipe.calories != null) return Math.round(recipe.calories);
    if (recipe.nutritionalInfo && recipe.nutritionalInfo.calories != null) return Math.round(recipe.nutritionalInfo.calories);
    if (recipe.ingredients && recipe.ingredients.length) {
        const base = recipe.ingredients.length;
        const proteinHeavy = recipe.ingredients.some(i =>
            (typeof i === 'string') && (i.includes('肉') || i.includes('鱼') || i.includes('鸡') || i.includes('蛋') || i.includes('虾'))
        );
        return proteinHeavy ? 250 + base * 30 : 150 + base * 25;
    }
    return null;
}