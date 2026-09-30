/**
 * ══════════════════════════════════════════════════════════════
 * COMMUNITY FORUM & STUDY LOUNGE CONTROLLER (DISCORD / FACEBOOK STYLE)
 * ══════════════════════════════════════════════════════════════
 */

let currentUser = null;
let currentChannel = 'all';
let currentTopicId = null;
let allTopics = [];
let cachedFeedPosts = [];
let currentPostMood = null;

/**
 * Các hàm hỗ trợ mẫu nội dung học tập chuyên sâu (Code, Mẹo Né Bẫy, Thăm dò, Trích dẫn)
 */
function appendToContent(template) {
    const textarea = document.getElementById('post-content-input');
    if (!textarea) return;
    if (textarea.value.trim().length > 0) {
        textarea.value += '\n\n' + template;
    } else {
        textarea.value = template;
    }
    textarea.focus();
}

function insertCodeTemplate() {
    appendToContent('```java\n// Dán đoạn mã hoặc lỗi cần giải đáp tại đây\npublic class Solution {\n    public static void main(String[] args) {\n        \n    }\n}\n```');
}
window.insertCodeTemplate = insertCodeTemplate;

function insertTipTemplate() {
    appendToContent('> ⚠️ **Bẫy tư duy thường gặp:**\n> - ❌ Lầm tưởng: [Hiểu sai hoặc phương án bẫy phổ biến]\n> - 💡 Bản chất đúng: [Nguyên lý và lý giải chính xác]');
}
window.insertTipTemplate = insertTipTemplate;

function insertQuizTemplate() {
    appendToContent('**❓ Thử thách câu hỏi nhanh:**\n[Nội dung câu hỏi tình huống hoặc đoạn code bẫy]\n\n- [A] Phương án A\n- [B] Phương án B\n- [C] Phương án C\n- [D] Phương án D\n\n👉 *Các bạn chọn phương án nào và vì sao?*');
}
window.insertQuizTemplate = insertQuizTemplate;

function insertCitationTemplate() {
    appendToContent('> 📖 **Tài liệu tham khảo:** Giáo trình / Slide bài giảng [Tên môn], Chương [Số], Trang [Số].');
}
window.insertCitationTemplate = insertCitationTemplate;

function removeSelectedMood() {
    currentPostMood = null;
    const badge = document.getElementById('selected-mood-badge');
    if (badge) badge.classList.add('d-none');
}
window.removeSelectedMood = removeSelectedMood;


/**
 * Lấy danh sách ID các bài viết đã bị người dùng hiện tại ẩn
 */
function getHiddenPostIds() {
    if (!currentUser) return [];
    try {
        const key = `lms_hidden_posts_${currentUser.userId}`;
        const data = localStorage.getItem(key);
        return data ? JSON.parse(data) : [];
    } catch (e) {
        return [];
    }
}

function addHiddenPostId(postId) {
    if (!currentUser) return;
    const list = getHiddenPostIds();
    if (!list.includes(postId)) {
        list.push(postId);
        localStorage.setItem(`lms_hidden_posts_${currentUser.userId}`, JSON.stringify(list));
    }
}

function removeHiddenPostId(postId) {
    if (!currentUser) return;
    let list = getHiddenPostIds();
    list = list.filter(id => id !== postId);
    localStorage.setItem(`lms_hidden_posts_${currentUser.userId}`, JSON.stringify(list));
}

/**
 * Ẩn bài viết khỏi bảng tin của người dùng (giống Facebook)
 */
function handleHidePost(postId) {
    addHiddenPostId(postId);
    const post = cachedFeedPosts.find(p => p.postId === postId);
    const cardEl = document.getElementById(`post-card-${postId}`);
    if (cardEl && post) {
        cardEl.outerHTML = renderHiddenPostCard(post);
    }
}

/**
 * Hiện lại bài viết đã ẩn
 */
function handleUnhidePost(postId) {
    removeHiddenPostId(postId);
    const post = cachedFeedPosts.find(p => p.postId === postId);
    const cardEl = document.getElementById(`post-card-${postId}`);
    if (cardEl && post) {
        cardEl.outerHTML = renderPostCard(post);
    }
}

/**
 * Thẻ thông báo bài viết đã bị ẩn
 */
function renderHiddenPostCard(p) {
    return `
        <div class="card p-3 shadow-sm rounded-4 mb-3 border bg-white" id="post-card-${p.postId}">
            <div class="d-flex align-items-center justify-content-between flex-wrap gap-2">
                <div class="text-muted small d-flex align-items-center gap-2">
                    <span class="badge bg-secondary-subtle text-secondary rounded-pill px-2.5 py-1">
                        <i class="fa-regular fa-eye-slash me-1"></i>Đã ẩn
                    </span>
                    <span>Bạn đã ẩn bài viết của <strong>${escapeHtml(p.authorName || p.authorUsername)}</strong> khỏi bảng tin của bạn.</span>
                </div>
                <button class="btn btn-sm btn-outline-primary rounded-pill px-3 py-1 fw-semibold" onclick="handleUnhidePost(${p.postId})">
                    <i class="fa-solid fa-rotate-left me-1"></i>Hiện lại bài viết
                </button>
            </div>
        </div>
    `;
}

const CHANNEL_INFO = {
    all: { title: 'Tất Cả Bài Viết', desc: 'Dòng thời gian các câu hỏi, mẹo học tập và bài thảo luận mới nhất' },
    general: { title: 'Thảo Luận Chung', desc: 'Không gian tự do chia sẻ cảm nghĩ, định hướng và phương pháp học' },
    qna: { title: 'Hỏi Đáp & Trợ Giúp', desc: 'Gỡ rối bài tập khó, phân tích bẫy đề thi và giải thích kiến thức' },
    tips: { title: 'Mẹo Né Bẫy Tư Duy', desc: 'Kinh nghiệm phát hiện phương án nhiễu và củng cố lỗ hổng nhận thức' },
    showcase: { title: 'Đề Xuất Đề Thi Hay', desc: 'Chia sẻ các câu hỏi trắc nghiệm hay, tình huống thực tế và bài toán mở' }
};

document.addEventListener('DOMContentLoaded', async () => {
    // 1. Kiểm tra xác thực
    currentUser = API.auth.requireAuth();
    if (!currentUser) return;

    setupNavbar();
    setupEventListeners();

    // 2. Tải dữ liệu ban đầu
    await Promise.all([
        loadTopics(),
        loadCommunityStats(),
        loadFeed()
    ]);
});

function setupNavbar() {
    const usernameEl = document.getElementById('nav-username');
    const fullnameEl = document.getElementById('nav-fullname');
    const avatarEl = document.getElementById('user-composer-avatar');

    if (usernameEl) usernameEl.textContent = currentUser.username;
    if (fullnameEl) fullnameEl.textContent = currentUser.fullName || currentUser.username;

    if (avatarEl) {
        const initial = (currentUser.fullName || currentUser.username || 'U').charAt(0).toUpperCase();
        avatarEl.textContent = initial;
        if (API.auth.isTeacher()) {
            avatarEl.className = 'author-avatar avatar-teacher';
        }
    }

    if (API.auth.isTeacher()) {
        const teacherLink = document.getElementById('nav-teacher-link');
        if (teacherLink) teacherLink.classList.remove('d-none');
        const dropdownTeacherItem = document.getElementById('dropdown-teacher-item');
        if (dropdownTeacherItem) dropdownTeacherItem.classList.remove('d-none');
    }
}

function setupEventListeners() {
    // Đăng xuất
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', (e) => {
            e.preventDefault();
            Swal.fire({
                title: 'Đăng xuất?',
                text: 'Bạn có chắc chắn muốn rời khỏi hệ thống?',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Đăng xuất',
                cancelButtonText: 'Hủy'
            }).then((result) => {
                if (result.isConfirmed) API.auth.logout();
            });
        });
    }

    // Chuyển kênh (Discord Channels)
    document.querySelectorAll('.channel-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            document.querySelectorAll('.channel-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            currentChannel = btn.getAttribute('data-channel') || 'all';
            updateChannelHeader();
            loadFeed();
        });
    });

    // Lọc theo môn học
    const topicFilter = document.getElementById('filter-topic-select');
    if (topicFilter) {
        topicFilter.addEventListener('change', () => {
            const val = topicFilter.value;
            currentTopicId = val ? parseInt(val, 10) : null;
            loadFeed();
        });
    }

    // Nút làm mới bảng tin
    const refreshBtn = document.getElementById('btn-refresh-feed');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', () => {
            loadFeed();
            loadCommunityStats();
        });
    }

    // Mở Modal tạo bài viết (Cá nhân hóa theo người dùng & kênh)
    const openComposer = document.getElementById('btn-open-create-post');
    if (openComposer) {
        openComposer.addEventListener('click', () => {
            const modalEl = document.getElementById('createPostModal');
            if (modalEl) {
                // Cá nhân hóa thông tin tác giả và gợi ý câu hỏi thân thiện
                const authorNameEl = document.getElementById('modal-composer-author-name');
                const modalAvatarEl = document.getElementById('modal-composer-avatar');
                const postContent = document.getElementById('post-content-input');
                const authorName = (currentUser && (currentUser.fullName || currentUser.username)) || 'Bạn';
                if (authorNameEl) authorNameEl.textContent = authorName;
                if (modalAvatarEl) {
                    modalAvatarEl.textContent = authorName.charAt(0).toUpperCase();
                    if (API.auth.isTeacher()) {
                        modalAvatarEl.className = 'author-avatar avatar-teacher';
                    }
                }
                if (postContent) {
                    postContent.placeholder = `${authorName} ơi, bạn đang thắc mắc hay muốn chia sẻ điều gì về bài học hôm nay?`;
                }

                // Đồng bộ kênh theo kênh hiện tại đang xem
                const channelSelect = document.getElementById('post-channel-select');
                const channelLabel = document.getElementById('selected-channel-label');
                if (channelSelect && currentChannel !== 'all') {
                    channelSelect.value = currentChannel;
                    const matchingOpt = document.querySelector(`.channel-option[data-channel="${currentChannel}"]`);
                    if (matchingOpt && channelLabel) {
                        channelLabel.textContent = matchingOpt.textContent.trim().replace(/^#\s*/, '');
                    }
                }
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
            }
        });
    }

    // Chọn kênh từ dropdown pill của modal
    document.querySelectorAll('.channel-option').forEach(opt => {
        opt.addEventListener('click', (e) => {
            e.preventDefault();
            const ch = opt.getAttribute('data-channel');
            const hiddenInput = document.getElementById('post-channel-select');
            const labelEl = document.getElementById('selected-channel-label');
            if (hiddenInput) hiddenInput.value = ch;
            if (labelEl) labelEl.textContent = opt.textContent.trim().replace(/^#\s*/, '');
        });
    });

    // Chọn nhanh môn học (Quick topic chips)
    document.querySelectorAll('.post-quick-topic-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const topic = btn.getAttribute('data-topic');
            const input = document.getElementById('post-topic-input');
            if (input) {
                input.value = topic;
                input.focus();
            }
        });
    });

    // Chọn trạng thái học tập (Learning Mood)
    document.querySelectorAll('.mood-option').forEach(opt => {
        opt.addEventListener('click', (e) => {
            e.preventDefault();
            const mood = opt.getAttribute('data-mood');
            currentPostMood = mood;
            const badge = document.getElementById('selected-mood-badge');
            const textEl = document.getElementById('selected-mood-text');
            if (badge && textEl) {
                textEl.textContent = mood;
                badge.classList.remove('d-none');
            }
        });
    });

    // Form submit tạo bài viết
    const createForm = document.getElementById('create-post-form');
    if (createForm) {
        createForm.addEventListener('submit', handleCreatePost);
    }
}

function updateChannelHeader() {
    const titleEl = document.getElementById('current-channel-title');
    const descEl = document.getElementById('current-channel-desc');
    const info = CHANNEL_INFO[currentChannel] || CHANNEL_INFO.all;

    if (titleEl) {
        titleEl.innerHTML = `<i class="fa-solid fa-hashtag text-primary"></i>${escapeHtml(info.title)}`;
    }
    if (descEl) {
        descEl.textContent = info.desc;
    }
}

/**
 * Tải danh sách môn học để đưa vào dropdown lọc & datalist tự do
 */
async function loadTopics() {
    try {
        const res = await API.topics.list();
        allTopics = res.data || [];

        const filterSelect = document.getElementById('filter-topic-select');
        const topicDatalist = document.getElementById('post-topic-datalist');

        if (filterSelect) {
            filterSelect.innerHTML = '<option value="">-- Mọi môn học & đề thi --</option>';
        }
        if (topicDatalist) {
            topicDatalist.innerHTML = '';
        }

        allTopics.forEach(t => {
            if (filterSelect) {
                const opt = document.createElement('option');
                opt.value = t.topicId;
                opt.textContent = t.topicName;
                filterSelect.appendChild(opt);
            }
            if (topicDatalist) {
                const opt2 = document.createElement('option');
                opt2.value = t.topicName;
                topicDatalist.appendChild(opt2);
            }
        });
    } catch (err) {
        console.error('Lỗi nạp danh sách môn học:', err);
    }
}

/**
 * Tải chỉ số cộng đồng
 */
async function loadCommunityStats() {
    try {
        const res = await API.community.getStats();
        const data = res.data || {};
        const pEl = document.getElementById('stat-posts-count');
        const cEl = document.getElementById('stat-comments-count');
        const crEl = document.getElementById('stat-creators-count');

        if (pEl) pEl.textContent = data.totalPosts || 0;
        if (cEl) cEl.textContent = data.totalComments || 0;
        if (crEl) crEl.textContent = data.totalCreators || 0;
    } catch (err) {
        console.error('Lỗi nạp thống kê cộng đồng:', err);
    }
}

/**
 * Tải danh sách bài viết theo bộ lọc
 */
async function loadFeed() {
    const loadingEl = document.getElementById('posts-loading');
    const emptyEl = document.getElementById('posts-empty');
    const container = document.getElementById('posts-container');

    if (loadingEl) {
        loadingEl.classList.remove('d-none');
        loadingEl.style.display = 'block';
    }
    if (emptyEl) {
        emptyEl.classList.add('d-none');
        emptyEl.style.display = 'none';
    }
    if (container) container.innerHTML = '';

    try {
        const res = await API.community.listPosts(currentChannel, currentTopicId, 40, 0);
        const posts = res.data || [];
        cachedFeedPosts = posts;

        if (loadingEl) {
            loadingEl.classList.add('d-none');
            loadingEl.style.setProperty('display', 'none', 'important');
        }

        if (posts.length === 0) {
            if (emptyEl) {
                emptyEl.classList.remove('d-none');
                emptyEl.style.display = 'block';
            }
            return;
        }

        container.innerHTML = posts.map(renderPostCard).join('');
    } catch (err) {
        if (loadingEl) {
            loadingEl.classList.add('d-none');
            loadingEl.style.setProperty('display', 'none', 'important');
        }
        if (container) {
            container.innerHTML = `
                <div class="alert alert-danger py-3 px-4 rounded-4 small">
                    <i class="fa-solid fa-triangle-exclamation me-1"></i>Không thể tải bài thảo luận: ${escapeHtml(err.message || 'Lỗi mạng')}
                </div>
            `;
        }
    }
}

/**
 * Sinh HTML cho từng bài viết
 */
function renderPostCard(p) {
    const hiddenList = getHiddenPostIds();
    if (hiddenList.includes(p.postId)) {
        return renderHiddenPostCard(p);
    }

    const isTeacher = (p.authorRole || '').toLowerCase() === 'teacher' || (p.authorRole || '').toLowerCase() === 'admin';
    const avatarClass = isTeacher ? 'author-avatar avatar-teacher' : 'author-avatar avatar-student';
    const roleBadge = isTeacher
        ? '<span class="badge bg-indigo text-white px-2 py-1 small rounded-pill" style="background-color: #6366f1; font-size: 0.7rem;"><i class="fa-solid fa-award me-1"></i>Giảng Viên</span>'
        : '<span class="badge bg-light text-secondary border px-2 py-1 small rounded-pill" style="font-size: 0.7rem;"><i class="fa-solid fa-user-graduate me-1 text-primary"></i>Sinh Viên</span>';

    const initial = (p.authorName || p.authorUsername || 'U').charAt(0).toUpperCase();
    const timeAgo = formatTimeAgo(p.createdAt);

    let channelBadge = '';
    if (p.channel === 'qna') {
        channelBadge = '<span class="badge bg-danger-subtle text-danger border border-danger-subtle rounded-pill px-2.5 py-1 small"><i class="fa-solid fa-circle-question me-1"></i>Hỏi & Đáp</span>';
    } else if (p.channel === 'tips') {
        channelBadge = '<span class="badge bg-warning-subtle text-dark border border-warning-subtle rounded-pill px-2.5 py-1 small"><i class="fa-solid fa-lightbulb me-1 text-warning"></i>Mẹo Né Bẫy</span>';
    } else if (p.channel === 'showcase') {
        channelBadge = '<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill px-2.5 py-1 small"><i class="fa-solid fa-code-compare me-1"></i>Đề Xuất Câu Hỏi</span>';
    } else {
        channelBadge = '<span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-2.5 py-1 small"><i class="fa-solid fa-hashtag me-1"></i>Thảo Luận Chung</span>';
    }

    const topicBadge = p.topicName
        ? `<span class="badge bg-light text-dark border rounded-pill px-2.5 py-1 small"><i class="fa-solid fa-book-bookmark me-1 text-primary"></i>${escapeHtml(p.topicName)}</span>`
        : '';

    // Quyền thao tác: Chỉ tác giả mới có nút xóa bài của mình. Người khác có nút Ẩn bài viết khỏi bảng tin.
    const isOwner = currentUser && (currentUser.userId === p.userId);
    const actionBtn = isOwner ? `
        <button class="btn btn-outline-danger btn-sm border-0 rounded-circle" style="width: 32px; height: 32px; padding: 0;" title="Xóa bài viết của tôi" onclick="handleDeletePost(${p.postId})">
            <i class="fa-regular fa-trash-can"></i>
        </button>
    ` : `
        <button class="btn btn-outline-secondary btn-sm border-0 rounded-circle text-muted" style="width: 32px; height: 32px; padding: 0;" title="Ẩn bài viết khỏi bảng tin của bạn" onclick="handleHidePost(${p.postId})">
            <i class="fa-regular fa-eye-slash"></i>
        </button>
    `;

    const isLiked = !!p.likedByMe;
    const likeBtnClass = isLiked ? 'post-action-btn liked' : 'post-action-btn';
    const heartIcon = isLiked ? 'fa-solid fa-heart' : 'fa-regular fa-heart';

    // Xử lý nội dung (Markdown nếu có hàm marked)
    let renderedContent = escapeHtml(p.content);
    if (typeof marked !== 'undefined' && marked.parse) {
        try {
            renderedContent = marked.parse(p.content);
        } catch (e) {
            renderedContent = escapeHtml(p.content);
        }
    }

    return `
        <div class="card post-card p-4 shadow-sm mb-3" id="post-card-${p.postId}">
            <!-- Header bài viết: Tác giả & Hành động -->
            <div class="d-flex justify-content-between align-items-center mb-2">
                <div class="d-flex align-items-center gap-3">
                    <div class="${avatarClass}">
                        ${initial}
                    </div>
                    <div>
                        <div class="d-flex align-items-center gap-2 flex-wrap">
                            <span class="fw-bold text-dark">${escapeHtml(p.authorName || p.authorUsername)}</span>
                            ${roleBadge}
                        </div>
                        <div class="text-muted" style="font-size: 0.78rem;">
                            <span>@${escapeHtml(p.authorUsername)}</span>
                            <span class="mx-1">&bull;</span>
                            <span>${timeAgo}</span>
                        </div>
                    </div>
                </div>
                <div>${actionBtn}</div>
            </div>

            <!-- Tags Kênh & Môn học (Gọn gàng ngay dưới phần tác giả, không làm chật chội avatar) -->
            <div class="d-flex flex-wrap align-items-center gap-2 my-2">
                ${channelBadge}
                ${topicBadge}
            </div>

            <!-- Tiêu đề & Nội dung bài viết -->
            <h5 class="fw-bold text-dark mt-2 mb-2" style="font-size: 1.15rem; line-height: 1.4;">${escapeHtml(p.title)}</h5>
            <div class="text-secondary post-body-content mb-3" style="font-size: 0.92rem; line-height: 1.65;">
                ${renderedContent}
            </div>

            <!-- Thanh tương tác bài viết (Like, Comment, Share) -->
            <div class="d-flex justify-content-between align-items-center pt-2 border-top">
                <div class="d-flex gap-2">
                    <button class="${likeBtnClass}" id="btn-like-post-${p.postId}" onclick="handleToggleLike(${p.postId})">
                        <i class="${heartIcon}"></i>
                        <span id="like-count-${p.postId}">${p.likesCount}</span>
                    </button>
                    <button class="post-action-btn" onclick="toggleCommentsSection(${p.postId})">
                        <i class="fa-regular fa-message"></i>
                        <span id="comment-count-${p.postId}">${p.commentsCount}</span> bình luận
                    </button>
                </div>
                <button class="post-action-btn text-muted" onclick="handleSharePost(${p.postId})">
                    <i class="fa-regular fa-share-from-square"></i>Chia sẻ
                </button>
            </div>

            <!-- Khu vực bình luận (Mở rộng theo phong cách Discord/Facebook) -->
            <div class="mt-3 pt-3 border-top" id="comments-section-${p.postId}" style="display: none;">
                <!-- Danh sách bình luận -->
                <div class="d-flex flex-column gap-2 mb-3" id="comments-list-${p.postId}">
                    <div class="text-muted small text-center py-2">
                        <span class="spinner-border spinner-border-sm me-1 text-primary"></span>Đang tải bình luận...
                    </div>
                </div>

                <!-- Input gửi bình luận mới -->
                <form onsubmit="handleSendComment(event, ${p.postId})" class="d-flex gap-2">
                    <input type="text" class="form-control rounded-pill px-3 py-2 small" id="comment-input-${p.postId}" placeholder="Viết phản hồi hoặc đóng góp ý kiến của bạn..." required autocomplete="off">
                    <button type="submit" class="btn btn-primary rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 40px; height: 40px;">
                        <i class="fa-solid fa-paper-plane fa-sm"></i>
                    </button>
                </form>
            </div>
        </div>
    `;
}

/**
 * Xử lý tạo bài viết mới (Hỗ trợ linh hoạt Topic theo ID hoặc tên tự do, kèm trạng thái học tập)
 */
async function handleCreatePost(e) {
    e.preventDefault();
    const title = document.getElementById('post-title-input').value.trim();
    let content = document.getElementById('post-content-input').value.trim();
    const channel = document.getElementById('post-channel-select')?.value || 'general';
    const topicInput = (document.getElementById('post-topic-input')?.value || '').trim();
    const submitBtn = document.getElementById('btn-submit-post');

    if (!title || !content) {
        Swal.fire('Lỗi', 'Vui lòng điền đầy đủ tiêu đề và nội dung bài học!', 'warning');
        return;
    }

    // Gắn trạng thái học tập nếu có chọn
    if (currentPostMood) {
        content = `[${currentPostMood}]\n\n` + content;
    }

    let topicId = null;
    let topicName = null;

    if (topicInput) {
        const matched = allTopics.find(t => (t.topicName || '').trim().toLowerCase() === topicInput.toLowerCase());
        if (matched) {
            topicId = matched.topicId;
        } else {
            topicName = topicInput;
        }
    }

    try {
        if (submitBtn) submitBtn.disabled = true;
        const res = await API.community.createPost({ title, content, channel, topicId, topicName });

        // Đóng modal & reset form
        const modalEl = document.getElementById('createPostModal');
        if (modalEl) {
            const inst = bootstrap.Modal.getInstance(modalEl);
            if (inst) inst.hide();
        }
        document.getElementById('create-post-form').reset();
        removeSelectedMood();

        Swal.fire({
            icon: 'success',
            title: 'Đăng bài thành công!',
            text: 'Bài thảo luận học tập của bạn đã được xuất bản trên diễn đàn cộng đồng.',
            timer: 2000,
            showConfirmButton: false
        });

        // Tải lại feed & stats
        await Promise.all([loadFeed(), loadCommunityStats()]);

    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể tạo bài viết.', 'error');
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

/**
 * Thả tim / Bỏ thả tim bài viết
 */
async function handleToggleLike(postId) {
    const btn = document.getElementById(`btn-like-post-${postId}`);
    const countEl = document.getElementById(`like-count-${postId}`);
    if (!btn) return;

    try {
        const res = await API.community.toggleLike(postId);
        const data = res.data || {};
        const liked = !!data.liked;
        const likesCount = data.likesCount !== undefined ? data.likesCount : 0;

        if (countEl) countEl.textContent = likesCount;

        if (liked) {
            btn.classList.add('liked');
            btn.querySelector('i').className = 'fa-solid fa-heart';
        } else {
            btn.classList.remove('liked');
            btn.querySelector('i').className = 'fa-regular fa-heart';
        }
    } catch (err) {
        console.error('Lỗi khi tương tác thả tim:', err);
    }
}

/**
 * Bật/tắt và tải bình luận cho bài viết
 */
async function toggleCommentsSection(postId) {
    const sec = document.getElementById(`comments-section-${postId}`);
    if (!sec) return;

    if (sec.style.display === 'none') {
        sec.style.display = 'block';
        await loadComments(postId);
    } else {
        sec.style.display = 'none';
    }
}

/**
 * Tải danh sách bình luận
 */
async function loadComments(postId) {
    const listEl = document.getElementById(`comments-list-${postId}`);
    if (!listEl) return;

    try {
        const res = await API.community.listComments(postId);
        const comments = res.data || [];

        if (comments.length === 0) {
            listEl.innerHTML = '<div class="text-muted small text-center py-2">Chưa có bình luận nào. Hãy mở đầu cuộc trò chuyện nhé!</div>';
            return;
        }

        listEl.innerHTML = comments.map(c => {
            const isTeacher = (c.authorRole || '').toLowerCase() === 'teacher' || (c.authorRole || '').toLowerCase() === 'admin';
            const roleBadge = isTeacher
                ? '<span class="badge bg-indigo text-white px-2 py-0 small ms-1" style="background-color: #6366f1; font-size: 0.65rem;">Giảng Viên</span>'
                : '';
            const initial = (c.authorName || c.authorUsername || 'U').charAt(0).toUpperCase();
            const timeAgo = formatTimeAgo(c.createdAt);
            const canDelete = currentUser && (currentUser.userId === c.userId || API.auth.isTeacher());

            const deleteBtn = canDelete ? `
                <button class="btn btn-link btn-sm text-danger p-0 text-decoration-none ms-2" onclick="handleDeleteComment(${postId}, ${c.commentId})" title="Xóa bình luận">
                    <i class="fa-regular fa-trash-can fa-xs"></i>
                </button>
            ` : '';

            return `
                <div class="d-flex gap-2 align-items-start" id="comment-item-${c.commentId}">
                    <div class="rounded-circle bg-primary-subtle text-primary d-flex align-items-center justify-content-center fw-bold small flex-shrink-0" style="width: 32px; height: 32px; font-size: 0.85rem;">
                        ${initial}
                    </div>
                    <div class="comment-bubble flex-grow-1">
                        <div class="d-flex justify-content-between align-items-center mb-1">
                            <span class="fw-bold text-dark small">
                                ${escapeHtml(c.authorName || c.authorUsername)}
                                ${roleBadge}
                            </span>
                            <div class="d-flex align-items-center">
                                <small class="text-muted" style="font-size: 0.72rem;">${timeAgo}</small>
                                ${deleteBtn}
                            </div>
                        </div>
                        <div class="text-secondary small" style="line-height: 1.5;">${escapeHtml(c.content)}</div>
                    </div>
                </div>
            `;
        }).join('');

    } catch (err) {
        listEl.innerHTML = `<div class="text-danger small py-1">Không thể tải bình luận: ${escapeHtml(err.message)}</div>`;
    }
}

/**
 * Gửi bình luận mới
 */
async function handleSendComment(e, postId) {
    e.preventDefault();
    const input = document.getElementById(`comment-input-${postId}`);
    if (!input) return;

    const content = input.value.trim();
    if (!content) return;

    try {
        input.disabled = true;
        await API.community.createComment(postId, content);
        input.value = '';

        // Tăng đếm comment trên UI
        const countEl = document.getElementById(`comment-count-${postId}`);
        if (countEl) {
            const current = parseInt(countEl.textContent || '0', 10);
            countEl.textContent = current + 1;
        }

        // Tải lại bình luận
        await loadComments(postId);

        // Cập nhật stats
        loadCommunityStats();

    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể gửi bình luận.', 'error');
    } finally {
        input.disabled = false;
        input.focus();
    }
}

/**
 * Xóa bình luận
 */
async function handleDeleteComment(postId, commentId) {
    const res = await Swal.fire({
        title: 'Xóa bình luận?',
        text: 'Bạn có chắc muốn xóa phản hồi này không?',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: 'Xóa',
        cancelButtonText: 'Hủy'
    });

    if (!res.isConfirmed) return;

    try {
        await API.community.deleteComment(postId, commentId);

        // Giảm đếm comment trên UI
        const countEl = document.getElementById(`comment-count-${postId}`);
        if (countEl) {
            const current = Math.max(0, parseInt(countEl.textContent || '1', 10) - 1);
            countEl.textContent = current;
        }

        await loadComments(postId);
        loadCommunityStats();

    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể xóa bình luận.', 'error');
    }
}

/**
 * Xóa bài viết
 */
async function handleDeletePost(postId) {
    const res = await Swal.fire({
        title: 'Xóa bài viết?',
        text: 'Toàn bộ nội dung và các bình luận liên quan sẽ bị xóa vĩnh viễn!',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonText: 'Xóa bài viết',
        cancelButtonText: 'Hủy'
    });

    if (!res.isConfirmed) return;

    try {
        await API.community.deletePost(postId);
        const card = document.getElementById(`post-card-${postId}`);
        if (card) card.remove();

        Swal.fire({
            icon: 'success',
            title: 'Đã xóa bài viết',
            timer: 1500,
            showConfirmButton: false
        });

        loadCommunityStats();
    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể xóa bài viết.', 'error');
    }
}

/**
 * Chia sẻ bài viết (Copy link)
 */
function handleSharePost(postId) {
    const url = window.location.origin + window.location.pathname + '#post-card-' + postId;
    if (navigator.clipboard) {
        navigator.clipboard.writeText(url).then(() => {
            Swal.fire({
                icon: 'success',
                title: 'Đã sao chép liên kết!',
                text: 'Bạn có thể gửi liên kết bài thảo luận này cho bạn học hoặc giảng viên.',
                timer: 1800,
                showConfirmButton: false
            });
        });
    }
}

/**
 * Chuyển đổi timestamp thành relative time thân thiện (Vừa xong, 10 phút trước...)
 */
function formatTimeAgo(ts) {
    if (!ts) return 'Vừa xong';
    try {
        const time = new Date(ts.replace ? ts.replace(' ', 'T') : ts).getTime();
        const diff = Math.floor((Date.now() - time) / 1000);

        if (diff < 60) return 'Vừa xong';
        if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`;
        if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`;
        if (diff < 604800) return `${Math.floor(diff / 86400)} ngày trước`;
        return new Date(time).toLocaleDateString('vi-VN');
    } catch (e) {
        return 'Vừa xong';
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
