/**
 * Teacher Dashboard Logic & Interactivity
 * Xử lý quản trị ngân hàng câu hỏi, thống kê KPI và AI Pedagogical Insight
 */

// 1. Kiểm tra phân quyền: Chỉ giảng viên và quản trị viên mới được vào
const currentUser = API.auth.requireTeacher();

// 2. Global State
let allQuestions = [];
let allTopics = [];
let topicsMap = {};
let questionModal = null;
let currentPage = 1;
const pageSize = 10;
let searchKeyword = '';
let currentGeneratedQuestions = [];
let copilotChatHistory = [];

document.addEventListener('DOMContentLoaded', () => {
    // Khởi tạo Bootstrap Modal
    const modalEl = document.getElementById('questionModal');
    if (modalEl) {
        questionModal = new bootstrap.Modal(modalEl);
    }

    // Hiển thị tên giảng viên
    if (currentUser) {
        document.getElementById('teacher-name').textContent = currentUser.fullName || currentUser.username;
    }

    // Đăng xuất
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', (e) => {
            e.preventDefault();
            Swal.fire({
                title: 'Đăng xuất?',
                text: 'Bạn có chắc chắn muốn rời khỏi bảng điều khiển giảng viên?',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Đăng xuất',
                cancelButtonText: 'Hủy'
            }).then((result) => {
                if (result.isConfirmed) {
                    API.auth.logout();
                }
            });
        });
    }

    // Gắn sự kiện các nút thao tác
    setupEventListeners();

    // Tải dữ liệu ban đầu
    loadInitialData();
});

/**
 * Gắn sự kiện cho các thành phần điều khiển
 */
function setupEventListeners() {
    // Filter chủ đề
    const filterTopic = document.getElementById('filter-topic');
    if (filterTopic) {
        filterTopic.addEventListener('change', () => {
            currentPage = 1;
            loadQuestions();
        });
    }

    // 1.5. Tìm kiếm từ khóa câu hỏi realtime
    const searchInput = document.getElementById('search-question');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            searchKeyword = e.target.value;
            currentPage = 1;
            renderQuestionsView();
        });
    }

    // Nút mở modal thêm môn / chủ đề mới
    const btnOpenTopic = document.getElementById('btn-open-topic-modal');
    if (btnOpenTopic) {
        btnOpenTopic.addEventListener('click', () => {
            const modalEl = document.getElementById('createTopicModal');
            if (modalEl) new bootstrap.Modal(modalEl).show();
        });
    }

    // Form tạo chủ đề mới
    const createTopicForm = document.getElementById('create-topic-form');
    if (createTopicForm) {
        createTopicForm.addEventListener('submit', handleCreateTopicSubmit);
    }

    // Nút mở modal thêm câu hỏi mới
    const btnOpenCreate = document.getElementById('btn-open-create-modal');
    if (btnOpenCreate) {
        btnOpenCreate.addEventListener('click', () => {
            openCreateModal();
        });
    }

    // Nút Lưu câu hỏi trong Modal
    const btnSave = document.getElementById('btn-save-question');
    if (btnSave) {
        btnSave.addEventListener('click', () => {
            handleSaveQuestion();
        });
    }

    // Nút làm mới dữ liệu thống kê
    const btnRefreshStats = document.getElementById('btn-refresh-stats');
    if (btnRefreshStats) {
        btnRefreshStats.addEventListener('click', () => {
            loadTeacherStats();
        });
    }

    // Nút làm mới báo lỗi
    const btnRefreshReports = document.getElementById('btn-refresh-reports');
    if (btnRefreshReports) {
        btnRefreshReports.addEventListener('click', () => {
            loadReportedQuestions();
        });
    }

    // Khi chuyển sang Tab Báo Lỗi thì tải dữ liệu
    const tabReportsBtn = document.getElementById('tab-reports-btn');
    if (tabReportsBtn) {
        tabReportsBtn.addEventListener('shown.bs.tab', () => {
            loadReportedQuestions();
        });
    }

    // ── AI Studio & Co-Pilot Events ──

    // Form sinh câu hỏi bằng AI
    const aiGenForm = document.getElementById('ai-generator-form');
    if (aiGenForm) {
        aiGenForm.addEventListener('submit', handleAiGenerateSubmit);
    }

    // Nút xóa kết quả sinh câu hỏi
    const btnClearAi = document.getElementById('btn-clear-ai-results');
    if (btnClearAi) {
        btnClearAi.addEventListener('click', () => {
            currentGeneratedQuestions = [];
            const resultsWrapper = document.getElementById('ai-results-wrapper');
            if (resultsWrapper) resultsWrapper.style.display = 'none';
            const cardsContainer = document.getElementById('ai-generated-cards-container');
            if (cardsContainer) cardsContainer.innerHTML = '';
        });
    }

    // Nút lưu tất cả câu hỏi vừa sinh vào DB
    const btnImportAll = document.getElementById('btn-import-all-ai');
    if (btnImportAll) {
        btnImportAll.addEventListener('click', handleImportAllAiQuestions);
    }

    // Form Trợ lý Sư phạm Co-Pilot Chat
    const copilotForm = document.getElementById('copilot-chat-form');
    if (copilotForm) {
        copilotForm.addEventListener('submit', handleCopilotChatSubmit);
    }

    // Quick Prompt Chips cho Co-Pilot
    document.querySelectorAll('.quick-prompt-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const prompt = btn.getAttribute('data-prompt');
            const input = document.getElementById('copilot-chat-input');
            if (input && prompt) {
                input.value = prompt;
                const form = document.getElementById('copilot-chat-form');
                if (form) form.dispatchEvent(new Event('submit'));
            }
        });
    });

    // Quick Topic Buttons
    document.querySelectorAll('.quick-topic-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const topic = btn.getAttribute('data-topic');
            const input = document.getElementById('ai-topic-input');
            if (input && topic) {
                input.value = topic;
                input.focus();
            }
        });
    });

    // Thẩm định môn học / chủ đề bằng AI
    const btnValidateTopic = document.getElementById('btn-validate-topic');
    if (btnValidateTopic) {
        btnValidateTopic.addEventListener('click', handleValidateTopic);
    }

    // AI gợi ý bẫy tư duy động theo môn
    const btnSuggestMisc = document.getElementById('btn-suggest-misconceptions');
    if (btnSuggestMisc) {
        btnSuggestMisc.addEventListener('click', handleSuggestMisconceptions);
    }

    // Thẩm định bẫy tư duy bằng AI
    const btnValidateMisc = document.getElementById('btn-validate-misconception');
    if (btnValidateMisc) {
        btnValidateMisc.addEventListener('click', handleValidateMisconception);
    }

    // AI Hỗ trợ hoàn thiện câu hỏi trong Modal
    const btnModalAiAssist = document.getElementById('btn-modal-ai-assist');
    if (btnModalAiAssist) {
        btnModalAiAssist.addEventListener('click', handleModalAiAssist);
    }

    // Gợi ý bẫy tư duy trong Modal thêm câu hỏi
    const btnModalSuggestMisc = document.getElementById('btn-modal-suggest-misconception');
    if (btnModalSuggestMisc) {
        btnModalSuggestMisc.addEventListener('click', handleModalSuggestMisconception);
    }

    // Tạo môn mới nhanh từ trong Modal thêm câu hỏi
    const btnQuickAddTopicModal = document.getElementById('btn-quick-add-topic-from-modal');
    if (btnQuickAddTopicModal) {
        btnQuickAddTopicModal.addEventListener('click', handleQuickAddTopicFromModal);
    }
}

/**
 * Tải toàn bộ dữ liệu ban đầu: Topics, KPIs, Questions, Reports
 */
async function loadInitialData() {
    try {
        await loadTopics();
        await Promise.all([
            loadTeacherStats(),
            loadQuestions(),
            loadReportedQuestions()
        ]);
    } catch (err) {
        console.error('Lỗi khi nạp dữ liệu ban đầu:', err);
    }
}

/**
 * Tải danh sách Chủ Đề để nạp vào các Dropdown và Datalist gợi ý
 */
async function loadTopics() {
    try {
        const res = await API.topics.list();
        allTopics = res.data || [];
        topicsMap = {};

        const filterSelect = document.getElementById('filter-topic');
        const modalSelect = document.getElementById('modal-topic-id');
        const topicDatalist = document.getElementById('topic-datalist');

        // Reset options
        if (filterSelect) filterSelect.innerHTML = '<option value="">-- Tất cả chủ đề --</option>';
        if (modalSelect) modalSelect.innerHTML = '<option value="">-- Chọn chủ đề bài học --</option>';
        if (topicDatalist) topicDatalist.innerHTML = '';

        allTopics.forEach(t => {
            topicsMap[t.topicId] = t.topicName;

            if (filterSelect) {
                const opt1 = document.createElement('option');
                opt1.value = t.topicId;
                opt1.textContent = t.topicName;
                filterSelect.appendChild(opt1);
            }

            if (modalSelect) {
                const opt2 = document.createElement('option');
                opt2.value = t.topicId;
                opt2.textContent = t.topicName;
                modalSelect.appendChild(opt2);
            }

            if (topicDatalist) {
                const opt3 = document.createElement('option');
                opt3.value = t.topicName;
                topicDatalist.appendChild(opt3);
            }
        });
    } catch (err) {
        console.error('Lỗi tải danh mục chủ đề:', err);
    }
}

/**
 * Tạo môn học / chủ đề mới
 */
async function handleCreateTopicSubmit(e) {
    if (e) e.preventDefault();
    const nameInput = document.getElementById('new-topic-name');
    const descInput = document.getElementById('new-topic-desc');
    const topicName = nameInput ? nameInput.value.trim() : '';
    const description = descInput ? descInput.value.trim() : '';

    if (!topicName) {
        Swal.fire('Lỗi', 'Vui lòng nhập tên môn học hoặc chủ đề.', 'warning');
        return;
    }

    const btn = document.getElementById('btn-save-topic');
    if (btn) btn.disabled = true;

    try {
        await API.topics.create({ topicName, description });
        const modalEl = document.getElementById('createTopicModal');
        if (modalEl) {
            const inst = bootstrap.Modal.getInstance(modalEl);
            if (inst) inst.hide();
        }
        if (nameInput) nameInput.value = '';
        if (descInput) descInput.value = '';

        Swal.fire({
            icon: 'success',
            title: 'Khởi tạo chủ đề thành công!',
            text: `Chủ đề "${topicName}" đã được lưu vào hệ thống.`,
            timer: 2000,
            showConfirmButton: false
        });

        await loadTopics();
    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể tạo chủ đề mới.', 'error');
    } finally {
        if (btn) btn.disabled = false;
    }
}

/**
 * Tải danh sách các câu hỏi bị báo lỗi từ người học hoặc đồng nghiệp
 */
async function loadReportedQuestions() {
    const tbody = document.getElementById('reports-tbody');
    const badge = document.getElementById('reports-count-badge');
    if (!tbody) return;

    try {
        const res = await API.discussion.getReported(50);
        const list = res.data || [];

        if (badge) badge.textContent = list.length;

        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center py-5 text-muted"><i class="fa-solid fa-circle-check text-success fs-3 d-block mb-2"></i>Tuyệt vời! Hiện tại không có câu hỏi nào bị cộng đồng báo lỗi.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(item => `
            <tr>
                <td class="fw-bold text-muted text-center">${item.questionId}</td>
                <td>
                    <div class="fw-semibold text-dark text-truncate" style="max-width: 450px;" title="${escapeHtml(item.questionText)}">
                        ${escapeHtml(item.questionText)}
                    </div>
                </td>
                <td>
                    <span class="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-2 py-1">
                        ${escapeHtml(item.topicName || 'Chủ đề')}
                    </span>
                </td>
                <td class="text-center">
                    <span class="badge bg-danger text-white rounded-pill px-3 py-1">
                        <i class="fa-solid fa-triangle-exclamation me-1"></i>${item.reportCount} lượt
                    </span>
                </td>
                <td class="text-center">
                    <button class="btn btn-sm btn-outline-primary rounded-pill px-3 me-1" onclick="openEditModal(${item.questionId})">
                        <i class="fa-solid fa-pen-to-square me-1"></i>Sửa câu
                    </button>
                    <button class="btn btn-sm btn-outline-danger rounded-pill px-2" onclick="handleDeleteQuestion(${item.questionId})">
                        <i class="fa-solid fa-trash-can"></i>
                    </button>
                </td>
            </tr>
        `).join('');
    } catch (err) {
        console.error('Lỗi khi tải danh sách báo lỗi:', err);
        tbody.innerHTML = '<tr><td colspan="5" class="text-center py-4 text-danger">Không thể tải danh sách báo lỗi.</td></tr>';
    }
}

/**
 * Tải 4 chỉ số KPIs và AI Pedagogical Insight
 */
async function loadTeacherStats() {
    try {
        const res = await API.teacher.stats();
        const data = res.data || {};

        // 1. Điền 4 KPI Cards
        const kpis = data.kpis || {};
        const kpiStudentsEl = document.getElementById('kpi-students');
        if (kpiStudentsEl) kpiStudentsEl.textContent = kpis.totalStudents !== undefined ? kpis.totalStudents : 0;

        const kpiQuestionsEl = document.getElementById('kpi-questions');
        if (kpiQuestionsEl) kpiQuestionsEl.textContent = kpis.totalQuestions !== undefined ? kpis.totalQuestions : 0;

        const kpiTopicsEl = document.getElementById('kpi-topics');
        if (kpiTopicsEl) kpiTopicsEl.textContent = kpis.totalTopics !== undefined ? kpis.totalTopics : 0;

        const kpiAvgScoreEl = document.getElementById('kpi-avg-score');
        if (kpiAvgScoreEl) kpiAvgScoreEl.textContent = kpis.averageScore !== undefined ? kpis.averageScore : '0.0';

        const kpiGuessRateEl = document.getElementById('kpi-guess-rate');
        if (kpiGuessRateEl) kpiGuessRateEl.textContent = kpis.guessRate !== undefined ? kpis.guessRate : '0.0';

        // 2. Điền số liệu 5 nhóm sai lầm (Misconceptions)
        const mis = data.misconceptions || {};
        const statSyntaxEl = document.getElementById('stat-syntax-swap');
        if (statSyntaxEl) statSyntaxEl.textContent = mis.syntax_swap || 0;

        const statBoundaryEl = document.getElementById('stat-boundary-blindness');
        if (statBoundaryEl) statBoundaryEl.textContent = mis.boundary_blindness || 0;

        const statMentalEl = document.getElementById('stat-mental-model-gap');
        if (statMentalEl) statMentalEl.textContent = mis.mental_model_gap || 0;

        const statLogicEl = document.getElementById('stat-logic-flaw');
        if (statLogicEl) statLogicEl.textContent = mis.logic_flaw || 0;

        const statOtherEl = document.getElementById('stat-other-traps');
        if (statOtherEl) statOtherEl.textContent = mis.other || 0;

        // 3. Điền bảng các bài nộp gần nhất
        renderRecentSessions(data.recentSessions || []);
    } catch (err) {
        console.error('Lỗi khi tải thống kê giảng viên:', err);
    }
}

/**
 * Hiển thị bảng các bài nộp gần đây nhất
 */
function renderRecentSessions(sessions) {
    const tbody = document.getElementById('recent-sessions-tbody');
    if (!tbody) return;

    if (sessions.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted">Chưa có lượt nộp bài nào trên hệ thống.</td></tr>`;
        return;
    }

    tbody.innerHTML = sessions.map(s => {
        let scoreBadge = 'bg-success';
        if (s.score < 5.0) scoreBadge = 'bg-danger';
        else if (s.score < 8.0) scoreBadge = 'bg-warning text-dark';

        const completedTime = s.completedAt ? s.completedAt.replace('T', ' ').substring(0, 19) : '--';

        return `
            <tr>
                <td class="fw-bold text-muted">#${s.sessionId}</td>
                <td>
                    <div class="fw-semibold text-dark">${escapeHtml(s.studentName || 'Sinh Viên')}</div>
                    <small class="text-muted">@${escapeHtml(s.username || '')}</small>
                </td>
                <td><span class="badge bg-light text-dark border">${escapeHtml(s.topicName || 'Chủ đề')}</span></td>
                <td class="text-center fw-semibold">${s.correctCount} / ${s.totalQuestions}</td>
                <td class="text-center">
                    <span class="badge ${scoreBadge} px-2 py-1 fs-6">${Number(s.score).toFixed(1)}</span>
                </td>
                <td class="text-muted small">${completedTime}</td>
            </tr>
        `;
    }).join('');
}

/**
 * Tải danh sách câu hỏi theo bộ lọc chủ đề
 */
async function loadQuestions() {
    const tbody = document.getElementById('questions-tbody');
    tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-muted"><div class="spinner-border spinner-border-sm text-primary me-2"></div>Đang tải câu hỏi...</td></tr>`;

    const filterVal = document.getElementById('filter-topic').value;
    const topicId = filterVal ? parseInt(filterVal) : null;

    try {
        const res = await API.questions.list(topicId);
        allQuestions = res.data || [];
        currentPage = 1;
        renderQuestionsView();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-4 text-danger"><i class="fa-solid fa-triangle-exclamation me-1"></i>Lỗi tải câu hỏi: ${err.message}</td></tr>`;
    }
}

/**
 * 1.5. Render bảng danh sách câu hỏi kèm phân trang (10 câu/trang) và tìm kiếm realtime
 */
function renderQuestionsView() {
    const tbody = document.getElementById('questions-tbody');
    const counter = document.getElementById('questions-count-text');
    const paginationEl = document.getElementById('questions-pagination');

    const filtered = filterQuestions(searchKeyword);
    const totalItems = filtered.length;
    const totalPages = Math.ceil(totalItems / pageSize) || 1;

    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPage < 1) currentPage = 1;

    const startIdx = (currentPage - 1) * pageSize;
    const endIdx = Math.min(startIdx + pageSize, totalItems);
    const pagedQuestions = filtered.slice(startIdx, endIdx);

    if (counter) {
        counter.textContent = totalItems > 0
            ? `Hiển thị ${startIdx + 1} - ${endIdx} trên tổng số ${totalItems} câu hỏi (Trang ${currentPage}/${totalPages})`
            : 'Không tìm thấy câu hỏi nào phù hợp';
    }

    if (totalItems === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center py-5 text-muted">Không tìm thấy câu hỏi nào phù hợp.</td></tr>`;
        if (paginationEl) paginationEl.innerHTML = '';
        return;
    }

    tbody.innerHTML = pagedQuestions.map(q => {
        const topicTitle = topicsMap[q.topicId] || `Chủ đề #${q.topicId}`;
        let diffBadge = 'bg-secondary-subtle text-secondary';
        let diffText = 'Trung bình';
        if (q.difficulty === 'easy') {
            diffBadge = 'bg-success-subtle text-success';
            diffText = 'Dễ';
        } else if (q.difficulty === 'hard') {
            diffBadge = 'bg-danger-subtle text-danger';
            diffText = 'Khó';
        }

        let tagBadge = '';
        if (q.misconceptionTag) {
            tagBadge = `<span class="badge bg-light text-primary border ms-1" style="font-size: 0.72rem;">${escapeHtml(q.misconceptionTag)}</span>`;
        }

        return `
            <tr>
                <td class="fw-bold text-muted">${q.questionId}</td>
                <td>
                    <div class="question-cell fw-semibold text-dark" title="${escapeHtml(q.questionText)}">
                        ${escapeHtml(q.questionText)}
                    </div>
                </td>
                <td>
                    <span class="badge bg-light text-dark border text-truncate" style="max-width: 130px;" title="${escapeHtml(topicTitle)}">${escapeHtml(topicTitle)}</span>
                    ${tagBadge}
                </td>
                <td class="text-center"><span class="badge ${diffBadge} px-2 py-1">${diffText}</span></td>
                <td class="text-center"><span class="badge bg-primary px-3 py-1 fw-bold fs-6">${q.correctAnswer}</span></td>
                <td class="text-center">
                    <button class="btn btn-outline-primary btn-sm table-action-btn me-1" title="Xem / Sửa câu hỏi" onclick="openEditModal(${q.questionId})">
                        <i class="fa-solid fa-pen"></i>
                    </button>
                    <button class="btn btn-outline-danger btn-sm table-action-btn" title="Xóa câu hỏi" onclick="confirmDeleteQuestion(${q.questionId})">
                        <i class="fa-solid fa-trash"></i>
                    </button>
                </td>
            </tr>
        `;
    }).join('');

    // Render bộ chuyển trang (Pagination Controls)
    if (paginationEl) {
        let pagHtml = '';

        // Nút Prev
        pagHtml += `
            <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <button class="page-link" onclick="goToPage(${currentPage - 1})" aria-label="Previous">
                    <i class="fa-solid fa-chevron-left"></i>
                </button>
            </li>
        `;

        // Các số trang
        for (let p = 1; p <= totalPages; p++) {
            pagHtml += `
                <li class="page-item ${p === currentPage ? 'active' : ''}">
                    <button class="page-link" onclick="goToPage(${p})">${p}</button>
                </li>
            `;
        }

        // Nút Next
        pagHtml += `
            <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
                <button class="page-link" onclick="goToPage(${currentPage + 1})" aria-label="Next">
                    <i class="fa-solid fa-chevron-right"></i>
                </button>
            </li>
        `;

        paginationEl.innerHTML = pagHtml;
    }
}

function goToPage(page) {
    currentPage = page;
    renderQuestionsView();
}

/**
 * Lọc danh sách câu hỏi theo từ khóa tìm kiếm
 */
function filterQuestions(keyword) {
    if (!keyword || !keyword.trim()) return allQuestions;
    const term = keyword.trim().toLowerCase();
    return allQuestions.filter(q => {
        return (q.questionText && q.questionText.toLowerCase().includes(term)) ||
               (q.explanation && q.explanation.toLowerCase().includes(term)) ||
               (q.misconceptionTag && q.misconceptionTag.toLowerCase().includes(term)) ||
               (q.questionId && q.questionId.toString().includes(term));
    });
}

/**
 * Mở modal tạo mới câu hỏi
 */
function openCreateModal() {
    document.getElementById('question-form').reset();
    document.getElementById('modal-question-id').value = '';
    document.getElementById('questionModalLabel').innerHTML = '<i class="fa-solid fa-plus-circle me-2"></i>Thêm Câu Hỏi Mới';
    document.getElementById('modal-difficulty').value = 'medium';
    document.getElementById('modal-misconception-tag').value = '';

    // Chọn topic mặc định nếu đang lọc theo topic
    const currentTopicFilter = document.getElementById('filter-topic').value;
    if (currentTopicFilter) {
        document.getElementById('modal-topic-id').value = currentTopicFilter;
    }

    questionModal.show();
}

/**
 * Mở modal chỉnh sửa câu hỏi hiện có
 */
function openEditModal(questionId) {
    const q = allQuestions.find(item => item.questionId === questionId);
    if (!q) return;

    document.getElementById('modal-question-id').value = q.questionId;
    document.getElementById('questionModalLabel').innerHTML = `<i class="fa-solid fa-pen-to-square me-2"></i>Chỉnh Sửa Câu Hỏi #${q.questionId}`;
    document.getElementById('modal-topic-id').value = q.topicId;
    document.getElementById('modal-difficulty').value = q.difficulty || 'medium';
    document.getElementById('modal-question-text').value = q.questionText;
    document.getElementById('modal-option-a').value = q.optionA;
    document.getElementById('modal-option-b').value = q.optionB;
    document.getElementById('modal-option-c').value = q.optionC;
    document.getElementById('modal-option-d').value = q.optionD;
    document.getElementById('modal-correct-answer').value = q.correctAnswer;
    document.getElementById('modal-explanation').value = q.explanation || '';
    document.getElementById('modal-misconception-tag').value = q.misconceptionTag || '';

    questionModal.show();
}

/**
 * Lưu câu hỏi (Thêm mới hoặc Cập nhật)
 */
async function handleSaveQuestion() {
    const idVal = document.getElementById('modal-question-id').value;
    const isEdit = !!idVal;

    const topicId = parseInt(document.getElementById('modal-topic-id').value);
    const difficulty = document.getElementById('modal-difficulty').value;
    const misconceptionTag = document.getElementById('modal-misconception-tag').value || null;
    const questionText = document.getElementById('modal-question-text').value.trim();
    const optionA = document.getElementById('modal-option-a').value.trim();
    const optionB = document.getElementById('modal-option-b').value.trim();
    const optionC = document.getElementById('modal-option-c').value.trim();
    const optionD = document.getElementById('modal-option-d').value.trim();
    const correctAnswer = document.getElementById('modal-correct-answer').value;
    const explanation = document.getElementById('modal-explanation').value.trim();

    if (!topicId || !questionText || !optionA || !optionB || !optionC || !optionD || !correctAnswer) {
        Swal.fire({
            icon: 'warning',
            title: 'Thiếu thông tin',
            text: 'Vui lòng điền đầy đủ chủ đề, nội dung câu hỏi, 4 phương án và đáp án đúng!'
        });
        return;
    }

    const payload = {
        topicId,
        difficulty,
        misconceptionTag,
        questionText,
        optionA,
        optionB,
        optionC,
        optionD,
        correctAnswer,
        explanation
    };

    try {
        if (isEdit) {
            payload.questionId = parseInt(idVal);
            await API.questions.update(parseInt(idVal), payload);
            Swal.fire({
                icon: 'success',
                title: 'Đã cập nhật!',
                text: 'Câu hỏi đã được cập nhật thành công.',
                timer: 1500,
                showConfirmButton: false
            });
        } else {
            await API.questions.create(payload);
            Swal.fire({
                icon: 'success',
                title: 'Thành công!',
                text: 'Câu hỏi mới đã được thêm vào ngân hàng đề.',
                timer: 1500,
                showConfirmButton: false
            });
        }

        questionModal.hide();
        // Nạp lại danh sách câu hỏi và cập nhật KPI
        await Promise.all([
            loadQuestions(),
            loadTeacherStats()
        ]);
    } catch (err) {
        Swal.fire({
            icon: 'error',
            title: 'Lỗi',
            text: err.message || 'Không thể lưu câu hỏi.'
        });
    }
}

/**
 * Xác nhận và thực hiện xóa câu hỏi
 */
function confirmDeleteQuestion(questionId) {
    Swal.fire({
        title: 'Xóa câu hỏi này?',
        text: `Bạn có chắc chắn muốn xóa câu hỏi ID #${questionId} khỏi ngân hàng đề?`,
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#e71d36',
        confirmButtonText: 'Đồng ý xóa',
        cancelButtonText: 'Hủy'
    }).then(async (result) => {
        if (result.isConfirmed) {
            try {
                await API.questions.delete(questionId);
                Swal.fire({
                    icon: 'success',
                    title: 'Đã xóa!',
                    text: 'Câu hỏi đã được xóa khỏi hệ thống.',
                    timer: 1500,
                    showConfirmButton: false
                });
                await Promise.all([
                    loadQuestions(),
                    loadTeacherStats()
                ]);
            } catch (err) {
                Swal.fire({
                    icon: 'error',
                    title: 'Lỗi khi xóa',
                    text: err.message || 'Không thể xóa câu hỏi này.'
                });
            }
        }
    });
}

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

/**
 * ══════════════════════════════════════════════════════════════
 * AI QUESTION GENERATOR STUDIO & TEACHER CO-PILOT
 * ══════════════════════════════════════════════════════════════
 */

/**
 * Xử lý khi Giảng viên nhấn nút "Sinh Bộ Câu Hỏi Bằng AI"
 */
async function handleAiGenerateSubmit(e) {
    if (e) e.preventDefault();

    const topicInput = document.getElementById('ai-topic-input');
    const topicName = topicInput ? topicInput.value.trim() : '';
    if (!topicName) {
        Swal.fire({
            icon: 'warning',
            title: 'Chưa nhập môn học / chủ đề',
            text: 'Vui lòng nhập tên môn học hoặc chọn một chủ đề trước khi yêu cầu AI sinh câu hỏi!'
        });
        return;
    }

    const difficulty = document.getElementById('ai-difficulty').value;
    const misconceptionTag = document.getElementById('ai-misconception').value.trim() || 'all';
    
    // Ràng buộc số lượng câu hỏi nhập từ bàn phím là số nguyên >= 1
    const countRaw = (document.getElementById('ai-count').value || '').trim();
    const count = parseInt(countRaw, 10);
    if (isNaN(count) || count < 1 || !Number.isInteger(count)) {
        Swal.fire({
            icon: 'warning',
            title: 'Số lượng câu hỏi không hợp lệ',
            text: 'Vui lòng nhập số lượng câu hỏi là một số nguyên dương từ bàn phím (≥ 1)!'
        });
        return;
    }

    const promptHint = document.getElementById('ai-custom-prompt').value.trim();

    const submitBtn = document.getElementById('btn-generate-ai');
    const submitText = document.getElementById('btn-generate-text');
    const originalText = submitText ? submitText.innerHTML : 'Sinh Bộ Câu Hỏi Bằng AI';

    try {
        if (submitBtn) submitBtn.disabled = true;
        if (submitText) submitText.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Gemini đang tư duy & tạo câu hỏi...';

        const res = await API.teacher.generateQuestions({
            topicName,
            difficulty,
            misconceptionTag,
            count,
            promptHint
        });

        const questions = res.data || [];
        if (!questions || questions.length === 0) {
            throw new Error('AI không trả về danh sách câu hỏi hợp lệ.');
        }

        currentGeneratedQuestions = questions.map((q, idx) => ({
            ...q,
            topicId: q.topicId || 1,
            topicName: q.topicName || topicName,
            imported: false,
            _index: idx
        }));

        renderAiGeneratedCards(currentGeneratedQuestions, topicName);

        const resultsWrapper = document.getElementById('ai-results-wrapper');
        if (resultsWrapper) {
            resultsWrapper.style.display = 'block';
            resultsWrapper.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }

        Swal.fire({
            toast: true,
            position: 'top-end',
            icon: 'success',
            title: `Đã sinh thành công ${currentGeneratedQuestions.length} câu hỏi!`,
            showConfirmButton: false,
            timer: 3000
        });
    } catch (err) {
        console.error('Lỗi khi sinh câu hỏi bằng AI:', err);
        Swal.fire({
            icon: 'error',
            title: 'Lỗi sinh câu hỏi AI',
            text: err.message || 'Không thể kết nối đến AI Gemini hoặc API đang bận. Vui lòng thử lại sau giây lát!'
        });
    } finally {
        if (submitBtn) submitBtn.disabled = false;
        if (submitText) submitText.innerHTML = originalText;
    }
}

/**
 * Hiển thị danh sách thẻ câu hỏi AI vừa sinh
 */
function renderAiGeneratedCards(questions, topicName) {
    const countEl = document.getElementById('ai-generated-count');
    if (countEl) countEl.textContent = questions.length;
    const badgeEl = document.getElementById('ai-target-topic-badge');
    if (badgeEl) badgeEl.textContent = topicName;

    const container = document.getElementById('ai-generated-cards-container');
    if (!container) return;

    if (questions.length === 0) {
        container.innerHTML = '<div class="alert alert-light text-center border">Chưa có câu hỏi nào được tạo.</div>';
        return;
    }

    container.innerHTML = questions.map((q, index) => {
        const correctLetter = normalizeCorrectAnswer(q.correctAnswer);

        // Misconception badge
        let misBadge = '<span class="badge bg-secondary">Chung</span>';
        if (q.misconceptionTag === 'syntax_swap') misBadge = '<span class="badge badge-syntax"><i class="fa-solid fa-code me-1"></i>Syntax Swap</span>';
        else if (q.misconceptionTag === 'boundary_blindness') misBadge = '<span class="badge badge-boundary"><i class="fa-solid fa-arrows-split-up-and-left me-1"></i>Boundary Blindness</span>';
        else if (q.misconceptionTag === 'mental_model_gap') misBadge = '<span class="badge badge-mental"><i class="fa-solid fa-brain me-1"></i>Mental Model Gap</span>';
        else if (q.misconceptionTag === 'logic_flaw') misBadge = '<span class="badge badge-logic"><i class="fa-solid fa-triangle-exclamation me-1"></i>Logic Flaw</span>';

        // Difficulty badge
        let diffBadge = '<span class="badge bg-warning-subtle text-warning border border-warning">Trung bình</span>';
        if (q.difficulty === 'easy') diffBadge = '<span class="badge bg-info-subtle text-info border border-info">Dễ</span>';
        else if (q.difficulty === 'hard') diffBadge = '<span class="badge bg-danger-subtle text-danger border border-danger">Khó</span>';

        // Parse markdown for question text
        let parsedText = '';
        if (typeof marked !== 'undefined' && marked.parse) {
            parsedText = AppUI.renderMarkdown(q.questionText || '');
        } else {
            parsedText = `<p>${escapeHtml(q.questionText || '')}</p>`;
        }

        const isA = correctLetter === 'A';
        const isB = correctLetter === 'B';
        const isC = correctLetter === 'C';
        const isD = correctLetter === 'D';

        const importBtnContent = q.imported
            ? `<button class="btn btn-outline-success btn-sm rounded-pill px-3" disabled><i class="fa-solid fa-circle-check me-1"></i>Đã lưu vào đề</button>`
            : `<button class="btn btn-primary btn-sm rounded-pill px-3 fw-semibold shadow-sm" onclick="importSingleAiQuestion(${index}, this)"><i class="fa-solid fa-plus me-1"></i>Thêm Vào Ngân Hàng Đề</button>`;

        return `
            <div class="ai-question-card p-4 position-relative" id="ai-card-${index}">
                <!-- Card Header -->
                <div class="d-flex align-items-center justify-content-between mb-3 border-bottom pb-2 flex-wrap gap-2">
                    <div class="d-flex align-items-center gap-2">
                        <span class="badge bg-primary text-white rounded-pill px-3 py-1">Câu #${index + 1}</span>
                        ${diffBadge}
                        ${misBadge}
                    </div>
                    <div class="d-flex gap-2">
                        <button class="btn btn-outline-secondary btn-sm rounded-pill px-2" title="Chỉnh sửa chi tiết trong Modal trước khi lưu" onclick="openModalWithAiQuestion(${index})">
                            <i class="fa-solid fa-pen-to-square me-1"></i>Tùy biến
                        </button>
                        <div id="ai-import-btn-wrapper-${index}">
                            ${importBtnContent}
                        </div>
                    </div>
                </div>

                <!-- Question Content (Markdown & Code) -->
                <div class="ai-card-code text-dark mb-3">
                    ${parsedText}
                </div>

                <!-- 4 Options Grid -->
                <div class="row g-2 mb-3">
                    <div class="col-md-6">
                        <div class="ai-option-item ${isA ? 'ai-option-correct' : ''}">
                            <strong>A.</strong> ${escapeHtml(q.optionA || '')}
                            ${isA ? '<i class="fa-solid fa-circle-check ms-1"></i>' : ''}
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="ai-option-item ${isB ? 'ai-option-correct' : ''}">
                            <strong>B.</strong> ${escapeHtml(q.optionB || '')}
                            ${isB ? '<i class="fa-solid fa-circle-check ms-1"></i>' : ''}
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="ai-option-item ${isC ? 'ai-option-correct' : ''}">
                            <strong>C.</strong> ${escapeHtml(q.optionC || '')}
                            ${isC ? '<i class="fa-solid fa-circle-check ms-1"></i>' : ''}
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="ai-option-item ${isD ? 'ai-option-correct' : ''}">
                            <strong>D.</strong> ${escapeHtml(q.optionD || '')}
                            ${isD ? '<i class="fa-solid fa-circle-check ms-1"></i>' : ''}
                        </div>
                    </div>
                </div>

                <!-- Pedagogical Explanation Buffer -->
                <div class="p-3 bg-light rounded-3 border">
                    <div class="d-flex align-items-center justify-content-between mb-1">
                        <span class="small fw-semibold text-primary">
                            <i class="fa-solid fa-shield-halved me-1"></i>Giải thích sư phạm & Bẫy tư duy (AI Fallback Buffer):
                        </span>
                        <span class="badge bg-white text-success border border-success small">Đáp án đúng: ${correctLetter}</span>
                    </div>
                    <p class="mb-0 text-secondary small" style="line-height: 1.5;">${escapeHtml(q.explanation || 'Chưa có giải thích cụ thể.')}</p>
                </div>
            </div>
        `;
    }).join('');

    // Syntax highlight code blocks
    if (typeof hljs !== 'undefined') {
        container.querySelectorAll('pre code').forEach(block => {
            hljs.highlightElement(block);
        });
    }
}

/**
 * Lưu 1 câu hỏi do AI sinh vào Ngân hàng câu hỏi (DB)
 */
async function importSingleAiQuestion(index, btnEl) {
    const q = currentGeneratedQuestions[index];
    if (!q || q.imported) return;

    if (btnEl) {
        btnEl.disabled = true;
        btnEl.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang lưu...';
    }

    try {
        const payload = {
            topicId: q.topicId,
            questionText: q.questionText,
            optionA: q.optionA,
            optionB: q.optionB,
            optionC: q.optionC,
            optionD: q.optionD,
            correctAnswer: normalizeCorrectAnswer(q.correctAnswer),
            explanation: q.explanation || '',
            difficulty: q.difficulty || 'medium',
            misconceptionTag: q.misconceptionTag || null
        };

        await API.questions.create(payload);
        q.imported = true;

        if (btnEl) {
            btnEl.className = 'btn btn-outline-success btn-sm rounded-pill px-3';
            btnEl.innerHTML = '<i class="fa-solid fa-circle-check me-1"></i>Đã lưu vào đề';
            btnEl.disabled = true;
        }

        Swal.fire({
            toast: true,
            position: 'top-end',
            icon: 'success',
            title: `Đã lưu câu #${index + 1} vào ngân hàng đề!`,
            showConfirmButton: false,
            timer: 2000
        });

        // Tải lại danh sách câu hỏi & KPIs
        loadQuestions();
        loadTeacherStats();
    } catch (err) {
        console.error('Lỗi khi lưu câu hỏi AI:', err);
        Swal.fire({
            icon: 'error',
            title: 'Lưu thất bại',
            text: err.message || 'Không thể lưu câu hỏi vào cơ sở dữ liệu.'
        });
        if (btnEl) {
            btnEl.disabled = false;
            btnEl.innerHTML = '<i class="fa-solid fa-plus me-1"></i>Thêm Vào Ngân Hàng Đề';
        }
    }
}

/**
 * Lưu toàn bộ các câu hỏi AI vừa sinh vào DB
 */
async function handleImportAllAiQuestions() {
    const unimported = currentGeneratedQuestions.filter(q => !q.imported);
    if (unimported.length === 0) {
        Swal.fire({
            icon: 'info',
            title: 'Đã lưu hết',
            text: 'Tất cả các câu hỏi trong danh sách này đã được lưu vào ngân hàng đề rồi!'
        });
        return;
    }

    const topicName = unimported[0] ? (topicsMap[unimported[0].topicId] || '') : '';
    const confirmResult = await Swal.fire({
        title: 'Lưu tất cả vào ngân hàng đề?',
        text: `Hệ thống sẽ lưu ${unimported.length} câu hỏi mới vào ngân hàng đề của chủ đề "${topicName}".`,
        icon: 'question',
        showCancelButton: true,
        confirmButtonText: 'Đồng ý lưu tất cả',
        cancelButtonText: 'Hủy'
    });

    if (!confirmResult.isConfirmed) return;

    Swal.fire({
        title: 'Đang lưu danh sách câu hỏi...',
        text: 'Vui lòng chờ trong giây lát...',
        allowOutsideClick: false,
        didOpen: () => {
            Swal.showLoading();
        }
    });

    let successCount = 0;
    let failCount = 0;

    for (let i = 0; i < currentGeneratedQuestions.length; i++) {
        const q = currentGeneratedQuestions[i];
        if (q.imported) continue;

        try {
            await API.questions.create({
                topicId: q.topicId,
                questionText: q.questionText,
                optionA: q.optionA,
                optionB: q.optionB,
                optionC: q.optionC,
                optionD: q.optionD,
                correctAnswer: normalizeCorrectAnswer(q.correctAnswer),
                explanation: q.explanation || '',
                difficulty: q.difficulty || 'medium',
                misconceptionTag: q.misconceptionTag || null
            });
            q.imported = true;
            successCount++;

            // Update button in card
            const btnWrapper = document.getElementById(`ai-import-btn-wrapper-${i}`);
            if (btnWrapper) {
                btnWrapper.innerHTML = `<button class="btn btn-outline-success btn-sm rounded-pill px-3" disabled><i class="fa-solid fa-circle-check me-1"></i>Đã lưu vào đề</button>`;
            }
        } catch (err) {
            console.error(`Lỗi khi lưu câu #${i + 1}:`, err);
            failCount++;
        }
    }

    await Promise.all([
        loadQuestions(),
        loadTeacherStats()
    ]);

    Swal.fire({
        icon: failCount === 0 ? 'success' : 'warning',
        title: 'Hoàn tất lưu câu hỏi',
        text: `Đã lưu thành công ${successCount} câu hỏi vào ngân hàng đề${failCount > 0 ? ` (${failCount} câu bị lỗi)` : ''}.`
    });
}

/**
 * Mở modal thêm/sửa câu hỏi để Giảng viên chỉnh sửa chi tiết câu do AI gợi ý
 */
function openModalWithAiQuestion(index) {
    const q = currentGeneratedQuestions[index];
    if (!q) return;

    document.getElementById('modal-question-id').value = '';
    document.getElementById('questionModalLabel').innerHTML = `<i class="fa-solid fa-wand-magic-sparkles text-warning me-2"></i>Tùy Biến Câu Hỏi AI (Câu #${index + 1})`;
    document.getElementById('modal-topic-id').value = q.topicId || '';
    document.getElementById('modal-difficulty').value = q.difficulty || 'medium';
    document.getElementById('modal-question-text').value = q.questionText || '';
    document.getElementById('modal-option-a').value = q.optionA || '';
    document.getElementById('modal-option-b').value = q.optionB || '';
    document.getElementById('modal-option-c').value = q.optionC || '';
    document.getElementById('modal-option-d').value = q.optionD || '';
    document.getElementById('modal-correct-answer').value = normalizeCorrectAnswer(q.correctAnswer);
    document.getElementById('modal-explanation').value = q.explanation || '';
    document.getElementById('modal-misconception-tag').value = q.misconceptionTag || '';

    if (questionModal) questionModal.show();
}

/**
 * Xử lý trò chuyện với Trợ Lý Sư Phạm AI Co-Pilot
 */
async function handleCopilotChatSubmit(e) {
    if (e) e.preventDefault();
    const inputEl = document.getElementById('copilot-chat-input');
    if (!inputEl) return;
    const message = inputEl.value.trim();
    if (!message) return;

    inputEl.value = '';

    const historyContainer = document.getElementById('copilot-chat-history');
    if (!historyContainer) return;

    // Append user bubble
    const userBubble = document.createElement('div');
    userBubble.className = 'd-flex justify-content-end mb-3';
    userBubble.innerHTML = `
        <div class="chat-bubble-user">
            <div>${escapeHtml(message)}</div>
        </div>
    `;
    historyContainer.appendChild(userBubble);

    // Append loading bubble
    const loadingBubble = document.createElement('div');
    loadingBubble.id = 'copilot-loading-bubble';
    loadingBubble.className = 'd-flex gap-3 mb-3';
    loadingBubble.innerHTML = `
        <div class="rounded-circle bg-primary text-white d-flex align-items-center justify-content-center flex-shrink-0 shadow-sm" style="width: 38px; height: 38px;">
            <i class="fa-solid fa-robot"></i>
        </div>
        <div class="chat-bubble-ai d-flex align-items-center gap-2 py-2.5 px-3">
            <div class="typing-dots">
                <span></span><span></span><span></span>
            </div>
            <span class="text-muted small fst-italic">Co-Pilot đang tư duy & soạn thảo câu trả lời...</span>
        </div>
    `;
    historyContainer.appendChild(loadingBubble);
    historyContainer.scrollTop = historyContainer.scrollHeight;

    try {
        const context = copilotChatHistory.slice(-4).join('\n');
        const res = await API.teacher.chat(message, context);
        const aiText = (res.data && res.data.response) ? res.data.response : 'Co-Pilot không có phản hồi.';

        copilotChatHistory.push('Giảng viên: ' + message);
        copilotChatHistory.push('Co-Pilot: ' + aiText);

        // Remove loading
        const loadingEl = document.getElementById('copilot-loading-bubble');
        if (loadingEl) loadingEl.remove();

        // Parse markdown
        let parsedAiHtml = '';
        if (typeof marked !== 'undefined' && marked.parse) {
            parsedAiHtml = marked.parse(aiText);
        } else {
            parsedAiHtml = `<p>${escapeHtml(aiText)}</p>`;
        }

        const aiBubble = document.createElement('div');
        aiBubble.className = 'd-flex gap-3 mb-3';
        aiBubble.innerHTML = `
            <div class="rounded-circle bg-primary text-white d-flex align-items-center justify-content-center flex-shrink-0" style="width: 36px; height: 36px;">
                <i class="fa-solid fa-robot"></i>
            </div>
            <div class="chat-bubble-ai">
                <div class="ai-chat-content">${parsedAiHtml}</div>
            </div>
        `;
        historyContainer.appendChild(aiBubble);

        // Syntax highlight
        if (typeof hljs !== 'undefined') {
            aiBubble.querySelectorAll('pre code').forEach(block => {
                hljs.highlightElement(block);
            });
        }

        historyContainer.scrollTop = historyContainer.scrollHeight;
    } catch (err) {
        console.error('Lỗi khi chat với Co-Pilot:', err);
        const loadingEl = document.getElementById('copilot-loading-bubble');
        if (loadingEl) loadingEl.remove();

        const errorBubble = document.createElement('div');
        errorBubble.className = 'd-flex gap-3 mb-3';
        errorBubble.innerHTML = `
            <div class="rounded-circle bg-danger text-white d-flex align-items-center justify-content-center flex-shrink-0" style="width: 36px; height: 36px;">
                <i class="fa-solid fa-triangle-exclamation"></i>
            </div>
            <div class="chat-bubble-ai border-danger-subtle bg-danger-subtle text-danger">
                <span class="small">Lỗi kết nối Co-Pilot: ${escapeHtml(err.message || 'Hệ thống bận. Vui lòng thử lại sau.')}</span>
            </div>
        `;
        historyContainer.appendChild(errorBubble);
        historyContainer.scrollTop = historyContainer.scrollHeight;
    }
}

/**
 * Chuẩn hóa ký tự đáp án đúng về A, B, C hoặc D
 */
function normalizeCorrectAnswer(val) {
    if (!val) return 'A';
    const s = String(val).trim().toUpperCase();
    if (s === '0') return 'A';
    if (s === '1') return 'B';
    if (s === '2') return 'C';
    if (s === '3') return 'D';
    const firstChar = s.charAt(0);
    if (['A', 'B', 'C', 'D'].includes(firstChar)) return firstChar;
    return 'A';
}

/**
 * ══════════════════════════════════════════════════════════════
 * AI VALIDATION & MISCONCEPTION GENERATIVE ASSISTANCE
 * ══════════════════════════════════════════════════════════════
 */

/**
 * Thẩm định môn học / chủ đề bằng AI (AI Topic Validation)
 */
async function handleValidateTopic() {
    const topicInput = document.getElementById('ai-topic-input');
    const topicName = topicInput ? topicInput.value.trim() : '';
    const box = document.getElementById('topic-validation-box');
    const btn = document.getElementById('btn-validate-topic');

    if (!topicName) {
        Swal.fire({
            icon: 'info',
            title: 'Chưa nhập môn học',
            text: 'Vui lòng gõ tên môn học hoặc chủ đề vào ô bên dưới trước khi bấm thẩm định!'
        });
        return;
    }

    const originalBtnHtml = btn ? btn.innerHTML : '';
    try {
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Đang thẩm định...';
        }
        if (box) {
            box.style.display = 'block';
            box.innerHTML = '<div class="text-muted small py-1"><span class="spinner-border spinner-border-sm me-1 text-primary"></span>Gemini đang phân tích chương trình học & phạm vi môn...</div>';
        }

        const res = await API.teacher.validateTopic(topicName);
        const data = res.data || {};

        let subtopicsHtml = '';
        if (data.suggestedSubtopics && data.suggestedSubtopics.length > 0) {
            const chips = data.suggestedSubtopics.map(st => `
                <button type="button" class="btn btn-sm btn-outline-secondary py-0 px-2 rounded-pill small me-1 mb-1" onclick="applySubtopicHint('${escapeHtml(st)}')">
                    + ${escapeHtml(st)}
                </button>
            `).join('');
            subtopicsHtml = `
                <div class="mt-2 pt-2 border-top">
                    <span class="text-muted small fw-semibold d-block mb-1">Gợi ý phân nhánh chủ đề (bấm để thêm vào yêu cầu):</span>
                    <div class="d-flex flex-wrap">${chips}</div>
                </div>
            `;
        }

        if (box) {
            box.style.display = 'block';
            box.innerHTML = `
                <div class="alert alert-primary-subtle border border-primary-subtle py-2 px-3 mb-0 rounded-3 small text-dark shadow-sm">
                    <div class="d-flex align-items-center justify-content-between mb-1">
                        <span class="fw-bold text-primary">
                            <i class="fa-solid fa-circle-check text-success me-1"></i>Lĩnh vực: ${escapeHtml(data.field || 'Đa ngành / Tổng hợp')}
                        </span>
                        <span class="badge bg-primary text-white rounded-pill px-2">Độ phù hợp: ${escapeHtml(data.clarity || 'high')}</span>
                    </div>
                    <div class="text-secondary">${escapeHtml(data.feedback || 'Chủ đề hợp lệ và sẵn sàng tạo đề.')}</div>
                    ${subtopicsHtml}
                </div>
            `;
        }
    } catch (err) {
        console.error('Lỗi khi thẩm định chủ đề:', err);
        if (box) {
            box.style.display = 'block';
            box.innerHTML = `
                <div class="alert alert-warning py-2 px-3 mb-0 rounded-3 small">
                    <i class="fa-solid fa-triangle-exclamation me-1"></i>${escapeHtml(err.message || 'Không thể kết nối đến bộ thẩm định AI.')}
                </div>
            `;
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalBtnHtml;
        }
    }
}

function applySubtopicHint(subtopic) {
    const hintInput = document.getElementById('ai-custom-prompt');
    if (hintInput) {
        hintInput.value = hintInput.value ? `${hintInput.value}, ${subtopic}` : `Tập trung vào: ${subtopic}`;
        hintInput.focus();
    }
}

/**
 * AI Gợi ý bẫy tư duy đặc thù cho môn học đang nhập
 */
async function handleSuggestMisconceptions() {
    const topicInput = document.getElementById('ai-topic-input');
    const topicName = topicInput ? topicInput.value.trim() : '';
    const btn = document.getElementById('btn-suggest-misconceptions');
    const chipsContainer = document.getElementById('ai-misconceptions-chips-container');
    const chipsBox = document.getElementById('ai-misconceptions-chips');
    const datalist = document.getElementById('misconception-datalist');

    if (!topicName) {
        Swal.fire({
            icon: 'info',
            title: 'Chưa có tên môn học',
            text: 'Vui lòng nhập tên môn học / chủ đề trước để AI tìm các bẫy tư duy chính xác nhất cho môn này!'
        });
        if (topicInput) topicInput.focus();
        return;
    }

    const originalBtnHtml = btn ? btn.innerHTML : '';
    try {
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>AI đang phân tích bẫy...';
        }

        const res = await API.teacher.suggestMisconceptions(topicName);
        const list = res.data || [];

        if (list.length === 0) {
            throw new Error('Không nhận được danh sách bẫy tư duy từ AI.');
        }

        // Cập nhật datalist
        if (datalist) {
            datalist.innerHTML = list.map(item => `
                <option value="${escapeHtml(item.tag || item.label)}">${escapeHtml(item.label)} - ${escapeHtml(item.description)}</option>
            `).join('');
        }

        // Hiển thị chips để người dùng bấm chọn nhanh
        if (chipsContainer && chipsBox) {
            chipsContainer.style.display = 'block';
            chipsBox.innerHTML = list.map(item => `
                <button type="button" class="btn btn-outline-danger btn-sm rounded-pill py-1 px-3 small fw-semibold text-start shadow-sm"
                        onclick="selectMisconception('${escapeHtml(item.tag || item.label)}')">
                    <i class="fa-solid fa-crosshairs me-1"></i>${escapeHtml(item.label)}
                    <span class="d-block text-muted fw-normal" style="font-size: 0.72rem;">${escapeHtml(item.description)}</span>
                </button>
            `).join('');
        }

        Swal.fire({
            icon: 'success',
            title: 'Đã gợi ý bẫy tư duy!',
            text: `AI đã phân tích ${list.length} bẫy nhận thức phổ biến cho môn "${topicName}". Bạn có thể bấm chọn ngay bên dưới!`,
            timer: 2000,
            showConfirmButton: false
        });

    } catch (err) {
        console.error('Lỗi khi gợi ý bẫy tư duy:', err);
        Swal.fire({
            icon: 'error',
            title: 'Lỗi gợi ý bẫy',
            text: err.message || 'Không thể tải bẫy tư duy từ AI.'
        });
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalBtnHtml;
        }
    }
}

function selectMisconception(val) {
    const input = document.getElementById('ai-misconception');
    if (input) {
        input.value = val;
        input.focus();
    }
}

/**
 * Thẩm định bẫy tư duy bằng AI (AI Misconception Validation)
 */
async function handleValidateMisconception() {
    const topicInput = document.getElementById('ai-topic-input');
    const topicName = topicInput ? topicInput.value.trim() : 'Tổng hợp';
    const miscInput = document.getElementById('ai-misconception');
    const misconception = miscInput ? miscInput.value.trim() : '';
    const btn = document.getElementById('btn-validate-misconception');
    const box = document.getElementById('misconception-validation-box');

    if (!misconception) {
        Swal.fire({
            icon: 'info',
            title: 'Chưa nhập bẫy tư duy',
            text: 'Vui lòng nhập tên bẫy tư duy hoặc chọn một gợi ý trước khi thẩm định!'
        });
        return;
    }

    const originalBtnHtml = btn ? btn.innerHTML : '';
    try {
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Thẩm định...';
        }
        if (box) {
            box.style.display = 'block';
            box.innerHTML = '<div class="text-muted small py-1"><span class="spinner-border spinner-border-sm me-1 text-primary"></span>Đang thẩm định tính phân hóa của bẫy tư duy...</div>';
        }

        const res = await API.teacher.validateMisconception(topicName, misconception);
        const data = res.data || {};

        if (box) {
            box.style.display = 'block';
            box.innerHTML = `
                <div class="alert alert-success-subtle border border-success-subtle py-2 px-3 mb-0 rounded-3 small text-dark shadow-sm">
                    <div class="fw-bold text-success mb-1">
                        <i class="fa-solid fa-shield-check me-1"></i>Bẫy tư duy: "${escapeHtml(data.misconception || misconception)}"
                    </div>
                    <div class="mb-1">${escapeHtml(data.feedback || 'Bẫy nhận thức phù hợp.')}</div>
                    ${data.distractorTip ? `<div class="text-muted fst-italic"><i class="fa-regular fa-lightbulb text-warning me-1"></i><strong>Chiến lược bẫy:</strong> ${escapeHtml(data.distractorTip)}</div>` : ''}
                </div>
            `;
        }
    } catch (err) {
        console.error('Lỗi khi thẩm định bẫy:', err);
        if (box) {
            box.style.display = 'block';
            box.innerHTML = `
                <div class="alert alert-warning py-2 px-3 mb-0 rounded-3 small">
                    <i class="fa-solid fa-triangle-exclamation me-1"></i>${escapeHtml(err.message || 'Lỗi kết nối thẩm định.')}
                </div>
            `;
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = originalBtnHtml;
        }
    }
}

/**
 * AI Hỗ trợ hoàn thiện câu hỏi tự động bên trong Modal thêm câu hỏi
 */
async function handleModalAiAssist() {
    const questionTextInput = document.getElementById('modal-question-text');
    const questionText = questionTextInput ? questionTextInput.value.trim() : '';
    const topicSelect = document.getElementById('modal-topic-id');
    const topicName = topicSelect && topicSelect.selectedIndex >= 0 ? topicSelect.options[topicSelect.selectedIndex].text : 'Kiến thức chung';
    const difficulty = document.getElementById('modal-difficulty').value || 'medium';

    if (!questionText) {
        Swal.fire({
            icon: 'info',
            title: 'Chưa có nội dung câu hỏi',
            text: 'Vui lòng gõ một câu hỏi hoặc ý tưởng câu hỏi vào ô "Nội dung câu hỏi" trước để AI tự động điền các phương án A, B, C, D và lời giải!'
        });
        if (questionTextInput) questionTextInput.focus();
        return;
    }

    const btn = document.getElementById('btn-modal-ai-assist');
    const btnText = document.getElementById('btn-modal-ai-assist-text');
    const originalText = btnText ? btnText.innerHTML : 'AI Tự Động Điền Phương Án';

    try {
        if (btn) btn.disabled = true;
        if (btnText) btnText.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Gemini đang soạn 4 phương án...';

        const res = await API.teacher.assistQuestion(topicName, questionText, difficulty);
        const data = res.data || {};

        if (data.optionA) document.getElementById('modal-option-a').value = data.optionA;
        if (data.optionB) document.getElementById('modal-option-b').value = data.optionB;
        if (data.optionC) document.getElementById('modal-option-c').value = data.optionC;
        if (data.optionD) document.getElementById('modal-option-d').value = data.optionD;
        if (data.correctAnswer) document.getElementById('modal-correct-answer').value = normalizeCorrectAnswer(data.correctAnswer);
        if (data.explanation) document.getElementById('modal-explanation').value = data.explanation;
        if (data.misconceptionTag) document.getElementById('modal-misconception-tag').value = data.misconceptionTag;

        Swal.fire({
            icon: 'success',
            title: '✨ AI Đã Hoàn Thiện Câu Hỏi!',
            text: 'Đã tự động điền đầy đủ 4 phương án, chỉ định đáp án đúng, phân loại bẫy tư duy và viết lời giải thích sư phạm. Bạn có thể xem lại và bấm "Lưu Câu Hỏi"!',
            timer: 2500,
            showConfirmButton: false
        });

    } catch (err) {
        console.error('Lỗi khi AI hỗ trợ soạn câu hỏi:', err);
        Swal.fire({
            icon: 'error',
            title: 'Lỗi Trợ Lý AI',
            text: err.message || 'Không thể hoàn thiện câu hỏi tự động. Vui lòng thử lại.'
        });
    } finally {
        if (btn) btn.disabled = false;
        if (btnText) btnText.innerHTML = originalText;
    }
}

/**
 * Gợi ý bẫy tư duy ngay trong Modal Thêm Câu Hỏi
 */
async function handleModalSuggestMisconception() {
    const topicSelect = document.getElementById('modal-topic-id');
    const topicName = topicSelect && topicSelect.selectedIndex >= 0 ? topicSelect.options[topicSelect.selectedIndex].text : 'Kiến thức chung';

    try {
        Swal.fire({
            title: 'Đang tải gợi ý bẫy tư duy...',
            didOpen: () => Swal.showLoading()
        });

        const res = await API.teacher.suggestMisconceptions(topicName);
        const list = res.data || [];

        if (list.length === 0) {
            Swal.fire({
                icon: 'info',
                title: 'Không có gợi ý',
                text: 'Chưa tìm thấy bẫy tư duy đặc thù cho môn này.'
            });
            return;
        }

        const inputOptions = {};
        list.forEach(item => {
            inputOptions[item.tag || item.label] = `${item.label} (${item.description})`;
        });

        const { value: selectedTag } = await Swal.fire({
            title: `Bẫy tư duy gợi ý cho: ${topicName}`,
            input: 'select',
            inputOptions: inputOptions,
            inputPlaceholder: '-- Chọn một bẫy nhận thức --',
            showCancelButton: true,
            confirmButtonText: 'Chọn bẫy này',
            cancelButtonText: 'Đóng'
        });

        if (selectedTag) {
            document.getElementById('modal-misconception-tag').value = selectedTag;
        }
    } catch (err) {
        console.error('Lỗi gợi ý bẫy trong modal:', err);
        Swal.fire({
            icon: 'error',
            title: 'Lỗi',
            text: err.message || 'Không thể tải bẫy tư duy.'
        });
    }
}

/**
 * Tạo môn học / chủ đề mới nhanh từ trong Modal thêm câu hỏi
 */
async function handleQuickAddTopicFromModal() {
    const { value: newTopicName } = await Swal.fire({
        title: 'Tạo Môn Học / Chủ Đề Mới',
        input: 'text',
        inputLabel: 'Tên môn học hoặc chủ đề bài kiểm tra:',
        inputPlaceholder: 'Ví dụ: Thiết Kế Web, Triết Học, Giải Tích...',
        showCancelButton: true,
        confirmButtonText: 'Khởi Tạo Môn',
        cancelButtonText: 'Hủy',
        inputValidator: (value) => {
            if (!value || !value.trim()) {
                return 'Tên môn học không được để trống!';
            }
        }
    });

    if (!newTopicName) return;

    try {
        const createRes = await API.topics.create({
            topicName: newTopicName.trim(),
            description: 'Khởi tạo trực tiếp từ trình soạn thảo câu hỏi'
        });

        await loadTopics();

        const createdId = createRes.data ? createRes.data.topicId : null;
        if (createdId) {
            document.getElementById('modal-topic-id').value = createdId;
        }

        Swal.fire({
            icon: 'success',
            title: 'Đã tạo môn mới!',
            text: `Môn "${newTopicName.trim()}" đã được thêm và chọn làm chủ đề cho câu hỏi này.`,
            timer: 1500,
            showConfirmButton: false
        });
    } catch (err) {
        console.error('Lỗi khi tạo môn mới từ modal:', err);
        Swal.fire({
            icon: 'error',
            title: 'Lỗi tạo môn',
            text: err.message || 'Không thể tạo môn học mới.'
        });
    }
}

