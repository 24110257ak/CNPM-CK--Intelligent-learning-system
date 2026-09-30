/**
 * Quiz Interface Logic (LMS Thông Minh)
 * Bao gồm:
 * - 1.1. Tự động lưu tiến độ làm bài (Autosave Progress) qua localStorage
 * - 1.2. Màn hình chờ AI phân tích (Loading Overlay) với chu kỳ đổi thông điệp 1.5s
 * - 1.3. Bảo vệ luồng điều hướng (Guard Routes)
 * - Tương tác câu hỏi, Confidence Tagging, đồng hồ đếm ngược, render Markdown & syntax highlighting
 */

// 1. Kiểm tra xác thực
const currentUser = API.auth.requireAuth();

// 2. State quản lý bài thi
const urlParams = new URLSearchParams(window.location.search);
const topicId = urlParams.get('topicId');
const topicNameParam = urlParams.get('topicName');

let sessionId = null;
let questions = [];
let currentIndex = 0;
let userAnswers = {}; // { [questionId]: { userAnswer: 'A', confidenceLevel: 'CERTAIN' } }
let timerInterval = null;
let timeLeft = 15 * 60; // 15 phút mặc định

document.addEventListener('DOMContentLoaded', () => {
    // 1.3. Guard Route: Kiểm tra xem URL có chứa topicId không
    if (!topicId || topicId.trim() === '') {
        Swal.fire({
            icon: 'error',
            title: 'Chưa chọn chủ đề',
            text: 'Vui lòng chọn một chủ đề học tập trước khi bắt đầu làm bài!',
            confirmButtonText: 'Về danh sách chủ đề'
        }).then(() => {
            window.location.href = 'index.html';
        });
        return;
    }

    if (topicNameParam) {
        document.getElementById('quiz-topic-title').textContent = decodeURIComponent(topicNameParam);
    }

    setupEventListeners();
    initQuiz();
});

/**
 * Gắn các sự kiện điều hướng và thao tác bài thi
 */
function setupEventListeners() {
    // Nút Trước / Tiếp
    document.getElementById('btn-prev-question').addEventListener('click', () => {
        if (currentIndex > 0) renderQuestion(currentIndex - 1);
    });

    document.getElementById('btn-next-question').addEventListener('click', () => {
        if (currentIndex < questions.length - 1) {
            renderQuestion(currentIndex + 1);
        } else {
            Swal.fire({
                title: 'Bạn đã xem hết các câu hỏi!',
                text: 'Bạn có muốn kiểm tra lại bài làm hay nộp bài ngay?',
                icon: 'info',
                showCancelButton: true,
                confirmButtonText: 'Nộp bài ngay',
                cancelButtonText: 'Kiểm tra lại'
            }).then(res => {
                if (res.isConfirmed) submitQuiz(false);
            });
        }
    });

    // Listener cho radio mức độ tự tin (Confidence Tagging)
    document.querySelectorAll('input[name="confidenceRadio"]').forEach(r => {
        r.addEventListener('change', () => {
            saveCurrentSelection();
            renderPalette();
            saveProgressToStorage();
        });
    });

    // Nút nộp bài
    document.getElementById('btn-submit-quiz').addEventListener('click', () => submitQuiz(false));

    // Nút báo lỗi câu hỏi tới Giảng viên
    const btnReport = document.getElementById('btn-quiz-report');
    if (btnReport) {
        btnReport.addEventListener('click', handleQuizReportQuestion);
    }

    // Nút quay lại trang chủ với xác nhận
    const btnBack = document.getElementById('btn-quiz-back');
    if (btnBack) {
        btnBack.addEventListener('click', (e) => {
            e.preventDefault();
            Swal.fire({
                title: 'Tạm dừng làm bài?',
                text: 'Tiến độ câu hỏi hiện tại đã được lưu. Bạn có muốn quay lại danh mục môn học không?',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'Quay lại',
                cancelButtonText: 'Tiếp tục làm'
            }).then((res) => {
                if (res.isConfirmed) {
                    saveProgressToStorage();
                    window.location.href = 'index.html';
                }
            });
        });
    }
}

/**
 * Báo lỗi câu hỏi hiện tại cho Giảng viên kiểm duyệt
 */
async function handleQuizReportQuestion() {
    const q = questions[currentIndex];
    if (!q) return;

    const { value: formValues } = await Swal.fire({
        title: '<i class="fa-solid fa-flag text-danger me-2"></i>Báo Lỗi Câu Hỏi',
        html: `
            <p class="small text-muted mb-3 text-start">
                Bạn nhận thấy câu hỏi này có sai sót? Phản hồi của bạn sẽ được gửi trực tiếp tới <strong>Bảng Quản Trị Giảng Viên</strong> để kiểm duyệt và chỉnh sửa.
            </p>
            <div class="mb-3 text-start">
                <label class="form-label small fw-bold">Dạng lỗi phát hiện:</label>
                <select id="swal-report-type" class="form-select form-select-sm">
                    <option value="Đáp án chuẩn bị sai / Gây tranh cãi">Đáp án chuẩn bị sai / Gây tranh cãi</option>
                    <option value="Đề bài tối nghĩa / Lỗi diễn đạt hoặc ngữ pháp">Đề bài tối nghĩa / Lỗi diễn đạt hoặc ngữ pháp</option>
                    <option value="Ảo giác AI (AI Hallucination) / Code sai logic">Ảo giác AI (AI Hallucination) / Code sai logic</option>
                    <option value="Khác">Lý do khác</option>
                </select>
            </div>
            <div class="text-start">
                <label class="form-label small fw-bold">Mô tả cụ thể (tùy chọn):</label>
                <textarea id="swal-report-detail" class="form-control form-control-sm" rows="3" placeholder="Nhập thêm chi tiết về lỗi nếu có..."></textarea>
            </div>
        `,
        focusConfirm: false,
        showCancelButton: true,
        confirmButtonText: 'Gửi báo cáo',
        cancelButtonText: 'Hủy',
        confirmButtonColor: '#dc3545',
        preConfirm: () => {
            const type = document.getElementById('swal-report-type').value;
            const detail = document.getElementById('swal-report-detail').value.trim();
            const reason = detail ? `[${type}] ${detail}` : type;
            return reason;
        }
    });

    if (formValues) {
        try {
            await API.discussion.rateQuestion(q.questionId, 'REPORT_ERROR', formValues);
            Swal.fire({
                icon: 'success',
                title: 'Đã gửi báo lỗi!',
                text: 'Cảm ơn đóng góp của bạn. Báo cáo đã được chuyển tới Giảng viên. Bạn có thể yên tâm tiếp tục làm bài!',
                timer: 2500,
                showConfirmButton: false
            });
        } catch (err) {
            Swal.fire('Lỗi', err.message || 'Không thể gửi báo lỗi lúc này.', 'error');
        }
    }
}

/**
 * Khởi tạo bài thi, nạp dữ liệu từ backend và kiểm tra Autosave
 */
async function initQuiz() {
    try {
        const res = await API.quiz.start(topicId);
        const data = res.data;

        sessionId = data.sessionId;
        questions = data.questions || [];

        if (questions.length === 0) {
            document.getElementById('quiz-loading').innerHTML = `
                <div class="alert alert-warning border-0 p-4">
                    <i class="fa-solid fa-triangle-exclamation fa-2x mb-2 text-warning"></i>
                    <h6>Chủ đề này hiện chưa có câu hỏi nào trong ngân hàng đề!</h6>
                    <a href="index.html" class="btn btn-outline-primary btn-sm rounded-pill mt-2">Chọn chủ đề khác</a>
                </div>
            `;
            return;
        }

        // 1.1. Kiểm tra khôi phục tiến độ từ localStorage
        const storageKey = `quiz_progress_${topicId}`;
        const savedDataStr = localStorage.getItem(storageKey);
        let restored = false;

        if (savedDataStr) {
            try {
                const saved = JSON.parse(savedDataStr);
                if (saved && saved.userAnswers && Object.keys(saved.userAnswers).length > 0) {
                    const confirmRestore = await Swal.fire({
                        title: 'Khôi phục bài làm?',
                        text: `Hệ thống phát hiện bạn có bài làm dở dang (${Object.keys(saved.userAnswers).length} câu đã chọn). Bạn có muốn khôi phục không?`,
                        icon: 'question',
                        showCancelButton: true,
                        confirmButtonText: 'Khôi phục bài làm',
                        cancelButtonText: 'Làm lại từ đầu'
                    });

                    if (confirmRestore.isConfirmed) {
                        userAnswers = saved.userAnswers || {};
                        if (saved.currentIndex !== undefined && saved.currentIndex < questions.length) {
                            currentIndex = saved.currentIndex;
                        }
                        if (saved.timeLeft && saved.timeLeft > 0) {
                            timeLeft = saved.timeLeft;
                        }
                        restored = true;
                        if (window.Toast) {
                            window.Toast.fire({ icon: 'info', title: 'Đã khôi phục tiến độ làm bài!' });
                        }
                    } else {
                        localStorage.removeItem(storageKey);
                        userAnswers = {};
                        currentIndex = 0;
                        timeLeft = 15 * 60;
                    }
                }
            } catch (e) {
                localStorage.removeItem(storageKey);
            }
        }

        // Ẩn loading, hiện giao diện bài làm
        document.getElementById('quiz-loading').style.display = 'none';
        document.getElementById('quiz-question-card').style.display = 'block';

        renderQuestion(currentIndex);
        renderPalette();
        startTimer();

    } catch (err) {
        document.getElementById('quiz-loading').innerHTML = `
            <div class="alert alert-danger border-0 p-4">
                <i class="fa-solid fa-circle-xmark fa-2x mb-2 text-danger"></i>
                <h6>Lỗi khởi tạo bài thi: ${err.message}</h6>
                <a href="index.html" class="btn btn-outline-primary btn-sm rounded-pill mt-2">Về trang chủ</a>
            </div>
        `;
    }
}

/**
 * Render câu hỏi theo chỉ số index
 */
function renderQuestion(index) {
    saveCurrentSelection();
    currentIndex = index;

    const q = questions[currentIndex];
    const total = questions.length;

    // Cập nhật badges và tiêu đề
    document.getElementById('quiz-question-counter').textContent = `Câu ${currentIndex + 1} / ${total}`;
    document.getElementById('question-index-badge').textContent = `Câu ${currentIndex + 1}`;

    const diffBadge = document.getElementById('question-difficulty-badge');
    diffBadge.textContent = `Mức độ: ${q.difficulty === 'easy' ? 'Dễ' : q.difficulty === 'hard' ? 'Khó' : 'Trung bình'}`;

    // Cập nhật thanh tiến trình
    const percent = Math.round(((currentIndex + 1) / total) * 100);
    document.getElementById('quiz-progress-bar').style.width = `${percent}%`;

    // Render nội dung câu hỏi (hỗ trợ Markdown & code block)
    const questionTextEl = document.getElementById('question-text');
    if (typeof marked !== 'undefined') {
        questionTextEl.innerHTML = AppUI.renderMarkdown(q.questionText || '');
        if (typeof hljs !== 'undefined') {
            questionTextEl.querySelectorAll('pre code').forEach((el) => {
                hljs.highlightElement(el);
            });
        }
    } else {
        questionTextEl.textContent = q.questionText;
    }

    // Lấy câu trả lời đã lưu trước đó nếu có
    const saved = userAnswers[q.questionId];

    // Render 4 đáp án A, B, C, D
    const optionsList = document.getElementById('options-list');
    const options = [
        { key: 'A', text: q.optionA },
        { key: 'B', text: q.optionB },
        { key: 'C', text: q.optionC },
        { key: 'D', text: q.optionD }
    ];

    optionsList.innerHTML = options.map(opt => `
        <div class="position-relative">
            <input type="radio" class="d-none option-input" name="optRadio" id="opt-${opt.key}" value="${opt.key}"
                   ${saved && saved.userAnswer === opt.key ? 'checked' : ''} onchange="onOptionSelected('${opt.key}')">
            <label class="option-label" for="opt-${opt.key}">
                <div class="d-flex align-items-center gap-3">
                    <span class="badge bg-light text-dark border px-2 py-1 rounded-circle fw-bold fs-6" style="width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center;">
                        ${opt.key}
                    </span>
                    <span class="fs-6">${escapeHtml(opt.text)}</span>
                </div>
            </label>
        </div>
    `).join('');

    // Khôi phục mức độ tự tin (Confidence Tagging)
    const confCertain = document.getElementById('conf-certain');
    const confGuess = document.getElementById('conf-guess');
    if (saved && saved.confidenceLevel === 'GUESS') {
        confGuess.checked = true;
    } else {
        confCertain.checked = true;
    }

    // Điều khiển nút Prev/Next
    document.getElementById('btn-prev-question').disabled = (currentIndex === 0);
    const nextBtn = document.getElementById('btn-next-question');
    if (currentIndex === total - 1) {
        nextBtn.innerHTML = `Kiểm tra & Nộp <i class="fa-solid fa-flag-checkered ms-1"></i>`;
    } else {
        nextBtn.innerHTML = `Câu tiếp theo <i class="fa-solid fa-arrow-right ms-1"></i>`;
    }

    renderPalette();
}

/**
 * Render bảng điều hướng câu hỏi (Palette)
 */
function renderPalette() {
    const grid = document.getElementById('question-palette');
    if (!grid) return;

    grid.innerHTML = questions.map((q, idx) => {
        const isCurrent = (idx === currentIndex);
        const ans = userAnswers[q.questionId];
        const isAnswered = !!(ans && ans.userAnswer);

        let btnClass = 'btn-outline-secondary';
        if (isCurrent) {
            btnClass = 'btn-primary text-white shadow-sm';
        } else if (isAnswered) {
            btnClass = (ans.confidenceLevel === 'GUESS')
                ? 'btn-warning text-dark fw-bold border-warning'
                : 'btn-success text-white fw-bold';
        }

        return `
            <button class="btn btn-sm ${btnClass} rounded-3" style="aspect-ratio: 1;" onclick="renderQuestion(${idx})">
                ${idx + 1}
            </button>
        `;
    }).join('');
}

/**
 * Lưu đáp án hiện tại vào memory
 */
function saveCurrentSelection() {
    if (questions.length === 0 || currentIndex < 0) return;
    const q = questions[currentIndex];
    const checkedOpt = document.querySelector('input[name="optRadio"]:checked');
    const checkedConf = document.querySelector('input[name="confidenceRadio"]:checked');

    if (checkedOpt) {
        userAnswers[q.questionId] = {
            userAnswer: checkedOpt.value,
            confidenceLevel: checkedConf ? checkedConf.value : 'CERTAIN'
        };
    }
}

/**
 * Khi người dùng chọn 1 đáp án
 */
function onOptionSelected(optKey) {
    saveCurrentSelection();
    renderPalette();
    saveProgressToStorage();
}

/**
 * 1.1. Lưu tiến độ làm bài vào localStorage
 */
function saveProgressToStorage() {
    if (!topicId || !sessionId) return;
    const progressData = {
        sessionId,
        userAnswers,
        currentIndex,
        timeLeft,
        timestamp: Date.now()
    };
    localStorage.setItem(`quiz_progress_${topicId}`, JSON.stringify(progressData));
}

/**
 * Đồng hồ đếm ngược
 */
function startTimer() {
    const timerEl = document.getElementById('quiz-timer');

    clearInterval(timerInterval);
    timerInterval = setInterval(() => {
        if (timeLeft <= 0) {
            clearInterval(timerInterval);
            timerEl.textContent = '00:00';
            Swal.fire({
                title: 'Hết giờ làm bài!',
                text: 'Thời gian thi đã kết thúc. Hệ thống sẽ tự động nộp bài làm của bạn.',
                icon: 'warning',
                allowOutsideClick: false,
                confirmButtonText: 'Đồng ý'
            }).then(() => {
                submitQuiz(true);
            });
            return;
        }

        timeLeft--;
        const mins = Math.floor(timeLeft / 60);
        const secs = timeLeft % 60;
        timerEl.textContent = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;

        if (timeLeft <= 60) {
            timerEl.classList.add('text-danger');
        }

        // Tự động lưu thời gian mỗi 10 giây
        if (timeLeft % 10 === 0) {
            saveProgressToStorage();
        }
    }, 1000);
}

/**
 * Nộp bài kiểm tra & Hiển thị màn hình chờ AI (1.2)
 */
async function submitQuiz(force = false) {
    saveCurrentSelection();
    const answeredCount = Object.keys(userAnswers).length;
    const total = questions.length;

    if (!force && answeredCount < total) {
        const confirm = await Swal.fire({
            title: 'Chưa làm hết câu hỏi!',
            text: `Bạn mới làm ${answeredCount}/${total} câu. Bạn có chắc chắn muốn nộp bài bây giờ không?`,
            icon: 'warning',
            showCancelButton: true,
            confirmButtonText: 'Vẫn nộp bài',
            cancelButtonText: 'Tiếp tục làm'
        });
        if (!confirm.isConfirmed) return;
    }

    // 1.2. Kích hoạt Fullscreen Modal / Overlay Loading với chu kỳ thông điệp 1.5s
    const overlay = document.getElementById('ai-loading-overlay');
    const loadingTitle = document.getElementById('ai-loading-title');
    const loadingSubtext = document.getElementById('ai-loading-subtext');

    const loadingStages = [
        {
            title: 'Đang chấm điểm bài làm...',
            sub: 'Hệ thống đang đối chiếu câu trả lời với bộ dữ liệu chuẩn.'
        },
        {
            title: 'Gemini AI đang chẩn đoán tư duy của bạn...',
            sub: 'Phân tích nguyên nhân sai sót và độ tự tin (Confidence Tagging).'
        },
        {
            title: 'Đang tổng hợp lộ trình khắc phục...',
            sub: 'Thiết kế bài giảng ngắn và chuẩn bị bài tập hồi quy thích ứng.'
        }
    ];

    if (overlay) {
        overlay.classList.remove('d-none');
        loadingTitle.textContent = loadingStages[0].title;
        loadingSubtext.textContent = loadingStages[0].sub;
    }

    let stageIndex = 0;
    const cycleTimer = setInterval(() => {
        stageIndex = (stageIndex + 1) % loadingStages.length;
        if (loadingTitle && loadingSubtext) {
            loadingTitle.textContent = loadingStages[stageIndex].title;
            loadingSubtext.textContent = loadingStages[stageIndex].sub;
        }
    }, 1500);

    const answersPayload = questions.map(q => {
        const ans = userAnswers[q.questionId];
        return {
            questionId: q.questionId,
            userAnswer: ans ? ans.userAnswer : '',
            confidenceLevel: ans ? ans.confidenceLevel : 'CERTAIN'
        };
    });

    // DỌN DẸP TIẾN ĐỘ DỞ DANG NGAY KHI TIẾN HÀNH NỘP BÀI (tránh trường hợp thoát ra vào lại bị hỏi khôi phục)
    const storageKey = `quiz_progress_${topicId}`;
    localStorage.removeItem(storageKey);

    try {
        clearInterval(timerInterval);
        const res = await API.quiz.submit(sessionId, answersPayload);

        // Dọn dẹp cycle timer
        clearInterval(cycleTimer);

        // Đảm bảo dọn dẹp triệt để localStorage
        localStorage.removeItem(storageKey);

        // Lưu kết quả vào sessionStorage để trang result.html hiển thị
        sessionStorage.setItem('last_quiz_result', JSON.stringify(res.data));

        // Chuyển hướng sang trang kết quả
        window.location.href = `result.html?sessionId=${sessionId}`;

    } catch (err) {
        clearInterval(cycleTimer);
        const msg = (err.message || '').toLowerCase();

        // 1. Nếu hệ thống báo phiên đã được nộp hoặc đã hoàn tất:
        if (msg.includes('đã được nộp') || msg.includes('đã nộp') || err.status === 409) {
            localStorage.removeItem(storageKey);
            if (overlay) overlay.classList.add('d-none');
            Swal.fire({
                icon: 'info',
                title: 'Bài làm đã hoàn tất',
                text: 'Hệ thống đã ghi nhận bài thi của bạn trước đó. Đang chuyển tới trang xem kết quả...',
                timer: 1600,
                showConfirmButton: false
            }).then(() => {
                window.location.href = `result.html?sessionId=${sessionId}`;
            });
            return;
        }

        // 2. Thử kiểm tra xem backend đã chấm xong chưa (tránh trường hợp timeout mạng phía client nhưng server đã lưu)
        try {
            const check = await API.quiz.session(sessionId);
            if (check && check.data && check.data.session && check.data.session.completedAt) {
                localStorage.removeItem(storageKey);
                sessionStorage.setItem('last_quiz_result', JSON.stringify(check.data));
                window.location.href = `result.html?sessionId=${sessionId}`;
                return;
            }
        } catch (checkErr) {
            // Bỏ qua lỗi kiểm tra
        }

        if (overlay) overlay.classList.add('d-none');

        // 3. Nếu thật sự lỗi không nộp được:
        Swal.fire({
            icon: 'error',
            title: 'Lỗi nộp bài',
            text: err.message || 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng hoặc thử lại.',
            showCancelButton: true,
            confirmButtonText: 'Thử nộp lại',
            cancelButtonText: 'Về trang chủ'
        }).then((result) => {
            if (result.isConfirmed) {
                submitQuiz(true);
            } else {
                localStorage.removeItem(storageKey);
                window.location.href = 'index.html';
            }
        });
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
