/**
 * Result Page Logic (LMS Thông Minh)
 * Bao gồm:
 * - 1.3. Bảo vệ luồng điều hướng (Guard Routes)
 * - 2.3. Thẻ Khắc Phục Lỗi (Remediation Card) & Modal Mini-Quiz Tự Động Vá Lỗ Hổng
 * - Hiệu ứng pháo hoa Confetti khi hoàn thành bài tập phục hồi
 * - Hiển thị bài học củng cố AI & Hỏi Trợ Giảng ảo
 */

// 1. Kiểm tra xác thực
const currentUser = API.auth.requireAuth();

// 2. State trang kết quả
const urlParams = new URLSearchParams(window.location.search);
let sessionId = urlParams.get('sessionId');

let currentQuizResult = null;
let activeMisconception = null;
let remediationQuestions = [];
let remediationModal = null;

document.addEventListener('DOMContentLoaded', () => {
    // Khởi tạo Bootstrap Modal
    const modalEl = document.getElementById('remediationModal');
    if (modalEl && typeof bootstrap !== 'undefined') {
        remediationModal = new bootstrap.Modal(modalEl);
    }

    if (currentUser) {
        const usernameEl = document.getElementById('nav-username');
        const fullnameEl = document.getElementById('nav-fullname');
        if (usernameEl) usernameEl.textContent = currentUser.username;
        if (fullnameEl) fullnameEl.textContent = currentUser.fullName || currentUser.username;

        if (API.auth.isTeacher()) {
            const teacherLink = document.getElementById('nav-teacher-link');
            if (teacherLink) teacherLink.classList.remove('d-none');
            const dropdownTeacherItem = document.getElementById('dropdown-teacher-item');
            if (dropdownTeacherItem) dropdownTeacherItem.classList.remove('d-none');
        }
    }

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
                if (result.isConfirmed) {
                    API.auth.logout();
                }
            });
        });
    }

    // 1.3. Guard Route: Kiểm tra xem có sessionId hoặc cache không
    const cached = sessionStorage.getItem('last_quiz_result');
    if (!sessionId && !cached) {
        Swal.fire({
            icon: 'warning',
            title: 'Chưa có bài thi',
            text: 'Không tìm thấy phiên làm bài nào. Vui lòng chọn chủ đề và làm bài trước khi xem kết quả!',
            confirmButtonText: 'Về trang chủ'
        }).then(() => {
            window.location.href = 'index.html';
        });
        return;
    }

    // Gắn sự kiện nút nộp bài mini-quiz vá lỗi
    const btnSubmitRemediation = document.getElementById('btn-submit-remediation');
    if (btnSubmitRemediation) {
        btnSubmitRemediation.addEventListener('click', handleSubmitRemediation);
    }

    // Nạp dữ liệu kết quả
    initResult();
});

/**
 * Khởi tạo và nạp dữ liệu kết quả thi
 */
async function initResult() {
    let resultData = null;

    // 1. Thử lấy từ sessionStorage để hiển thị tức thì
    const cached = sessionStorage.getItem('last_quiz_result');
    if (cached) {
        try {
            const parsed = JSON.parse(cached);
            if (parsed && (!sessionId || parsed.sessionId == sessionId)) {
                resultData = parsed;
                if (!sessionId && parsed.sessionId) sessionId = parsed.sessionId;
            }
        } catch (e) {}
    }

    // 2. Nếu chưa có, tải từ Backend API
    if (!resultData && sessionId) {
        try {
            const res = await API.quiz.session(sessionId);
            resultData = res.data;
        } catch (err) {
            console.error('Không thể tải chi tiết phiên thi:', err);
        }
    }

    if (!resultData) {
        document.getElementById('result-headline').textContent = 'Không tìm thấy dữ liệu bài kiểm tra.';
        return;
    }

    // Dọn dẹp triệt để localStorage cho bài thi này
    if (resultData.topicId) {
        localStorage.removeItem(`quiz_progress_${resultData.topicId}`);
    }

    currentQuizResult = resultData;
    renderResult(resultData);
}

/**
 * Render toàn bộ giao diện kết quả thi
 */
function renderResult(data) {
    const sessionObj = data.session || data;
    const total = data.totalQuestions || sessionObj.totalQuestions || 0;
    const correct = data.correctCount !== undefined ? data.correctCount : (sessionObj.correctCount || 0);
    const wrong = Math.max(0, total - correct);

    // Tính điểm thang 10 & phần trăm chính xác (%)
    let percent = 0;
    let score10 = 0;

    if (data.percentage !== undefined) {
        percent = Number(data.percentage);
    } else if (total > 0) {
        percent = (correct / total) * 100;
    }

    if (data.score !== undefined) {
        score10 = Number(data.score);
    } else if (sessionObj.score !== undefined) {
        score10 = Number(sessionObj.score);
    } else if (total > 0) {
        score10 = (correct / total) * 10;
    }

    // Nếu dữ liệu cũ lưu score theo thang 100 (score > 10):
    if (score10 > 10) {
        percent = score10;
        score10 = percent / 10;
    }

    // Làm tròn hiển thị
    const roundedPercent = Math.round(percent);
    const formattedScore10 = (Math.round(score10 * 10) / 10).toFixed(1);

    // Tính số câu đoán mò (GUESS)
    const graded = data.gradedAnswers || data.answers || [];
    const guessCount = graded.filter(a => a.confidenceLevel === 'GUESS').length;

    const scoreValEl = document.getElementById('score-value');
    if (scoreValEl) scoreValEl.textContent = `${roundedPercent}%`;

    const scoreScaleEl = document.getElementById('score-scale');
    if (scoreScaleEl) scoreScaleEl.textContent = `${formattedScore10} / 10 điểm`;

    const circleEl = document.getElementById('score-circle');
    if (circleEl) {
        circleEl.className = 'd-inline-flex flex-column align-items-center justify-content-center rounded-circle shadow-sm';
        if (roundedPercent >= 80) {
            circleEl.classList.add('bg-success-subtle', 'text-success');
        } else if (roundedPercent >= 50) {
            circleEl.classList.add('bg-primary-subtle', 'text-primary');
        } else {
            circleEl.classList.add('bg-warning-subtle', 'text-warning');
        }
    }

    document.getElementById('correct-count').textContent = correct;
    document.getElementById('wrong-count').textContent = wrong;
    document.getElementById('guess-count').textContent = guessCount;

    // Tiêu đề nhận xét theo tỷ lệ %
    const headlineEl = document.getElementById('result-headline');
    if (headlineEl) {
        if (roundedPercent >= 90) {
            headlineEl.textContent = 'Xuất Sắc! Bạn đã làm chủ rất tốt kiến thức!';
        } else if (roundedPercent >= 70) {
            headlineEl.textContent = 'Rất Tốt! Tuy nhiên vẫn còn điểm cần lưu ý.';
        } else if (roundedPercent >= 50) {
            headlineEl.textContent = 'Khá Tốt! Hãy ôn thêm một số khái niệm còn phân vân.';
        } else {
            headlineEl.textContent = 'Cố Gắng Lên! Đọc kỹ phân tích AI và làm bài tập phục hồi bên dưới nhé.';
        }
    }

    // Render Bài Học Củng Cố AI (Remedial Lessons)
    const remedialList = data.remedialLessons || [];
    document.getElementById('remedial-badge').textContent = `${remedialList.length} bài học`;

    const remedialContainer = document.getElementById('remedial-container');
    if (remedialList.length === 0) {
        remedialContainer.innerHTML = `
            <div class="alert alert-success border-0 shadow-sm rounded-4 p-4 text-center">
                <i class="fa-solid fa-circle-check fa-2x mb-2 text-success"></i>
                <h6 class="fw-bold">Chúc mừng! Bạn đã trả lời đúng tất cả các câu hỏi và không câu nào đoán mò!</h6>
                <p class="small mb-0 text-muted">Bạn có một nền tảng tư duy rất vững chắc trong chủ đề này.</p>
            </div>
        `;
    } else {
        remedialContainer.innerHTML = remedialList.map((lesson, idx) => {
            const badgeClass = getMisconceptionBadgeClass(lesson.misconceptionType);
            const typeLabel = getMisconceptionLabel(lesson.misconceptionType);
            const renderedLesson = typeof marked !== 'undefined' ? marked.parse(lesson.lessonContent || '') : lesson.lessonContent;

            return `
                <div class="card remedial-card border-0 p-4 mb-3 shadow-sm rounded-4 bg-white">
                    <div class="d-flex justify-content-between align-items-start mb-2 flex-wrap gap-2">
                        <span class="badge ${badgeClass} misconception-badge px-3 py-2 rounded-pill">
                            <i class="fa-solid fa-tag me-1"></i>${typeLabel}
                        </span>
                        <button class="btn btn-outline-primary btn-sm rounded-pill px-3" onclick="askAITutor('${encodeURIComponent(lesson.errorReason || '')}')">
                            <i class="fa-solid fa-comments me-1"></i>Hỏi Trợ Giảng Về Lỗi Này
                        </button>
                    </div>

                    <!-- Chẩn đoán nguyên nhân -->
                    <div class="mb-3">
                        <h6 class="fw-bold text-dark mb-1">🔍 Chẩn Đoán Của AI:</h6>
                        <p class="text-muted small mb-0">${lesson.errorReason || 'Chưa có thông tin chẩn đoán.'}</p>
                    </div>

                    <!-- Nội dung bài học củng cố -->
                    <div class="bg-light p-3 rounded-3 mb-3 border">
                        <div class="text-dark markdown-body small">
                            ${renderedLesson}
                        </div>
                    </div>

                    <!-- Câu hỏi thực hành nhanh -->
                    ${lesson.practiceQuestion ? `
                        <div class="p-3 bg-primary-subtle border border-primary-subtle rounded-3">
                            <div class="fw-bold text-primary small mb-1">
                                <i class="fa-solid fa-pen-nib me-1"></i>Thực Hành Nhanh (Làm Lại Kiến Thức):
                            </div>
                            <div class="small text-dark mb-2">${lesson.practiceQuestion}</div>
                        </div>
                    ` : ''}
                </div>
            `;
        }).join('');

        // Highlight code blocks
        if (typeof hljs !== 'undefined') {
            document.querySelectorAll('pre code').forEach((el) => {
                hljs.highlightElement(el);
            });
        }
    }

    // 2.3. Khởi tạo Thẻ Khắc Phục Lỗi (Adaptive Remediation Card) nếu phát hiện lỗ hổng
    setupAdaptiveRemediation(remedialList, data);

    // Render Danh Sách Xem Lại Từng Câu Hỏi kèm Diễn Đàn & Tín Nhiệm Phản Biện
    const reviewList = document.getElementById('questions-review-list');
    if (graded.length > 0) {
        reviewList.innerHTML = graded.map((ans, idx) => `
            <div class="card border rounded-4 mb-3 overflow-hidden shadow-sm ${ans.isCorrect ? 'border-success-subtle bg-white' : 'border-danger-subtle bg-white'}">
                <div class="card-body p-4">
                    <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
                        <span class="fw-bold text-dark fs-6">Câu ${idx + 1}: ${escapeHtml(ans.questionText || '')}</span>
                        <div class="d-flex align-items-center gap-2">
                            <span class="badge ${ans.isCorrect ? 'bg-success' : 'bg-danger'} rounded-pill px-3 py-1">
                                ${ans.isCorrect ? '<i class="fa-solid fa-circle-check me-1"></i>ĐÚNG' : '<i class="fa-solid fa-circle-xmark me-1"></i>SAI'}
                            </span>
                            <span class="badge bg-light text-muted border rounded-pill px-2 py-1" id="cred-badge-${ans.questionId}">
                                <i class="fa-solid fa-shield-halved me-1 text-primary"></i>Độ tin cậy: ...
                            </span>
                        </div>
                    </div>
                    <div class="small d-flex flex-wrap gap-3 mb-2 py-2 px-3 bg-light rounded-3 border">
                        <span>Lựa chọn của bạn: <strong class="${ans.isCorrect ? 'text-success' : 'text-danger'}">${ans.chosenAnswer || '(Bỏ trống)'}</strong></span>
                        <span>Đáp án chuẩn: <strong class="text-success">${ans.correctAnswer}</strong></span>
                        <span>Mức độ tự tin: <strong class="${ans.confidenceLevel === 'GUESS' ? 'text-warning' : 'text-success'}">${ans.confidenceLevel === 'GUESS' ? 'Đoán mò' : 'Chắc chắn'}</strong></span>
                    </div>
                    ${ans.explanation ? `
                        <div class="text-muted small pt-2 mb-3">
                            <strong class="text-secondary"><i class="fa-solid fa-lightbulb text-warning me-1"></i>Giải thích:</strong> ${escapeHtml(ans.explanation)}
                        </div>
                    ` : ''}

                    <!-- ── Thanh Đánh Giá Độ Tin Cậy & Phản Biện ── -->
                    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 pt-3 border-top">
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-outline-success rounded-pill px-3" id="btn-upvote-${ans.questionId}" onclick="voteQuestion(${ans.questionId}, 'UPVOTE')">
                                <i class="fa-solid fa-thumbs-up me-1"></i>Hữu ích (<span id="upvotes-cnt-${ans.questionId}">0</span>)
                            </button>
                            <button class="btn btn-sm btn-outline-secondary rounded-pill px-3" id="btn-downvote-${ans.questionId}" onclick="voteQuestion(${ans.questionId}, 'DOWNVOTE')">
                                <i class="fa-solid fa-thumbs-down me-1"></i>Chưa chuẩn (<span id="downvotes-cnt-${ans.questionId}">0</span>)
                            </button>
                            <button class="btn btn-sm btn-outline-danger rounded-pill px-3" id="btn-report-${ans.questionId}" onclick="reportQuestionPrompt(${ans.questionId})">
                                <i class="fa-solid fa-flag me-1"></i>Báo lỗi / Ảo giác AI
                            </button>
                        </div>
                        <div>
                            <button class="btn btn-sm btn-outline-primary rounded-pill px-3" onclick="toggleComments(${ans.questionId})">
                                <i class="fa-solid fa-comments me-1"></i>Thảo luận & Phản biện (<span id="comments-cnt-${ans.questionId}">0</span>)
                            </button>
                        </div>
                    </div>

                    <!-- ── Khung Bình Luận Phản Biện Mở Rộng ── -->
                    <div id="comments-section-${ans.questionId}" class="mt-3 pt-3 border-top" style="display: none;">
                        <h6 class="fw-bold small text-dark mb-2">
                            <i class="fa-solid fa-users-viewfinder text-primary me-1"></i>Diễn đàn thảo luận & phản biện câu hỏi này
                        </h6>
                        <div id="comments-list-${ans.questionId}" class="d-flex flex-column gap-2 mb-3">
                            <div class="text-muted small text-center py-2"><span class="spinner-border spinner-border-sm me-1"></span>Đang tải bình luận...</div>
                        </div>

                        <!-- Form gửi bình luận -->
                        <div class="d-flex gap-2">
                            <input type="text" class="form-control form-control-sm rounded-pill px-3" id="comment-input-${ans.questionId}" placeholder="Viết phản biện, chia sẻ góc nhìn hoặc hỏi đồng nghiệp..." onkeydown="if(event.key==='Enter') submitComment(${ans.questionId})">
                            <button class="btn btn-primary btn-sm rounded-pill px-3" onclick="submitComment(${ans.questionId})">
                                <i class="fa-solid fa-paper-plane me-1"></i>Gửi
                            </button>
                        </div>
                    </div>

                </div>
            </div>
        `).join('');

        // Tải độ tín nhiệm ban đầu cho từng câu hỏi
        graded.forEach(ans => {
            loadCredibility(ans.questionId);
        });
    }
}

/**
 * 2.3. Kiểm tra và thiết lập Thẻ Khắc Phục Lỗi (Adaptive Remediation Card)
 */
function setupAdaptiveRemediation(remedialList, data) {
    const wrapper = document.getElementById('adaptive-remediation-wrapper');
    if (!wrapper) return;

    if (!remedialList || remedialList.length === 0) {
        wrapper.classList.add('d-none');
        return;
    }

    // Lấy dạng lỗi phổ biến nhất trong các bài học củng cố
    let detectedType = null;
    for (const r of remedialList) {
        if (r.misconceptionType && r.misconceptionType !== 'other') {
            detectedType = r.misconceptionType;
            break;
        }
    }
    if (!detectedType && remedialList.length > 0) {
        detectedType = remedialList[0].misconceptionType || 'syntax_swap';
    }

    activeMisconception = detectedType;
    const typeLabel = getMisconceptionLabel(detectedType);

    document.getElementById('remediation-tag-badge').innerHTML = `<i class="fa-solid fa-triangle-exclamation me-1"></i>Phát hiện: ${typeLabel}`;
    document.getElementById('remediation-title').textContent = `Lộ Trình Khắc Phục: ${typeLabel}`;
    document.getElementById('remediation-desc').textContent =
        `Hệ thống AI nhận thấy bạn đang gặp vướng mắc ở dạng này. Hãy hoàn thành 3 câu hỏi nhanh dưới đây để làm chủ kiến thức và nhận huy hiệu phục hồi!`;

    const actionContainer = document.getElementById('remediation-action-container');
    actionContainer.innerHTML = `
        <button class="btn btn-warning text-dark fw-bold px-4 py-2 rounded-pill shadow" id="btn-start-remediation" onclick="startAdaptiveRemediation()">
            <i class="fa-solid fa-bolt me-1"></i>Bắt đầu bài tập phục hồi (3 phút)
        </button>
    `;

    wrapper.classList.remove('d-none');
}

/**
 * Mở Modal Mini-Quiz và tải 2-3 câu hỏi ôn tập theo lỗ hổng tư duy
 */
async function startAdaptiveRemediation() {
    if (!remediationModal) return;

    const modalBody = document.getElementById('remediation-modal-body');
    modalBody.innerHTML = `
        <div class="text-center py-4 position-relative overflow-hidden">
            <div class="ai-orb-container mb-3" style="width: 80px; height: 80px;">
                <div class="ai-orb-ring-outer"></div>
                <div class="ai-orb-ring-inner"></div>
                <div class="ai-orb-core" style="width: 52px; height: 52px;">
                    <i class="fa-solid fa-wand-magic-sparkles fa-lg text-white"></i>
                </div>
            </div>
            <h6 class="fw-bold text-dark mb-1">AI đang tuyển chọn 3 bài tập phục hồi tối ưu...</h6>
            <p class="text-muted small mb-3">Tự động thiết kế bẫy tư duy phản biện để vá lỗ hổng nhận thức.</p>
            <div class="loading-progress-track mx-auto mb-2" style="max-width: 280px;">
                <div class="loading-progress-bar"></div>
            </div>
            <div class="d-flex align-items-center justify-content-center gap-2 text-muted small">
                <div class="typing-dots"><span></span><span></span><span></span></div>
                <span class="fst-italic" style="font-size: 0.78rem;">Đang kết nối hệ thống Gemini AI...</span>
            </div>
        </div>
    `;

    remediationModal.show();

    const topicId = (currentQuizResult && currentQuizResult.topicId) ? currentQuizResult.topicId : 1;

    try {
        const res = await API.remediation.get(topicId, activeMisconception);
        remediationQuestions = res.data || [];

        if (remediationQuestions.length === 0) {
            modalBody.innerHTML = `
                <div class="alert alert-info border-0 p-4 text-center">
                    <i class="fa-solid fa-circle-check fa-2x text-info mb-2"></i>
                    <h6>Không còn câu hỏi nào cần ôn tập cho phần này!</h6>
                    <p class="small text-muted mb-0">Bạn đã hoàn thành tốt các kiến thức trọng tâm.</p>
                </div>
            `;
            document.getElementById('btn-submit-remediation').style.display = 'none';
            return;
        }

        document.getElementById('btn-submit-remediation').style.display = 'inline-block';
        renderRemediationQuiz(remediationQuestions);

    } catch (err) {
        modalBody.innerHTML = `
            <div class="alert alert-danger border-0 p-4">
                <i class="fa-solid fa-triangle-exclamation me-1"></i>Lỗi tải bài tập: ${err.message}
            </div>
        `;
    }
}

/**
 * Render giao diện Mini-Quiz trong Modal
 */
function renderRemediationQuiz(qList) {
    const modalBody = document.getElementById('remediation-modal-body');
    modalBody.innerHTML = `
        <div class="mb-3 p-3 rounded-3 bg-light border">
            <div class="fw-semibold text-dark small">
                <i class="fa-solid fa-bullseye text-warning me-1"></i>
                Mục tiêu: Trả lời đúng các câu hỏi để làm chủ dạng lỗi <strong>${getMisconceptionLabel(activeMisconception)}</strong>.
            </div>
        </div>
        <form id="remediation-form">
            ${qList.map((q, idx) => `
                <div class="card border rounded-3 p-3 mb-3 bg-white">
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <span class="badge bg-primary text-white rounded-pill">Câu ${idx + 1}</span>
                        <span class="badge bg-light text-muted border">${q.difficulty || 'medium'}</span>
                    </div>
                    <div class="fw-bold text-dark mb-3">${escapeHtml(q.questionText)}</div>
                    <div class="options-container">
                        ${['A', 'B', 'C', 'D'].map(key => {
                            const optText = q['option' + key];
                            if (!optText) return '';
                            return `
                                <div class="form-check mb-2">
                                    <input class="form-check-input" type="radio" name="remed_opt_${q.questionId}" id="remed_${q.questionId}_${key}" value="${key}" required>
                                    <label class="form-check-label text-dark" for="remed_${q.questionId}_${key}">
                                        <strong>${key}.</strong> ${escapeHtml(optText)}
                                    </label>
                                </div>
                            `;
                        }).join('')}
                    </div>
                </div>
            `).join('')}
        </form>
    `;
}

/**
 * Nộp bài mini-quiz và xử lý hiệu ứng Pháo hoa (Confetti)
 */
async function handleSubmitRemediation() {
    if (remediationQuestions.length === 0) return;

    // Thu thập câu trả lời
    const answers = [];
    for (const q of remediationQuestions) {
        const checked = document.querySelector(`input[name="remed_opt_${q.questionId}"]:checked`);
        if (!checked) {
            Swal.fire({
                icon: 'warning',
                title: 'Chưa hoàn thành',
                text: 'Vui lòng chọn đáp án cho tất cả các câu hỏi trước khi nộp bài!'
            });
            return;
        }
        answers.push({
            questionId: q.questionId,
            userAnswer: checked.value
        });
    }

    const payload = {
        sessionId: sessionId || 0,
        misconception: activeMisconception,
        answers: answers
    };

    try {
        const res = await API.remediation.submit(payload);
        const result = res.data;

        if (result.repaired || result.allCorrect) {
            // Dọn dẹp triệt để localStorage cho bài thi nếu còn lưu
            if (currentQuizResult && currentQuizResult.topicId) {
                localStorage.removeItem(`quiz_progress_${currentQuizResult.topicId}`);
            }

            // Đóng modal
            remediationModal.hide();

            // Kích hoạt pháo hoa Confetti
            if (typeof confetti === 'function') {
                confetti({
                    particleCount: 120,
                    spread: 80,
                    origin: { y: 0.6 }
                });
            }

            // Cập nhật Thẻ Khắc Phục Lỗi trên trang chính
            const actionContainer = document.getElementById('remediation-action-container');
            actionContainer.innerHTML = `
                <div class="d-flex align-items-center gap-2">
                    <span class="badge bg-success text-white fs-6 px-3 py-2 rounded-pill shadow-sm">
                        <i class="fa-solid fa-award me-1"></i>ĐÃ PHỤC HỒI KIẾN THỨC
                    </span>
                </div>
            `;

            // Thông báo Toast thành công
            if (window.Toast) {
                window.Toast.fire({
                    icon: 'success',
                    title: `Xuất sắc! Bạn đã trả lời đúng ${result.correctCount}/${result.total} câu và vá thành công lỗ hổng tư duy!`
                });
            } else {
                Swal.fire({
                    icon: 'success',
                    title: 'Đã Phục Hồi Kiến Thức!',
                    text: `Bạn đã trả lời đúng ${result.correctCount}/${result.total} câu ôn tập.`
                });
            }

        } else {
            // Chưa đúng hết: hiển thị kết quả chi tiết trong modal để ôn lại
            const modalBody = document.getElementById('remediation-modal-body');
            modalBody.innerHTML = `
                <div class="alert alert-warning border-0 p-3 mb-3">
                    <h6 class="fw-bold mb-1"><i class="fa-solid fa-circle-exclamation me-1"></i>Bạn trả lời đúng ${result.correctCount}/${result.total} câu</h6>
                    <p class="small mb-0">Hãy xem lại giải thích chi tiết dưới đây để hiểu sâu hơn nhé:</p>
                </div>
                ${result.feedback.map((fb, idx) => `
                    <div class="card p-3 mb-2 border ${fb.isCorrect ? 'border-success bg-success-subtle bg-opacity-10' : 'border-danger bg-danger-subtle bg-opacity-10'}">
                        <div class="d-flex justify-content-between mb-1">
                            <span class="fw-bold">Câu ${idx + 1}</span>
                            <span class="badge ${fb.isCorrect ? 'bg-success' : 'bg-danger'}">${fb.isCorrect ? 'ĐÚNG' : 'SAI'}</span>
                        </div>
                        <div class="small">Lựa chọn của bạn: <strong>${fb.userAnswer}</strong> | Đáp án đúng: <strong class="text-success">${fb.correctAnswer}</strong></div>
                        ${fb.explanation ? `<div class="small text-muted mt-1 border-top pt-1"><strong>Giải thích:</strong> ${fb.explanation}</div>` : ''}
                    </div>
                `).join('')}
                <div class="text-center mt-3">
                    <button class="btn btn-warning rounded-pill px-4 fw-bold" onclick="startAdaptiveRemediation()">
                        <i class="fa-solid fa-rotate-left me-1"></i>Luyện tập lại
                    </button>
                </div>
            `;
            document.getElementById('btn-submit-remediation').style.display = 'none';
        }

    } catch (err) {
        Swal.fire({
            icon: 'error',
            title: 'Lỗi',
            text: err.message || 'Không thể nộp bài tập phục hồi.'
        });
    }
}

function getMisconceptionBadgeClass(type) {
    switch (type) {
        case 'syntax_swap': return 'bg-danger text-white';
        case 'boundary_blindness': return 'bg-warning text-dark';
        case 'mental_model_gap': return 'bg-info text-dark';
        case 'logic_flaw': return 'bg-secondary text-white';
        default: return 'bg-secondary text-white';
    }
}

function getMisconceptionLabel(type) {
    switch (type) {
        case 'syntax_swap': return 'Nhầm lẫn cú pháp (Syntax Swap)';
        case 'boundary_blindness': return 'Bỏ quên biên/Null (Boundary Blindness)';
        case 'mental_model_gap': return 'Hổng mô hình tư duy (Mental Model Gap)';
        case 'logic_flaw': return 'Sai sót logic điều kiện (Logic Flaw)';
        default: return type || 'Quan niệm sai lầm';
    }
}

function askAITutor(encodedReason) {
    const reason = decodeURIComponent(encodedReason);
    const launcher = document.getElementById('chatbot-launcher');
    const windowEl = document.getElementById('chatbot-window');
    const input = document.getElementById('chat-input');

    if (launcher && windowEl && !windowEl.classList.contains('active')) {
        launcher.click();
    }
    if (input) {
        input.value = `Thầy/bạn ơi, mình vừa làm sai câu hỏi với chẩn đoán: "${reason}". Giúp mình giải thích sâu hơn được không?`;
        input.focus();
    }
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
 * DIỄN ĐÀN THẢO LUẬN & ĐÁNH GIÁ ĐỘ TIN CẬY CÂU HỎI (FORUM & CREDIBILITY)
 * ══════════════════════════════════════════════════════════════
 */

const loadedCommentsMap = {};

/**
 * Tải chỉ số tín nhiệm của câu hỏi (Upvote, Downvote, % Tin cậy)
 */
async function loadCredibility(questionId) {
    try {
        const res = await API.discussion.getCredibility(questionId);
        const data = res.data || {};

        const upvotesEl = document.getElementById(`upvotes-cnt-${questionId}`);
        const downvotesEl = document.getElementById(`downvotes-cnt-${questionId}`);
        const badgeEl = document.getElementById(`cred-badge-${questionId}`);
        const upBtn = document.getElementById(`btn-upvote-${questionId}`);
        const downBtn = document.getElementById(`btn-downvote-${questionId}`);
        const reportBtn = document.getElementById(`btn-report-${questionId}`);

        if (upvotesEl) upvotesEl.textContent = data.upvotes !== undefined ? data.upvotes : 0;
        if (downvotesEl) downvotesEl.textContent = data.downvotes !== undefined ? data.downvotes : 0;

        if (badgeEl) {
            const score = data.scorePercent !== undefined ? data.scorePercent : 100;
            let color = 'text-success border-success-subtle bg-success-subtle';
            if (score < 60) color = 'text-danger border-danger-subtle bg-danger-subtle';
            else if (score < 80) color = 'text-warning border-warning-subtle bg-warning-subtle';

            badgeEl.className = `badge ${color} border rounded-pill px-2 py-1`;
            badgeEl.innerHTML = `<i class="fa-solid fa-shield-halved me-1"></i>Độ tin cậy: ${score}%`;
        }

        // Highlight vote của người dùng hiện tại
        const userVote = data.userRating;
        if (upBtn) {
            if (userVote === 'UPVOTE') {
                upBtn.className = 'btn btn-sm btn-success rounded-pill px-3 shadow-sm text-white';
            } else {
                upBtn.className = 'btn btn-sm btn-outline-success rounded-pill px-3';
            }
        }
        if (downBtn) {
            if (userVote === 'DOWNVOTE') {
                downBtn.className = 'btn btn-sm btn-secondary rounded-pill px-3 shadow-sm text-white';
            } else {
                downBtn.className = 'btn btn-sm btn-outline-secondary rounded-pill px-3';
            }
        }
        if (reportBtn) {
            if (userVote === 'REPORT_ERROR') {
                reportBtn.className = 'btn btn-sm btn-danger rounded-pill px-3 shadow-sm text-white';
                reportBtn.innerHTML = '<i class="fa-solid fa-flag me-1"></i>Đã báo lỗi';
            } else {
                reportBtn.className = 'btn btn-sm btn-outline-danger rounded-pill px-3';
                reportBtn.innerHTML = '<i class="fa-solid fa-flag me-1"></i>Báo lỗi / Ảo giác AI';
            }
        }

    } catch (err) {
        console.error(`Lỗi tải tín nhiệm cho câu ${questionId}:`, err);
    }
}

/**
 * Đánh giá Upvote / Downvote câu hỏi
 */
async function voteQuestion(questionId, ratingType) {
    try {
        await API.discussion.rateQuestion(questionId, ratingType);
        await loadCredibility(questionId);

        Swal.fire({
            toast: true,
            position: 'top-end',
            icon: 'success',
            title: ratingType === 'UPVOTE' ? 'Đã ghi nhận hữu ích!' : 'Đã ghi nhận đánh giá!',
            showConfirmButton: false,
            timer: 1500
        });
    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể gửi đánh giá.', 'error');
    }
}

/**
 * Mở hộp thoại báo lỗi câu hỏi hoặc phát hiện AI bị ảo giác
 */
async function reportQuestionPrompt(questionId) {
    const { value: reason } = await Swal.fire({
        title: '<i class="fa-solid fa-flag text-danger me-2"></i>Báo Lỗi / Phản Biện Câu Hỏi',
        html: `
            <p class="small text-muted mb-3 text-start">
                Hãy cho cộng đồng và Giảng viên biết vấn đề cụ thể ở câu hỏi này để hệ thống tiến hành kiểm duyệt:
            </p>
            <div class="mb-3 text-start">
                <label class="form-label small fw-bold">Dạng lỗi phát hiện:</label>
                <select id="swal-res-report-type" class="form-select form-select-sm">
                    <option value="Đáp án chuẩn bị sai / Gây tranh cãi">Đáp án chuẩn bị sai / Gây tranh cãi</option>
                    <option value="Đề bài tối nghĩa / Lỗi diễn đạt hoặc ngữ pháp">Đề bài tối nghĩa / Lỗi diễn đạt hoặc ngữ pháp</option>
                    <option value="Ảo giác AI (AI Hallucination) / Code sai logic">Ảo giác AI (AI Hallucination) / Code sai logic</option>
                    <option value="Khác">Lý do khác</option>
                </select>
            </div>
            <div class="text-start">
                <label class="form-label small fw-bold">Mô tả cụ thể (tùy chọn):</label>
                <textarea id="swal-res-report-detail" class="form-control form-control-sm" rows="3" placeholder="Nhập thêm chi tiết về lỗi nếu có..."></textarea>
            </div>
        `,
        focusConfirm: false,
        showCancelButton: true,
        confirmButtonText: 'Gửi báo lỗi',
        cancelButtonText: 'Hủy',
        confirmButtonColor: '#dc3545',
        preConfirm: () => {
            const type = document.getElementById('swal-res-report-type').value;
            const detail = document.getElementById('swal-res-report-detail').value.trim();
            return detail ? `[${type}] ${detail}` : type;
        }
    });

    if (reason) {
        try {
            await API.discussion.rateQuestion(questionId, 'REPORT_ERROR', reason.trim());
            await loadCredibility(questionId);

            Swal.fire({
                icon: 'success',
                title: 'Đã gửi báo lỗi thành công!',
                text: 'Cảm ơn tinh thần phản biện học thuật của bạn. Đội ngũ kiểm duyệt và giảng viên sẽ rà soát câu hỏi này sớm nhất!',
                confirmButtonColor: '#0d6efd'
            });
        } catch (err) {
            Swal.fire('Lỗi', err.message || 'Không thể gửi báo lỗi.', 'error');
        }
    }
}

/**
 * Đóng / mở khung bình luận phản biện
 */
function toggleComments(questionId) {
    const section = document.getElementById(`comments-section-${questionId}`);
    if (!section) return;

    if (section.style.display === 'none' || !section.style.display) {
        section.style.display = 'block';
        loadComments(questionId);
    } else {
        section.style.display = 'none';
    }
}

/**
 * Tải danh sách bình luận của câu hỏi
 */
async function loadComments(questionId) {
    const listEl = document.getElementById(`comments-list-${questionId}`);
    const cntEl = document.getElementById(`comments-cnt-${questionId}`);
    if (!listEl) return;

    try {
        const res = await API.discussion.getComments(questionId);
        const comments = res.data || [];
        loadedCommentsMap[questionId] = comments;

        if (cntEl) cntEl.textContent = comments.length;

        if (comments.length === 0) {
            listEl.innerHTML = `
                <div class="text-muted small text-center py-3 bg-light rounded-3">
                    <i class="fa-regular fa-comment-dots me-1"></i>Chưa có phản biện nào. Hãy là người đầu tiên mở đầu thảo luận!
                </div>
            `;
            return;
        }

        listEl.innerHTML = comments.map(c => {
            const roleBadge = (c.userRole === 'teacher' || c.userRole === 'admin')
                ? '<span class="badge bg-success-subtle text-success border border-success-subtle rounded-pill ms-1" style="font-size:0.68rem;"><i class="fa-solid fa-chalkboard-user me-1"></i>Giảng viên</span>'
                : '<span class="badge bg-light text-muted border rounded-pill ms-1" style="font-size:0.68rem;">Học viên</span>';

            const timeStr = c.createdAt ? c.createdAt.replace('T', ' ').substring(0, 16) : '';

            return `
                <div class="p-3 bg-light rounded-3 border">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <div>
                            <strong class="text-dark small">${escapeHtml(c.userFullName || c.username)}</strong>
                            ${roleBadge}
                        </div>
                        <small class="text-muted" style="font-size: 0.72rem;">${timeStr}</small>
                    </div>
                    <div class="text-dark small" style="white-space: pre-wrap;">${escapeHtml(c.content)}</div>
                </div>
            `;
        }).join('');

    } catch (err) {
        console.error(`Lỗi tải bình luận cho câu ${questionId}:`, err);
        listEl.innerHTML = '<div class="text-danger small text-center py-2">Không thể tải danh sách bình luận.</div>';
    }
}

/**
 * Gửi bình luận mới vào câu hỏi
 */
async function submitComment(questionId) {
    const input = document.getElementById(`comment-input-${questionId}`);
    if (!input) return;

    const content = input.value.trim();
    if (!content) return;

    try {
        input.disabled = true;
        await API.discussion.addComment(questionId, content);
        input.value = '';
        await loadComments(questionId);

        Swal.fire({
            toast: true,
            position: 'top-end',
            icon: 'success',
            title: 'Đã gửi phản biện thành công!',
            showConfirmButton: false,
            timer: 1500
        });
    } catch (err) {
        Swal.fire('Lỗi', err.message || 'Không thể gửi bình luận.', 'error');
    } finally {
        input.disabled = false;
        input.focus();
    }
}
