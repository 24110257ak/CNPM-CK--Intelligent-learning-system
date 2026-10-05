/**
 * Resilient API client for the LMS frontend.
 * - timeout + AbortController
 * - offline/network/HTTP error classification
 * - safe JSON parsing
 * - idempotent retry for GET requests only
 */
const API_BASE = window.location.origin + (window.location.pathname.startsWith('/lms') ? '/lms' : '') + '/api';
const API_TIMEOUT_MS = 15000;

class ApiError extends Error {
    constructor(message, { status = 0, code = 'UNKNOWN', details = null, cause = null } = {}) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = code;
        this.details = details;
        this.cause = cause;
    }
}

if (typeof Swal !== 'undefined') {
    window.Toast = Swal.mixin({
        toast: true, position: 'top-end', showConfirmButton: false, timer: 3200,
        timerProgressBar: true, background: '#111827', color: '#fff', iconColor: '#818cf8'
    });
}

const API = {
    async request(endpoint, options = {}) {
        const { timeout = API_TIMEOUT_MS, retries, signal: externalSignal, ...fetchOptions } = options;
        const method = String(fetchOptions.method || 'GET').toUpperCase();
        const maxRetries = Number.isInteger(retries) ? retries : (method === 'GET' ? 1 : 0);

        if (!navigator.onLine) {
            throw new ApiError('Bạn đang ngoại tuyến. Hãy kiểm tra kết nối mạng rồi thử lại.', { code: 'OFFLINE' });
        }

        for (let attempt = 0; attempt <= maxRetries; attempt++) {
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort('timeout'), timeout);
            const abortFromOutside = () => controller.abort('external');
            externalSignal?.addEventListener('abort', abortFromOutside, { once: true });

            const user = API.auth?.getUser?.();
            const headers = { 'Accept': 'application/json', ...fetchOptions.headers };
            if (user && user.userId && !headers['X-User-Id']) {
                headers['X-User-Id'] = String(user.userId);
            }
            const geminiKey = localStorage.getItem('gemini_api_key');
            if (geminiKey && !headers['X-Gemini-Api-Key']) {
                headers['X-Gemini-Api-Key'] = geminiKey.trim();
            }

            const config = {
                credentials: 'same-origin',
                headers,
                ...fetchOptions,
                signal: controller.signal
            };
            if (config.body && !(config.body instanceof FormData) && !config.headers['Content-Type']) {
                config.headers['Content-Type'] = 'application/json';
            }

            try {
                const res = await fetch(`${API_BASE}${endpoint}`, config);
                const text = await res.text();
                let data = {};
                if (text) {
                    try { data = JSON.parse(text); }
                    catch { throw new ApiError('Máy chủ trả về dữ liệu không hợp lệ.', { status: res.status, code: 'INVALID_JSON' }); }
                }

                if (!res.ok) {
                    if (res.status === 401 && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/me')) {
                        localStorage.removeItem('lms_user');
                        if (!window.location.pathname.endsWith('/auth.html')) window.location.href = 'auth.html?reason=session-expired';
                    }
                    const codes = { 400: 'BAD_REQUEST', 401: 'UNAUTHORIZED', 403: 'FORBIDDEN', 404: 'NOT_FOUND', 409: 'CONFLICT', 422: 'VALIDATION', 429: 'RATE_LIMIT', 500: 'SERVER_ERROR', 503: 'UNAVAILABLE' };
                    throw new ApiError(data.message || `Yêu cầu thất bại (HTTP ${res.status}).`, {
                        status: res.status, code: codes[res.status] || 'HTTP_ERROR', details: data
                    });
                }
                return data;
            } catch (err) {
                const aborted = controller.signal.aborted;
                const apiErr = err instanceof ApiError ? err : new ApiError(
                    aborted ? 'Yêu cầu mất quá nhiều thời gian hoặc đã bị hủy.' : 'Không thể kết nối tới máy chủ.',
                    { code: aborted ? 'ABORTED' : 'NETWORK_ERROR', cause: err }
                );
                if (attempt < maxRetries && ['NETWORK_ERROR', 'ABORTED'].includes(apiErr.code) && !externalSignal?.aborted) {
                    await new Promise(resolve => setTimeout(resolve, 350 * (attempt + 1)));
                    continue;
                }
                console.error(`[API] ${method} ${endpoint}`, apiErr);
                throw apiErr;
            } finally {
                clearTimeout(timer);
                externalSignal?.removeEventListener('abort', abortFromOutside);
            }
        }
    },

    auth: {
        async login(username, password) {
            const res = await API.request('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) });
            if (res.data) localStorage.setItem('lms_user', JSON.stringify(res.data));
            return res;
        },
        async register(username, password, fullName, email, interests, role = 'student') {
            const res = await API.request('/auth/register', { method: 'POST', body: JSON.stringify({ username, password, fullName, email, interests, role }) });
            if (res.data) localStorage.setItem('lms_user', JSON.stringify(res.data));
            return res;
        },
        async logout() { try { await API.request('/auth/logout', { method: 'POST' }); } finally { localStorage.removeItem('lms_user'); window.location.href = 'auth.html'; } },
        async me() { return API.request('/auth/me'); },
        getUser() { try { return JSON.parse(localStorage.getItem('lms_user')); } catch { return null; } },
        requireAuth() { const u=this.getUser(); if(!u){ window.location.href='auth.html'; return null; } return u; },
        requireTeacher() { const u=this.requireAuth(); if(!u)return null; const r=String(u.role||'').toUpperCase(); if(!['TEACHER','ADMIN'].includes(r)){ window.location.href='index.html'; return null; } return u; },
        isTeacher() { const u=this.getUser(); return !!u && ['TEACHER','ADMIN'].includes(String(u.role||'').toUpperCase()); }
    },
    topics: {
        list: () => API.request('/topics/list'),
        get: id => API.request(`/topics/${encodeURIComponent(id)}`),
        create: data => API.request('/topics', { method: 'POST', body: JSON.stringify(data) }),
        update: (id, data) => API.request(`/topics/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(data) }),
        delete: id => API.request(`/topics/${encodeURIComponent(id)}`, { method: 'DELETE' })
    },
    questions: {
        list: (topicId=null) => API.request(`/questions${topicId ? `?topicId=${encodeURIComponent(topicId)}` : ''}`),
        create: data => API.request('/questions', { method:'POST', body:JSON.stringify(data) }),
        update: (id,data) => API.request(`/questions/${encodeURIComponent(id)}`, { method:'PUT', body:JSON.stringify(data) }),
        delete: id => API.request(`/questions/${encodeURIComponent(id)}`, { method:'DELETE' })
    },
    discussion: {
        getComments: questionId => API.request(`/discussion/comments?questionId=${encodeURIComponent(questionId)}`),
        addComment: (questionId, content, parentCommentId = null) => API.request('/discussion/comments', { method: 'POST', body: JSON.stringify({ questionId, content, parentCommentId }) }),
        deleteComment: commentId => API.request(`/discussion/comments/${encodeURIComponent(commentId)}`, { method: 'DELETE' }),
        getCredibility: questionId => API.request(`/discussion/credibility?questionId=${encodeURIComponent(questionId)}`),
        rateQuestion: (questionId, ratingType, reportReason = null) => API.request('/discussion/rate', { method: 'POST', body: JSON.stringify({ questionId, ratingType, reportReason }) }),
        getReported: (limit = 50) => API.request(`/discussion/reported?limit=${encodeURIComponent(limit)}`)
    },
    community: {
        listPosts: (channel = null, topicId = null, limit = 30, offset = 0) => {
            const params = new URLSearchParams();
            if (channel && channel !== 'all') params.append('channel', channel);
            if (topicId) params.append('topicId', topicId);
            params.append('limit', limit);
            params.append('offset', offset);
            return API.request(`/community/posts?${params.toString()}`);
        },
        getPost: postId => API.request(`/community/posts/${encodeURIComponent(postId)}`),
        createPost: data => API.request('/community/posts', { method: 'POST', body: JSON.stringify(data) }),
        deletePost: postId => API.request(`/community/posts/${encodeURIComponent(postId)}`, { method: 'DELETE' }),
        toggleLike: postId => API.request(`/community/posts/${encodeURIComponent(postId)}/like`, { method: 'POST' }),
        listComments: postId => API.request(`/community/posts/${encodeURIComponent(postId)}/comments`),
        createComment: (postId, content) => API.request(`/community/posts/${encodeURIComponent(postId)}/comments`, { method: 'POST', body: JSON.stringify({ content }) }),
        deleteComment: (postId, commentId) => API.request(`/community/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`, { method: 'DELETE' }),
        getStats: () => API.request('/community/stats')
    },
    teacher: {
        stats: () => API.request('/teacher/stats'),
        generateQuestions: payload => API.request('/teacher/ai/generate', { method:'POST', body:JSON.stringify(payload), timeout:60000 }),
        chat: (message,context='') => API.request('/teacher/ai/chat', { method:'POST', body:JSON.stringify({message,context}), timeout:30000 }),
        suggestMisconceptions: topicName => API.request('/teacher/ai/suggest-misconceptions', { method:'POST', body:JSON.stringify({topicName}), timeout:30000 }),
        validateTopic: topicName => API.request('/teacher/ai/validate-topic', { method:'POST', body:JSON.stringify({topicName}), timeout:30000 }),
        validateMisconception: (topicName, misconception) => API.request('/teacher/ai/validate-misconception', { method:'POST', body:JSON.stringify({topicName, misconception}), timeout:30000 }),
        assistQuestion: (topicName, questionPrompt, difficulty='medium') => API.request('/teacher/ai/assist-question', { method:'POST', body:JSON.stringify({topicName, questionPrompt, difficulty}), timeout:45000 }),
        validateApiKey: apiKey => API.request('/teacher/ai/validate-key', { method:'POST', body:JSON.stringify({apiKey}), timeout:20000 })
    },
    quiz: {
        start: topicId => API.request('/quiz/start', { method:'POST', body:JSON.stringify({topicId}) }),
        submit: (sessionId,answers) => API.request('/quiz/submit', { method:'POST', body:JSON.stringify({sessionId,answers}), timeout:60000 }),
        history: () => API.request('/quiz/history'),
        session: sessionId => API.request(`/quiz/session/${encodeURIComponent(sessionId)}`)
    },
    remediation: {
        get: (topicId,misconception) => API.request(`/remediation?topicId=${encodeURIComponent(topicId)}&misconception=${encodeURIComponent(misconception||'')}`),
        submit: payload => API.request('/remediation/submit', { method:'POST', body:JSON.stringify(payload) })
    },
    chat: {
        send: (message,persona='peer_tutor',sessionId=null,context=null) => API.request('/chat/send', { method:'POST', body:JSON.stringify({message,persona,sessionId,context}), timeout:30000 }),
        history: (limit=30) => API.request(`/chat/history?limit=${Math.min(100, Math.max(1, Number(limit)||30))}`)
    }
};
