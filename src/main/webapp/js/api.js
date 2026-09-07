/**
 * ═══════════════════════════════════════════════════════════════════
 * LMS AI - API Client Module (Fetch API Wrapper)
 * Quản lý giao tiếp HTTP với Jakarta Servlet Backend
 * ═══════════════════════════════════════════════════════════════════
 */

const API_BASE = window.location.origin + (window.location.pathname.startsWith('/lms') ? '/lms' : '') + '/api';

const API = {
    async request(endpoint, options = {}) {
        const url = `${API_BASE}${endpoint}`;
        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            credentials: 'same-origin',
            ...options
        };

        try {
            const res = await fetch(url, config);
            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                if (res.status === 401 && !endpoint.includes('/auth/login') && !endpoint.includes('/auth/me')) {
                    // Phiên đăng nhập hết hạn
                    localStorage.removeItem('lms_user');
                    window.location.href = 'auth.html';
                }
                throw new Error(data.message || `Lỗi HTTP ${res.status}`);
            }

            return data;
        } catch (err) {
            console.error(`[API Error] ${endpoint}:`, err);
            throw err;
        }
    },

    auth: {
        async login(username, password) {
            const res = await API.request('/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username, password })
            });
            if (res.data) {
                localStorage.setItem('lms_user', JSON.stringify(res.data));
            }
            return res;
        },

        async register(username, password, fullName, email, interests) {
            const res = await API.request('/auth/register', {
                method: 'POST',
                body: JSON.stringify({ username, password, fullName, email, interests })
            });
            if (res.data) {
                localStorage.setItem('lms_user', JSON.stringify(res.data));
            }
            return res;
        },

        async logout() {
            try {
                await API.request('/auth/logout', { method: 'POST' });
            } finally {
                localStorage.removeItem('lms_user');
                window.location.href = 'auth.html';
            }
        },

        async me() {
            return API.request('/auth/me');
        },

        getUser() {
            try {
                return JSON.parse(localStorage.getItem('lms_user'));
            } catch (e) {
                return null;
            }
        },

        requireAuth() {
            const user = this.getUser();
            if (!user) {
                window.location.href = 'auth.html';
                return null;
            }
            return user;
        }
    },

    topics: {
        async list() {
            return API.request('/topics/list');
        },

        async get(topicId) {
            return API.request(`/topics/${topicId}`);
        }
    },

    quiz: {
        async start(topicId) {
            return API.request('/quiz/start', {
                method: 'POST',
                body: JSON.stringify({ topicId })
            });
        },

        async submit(sessionId, answers) {
            return API.request('/quiz/submit', {
                method: 'POST',
                body: JSON.stringify({ sessionId, answers })
            });
        },

        async history() {
            return API.request('/quiz/history');
        },

        async session(sessionId) {
            return API.request(`/quiz/session/${sessionId}`);
        }
    },

    chat: {
        async send(message, persona = 'peer_tutor', sessionId = null, context = null) {
            return API.request('/chat/send', {
                method: 'POST',
                body: JSON.stringify({ message, persona, sessionId, context })
            });
        },

        async history(limit = 30) {
            return API.request(`/chat/history?limit=${limit}`);
        }
    }
};
