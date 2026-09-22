/** Shared UI/resilience helpers. */
const AppUI = (() => {
    const escapeHtml = (value = '') => String(value)
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#039;');

    const sanitizeHtml = (html = '') => {
        const template = document.createElement('template');
        template.innerHTML = String(html);
        template.content.querySelectorAll('script, iframe, object, embed, link[rel="import"]').forEach(el => el.remove());
        template.content.querySelectorAll('*').forEach(el => {
            [...el.attributes].forEach(attr => {
                const name = attr.name.toLowerCase();
                const value = attr.value.trim().toLowerCase();
                if (name.startsWith('on') || ((name === 'href' || name === 'src') && value.startsWith('javascript:'))) {
                    el.removeAttribute(attr.name);
                }
            });
        });
        return template.innerHTML;
    };

    const renderMarkdown = (source = '') => {
        if (typeof marked === 'undefined') return escapeHtml(source);
        return sanitizeHtml(marked.parse(String(source)));
    };

    const setBusy = (element, busy, label = 'Đang xử lý...') => {
        if (!element) return;
        if (busy) {
            element.dataset.originalHtml ??= element.innerHTML;
            element.disabled = true;
            element.setAttribute('aria-busy', 'true');
            element.innerHTML = `<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>${escapeHtml(label)}`;
        } else {
            element.disabled = false;
            element.removeAttribute('aria-busy');
            if (element.dataset.originalHtml) {
                element.innerHTML = element.dataset.originalHtml;
                delete element.dataset.originalHtml;
            }
        }
    };

    const announce = (message) => {
        let region = document.getElementById('app-live-region');
        if (!region) {
            region = document.createElement('div');
            region.id = 'app-live-region';
            region.className = 'visually-hidden';
            region.setAttribute('aria-live', 'polite');
            region.setAttribute('aria-atomic', 'true');
            document.body.appendChild(region);
        }
        region.textContent = '';
        requestAnimationFrame(() => { region.textContent = String(message || ''); });
    };

    window.addEventListener('offline', () => announce('Bạn đang ngoại tuyến. Một số chức năng cần mạng sẽ tạm thời không khả dụng.'));
    window.addEventListener('online', () => announce('Đã kết nối mạng trở lại.'));

    return { escapeHtml, sanitizeHtml, renderMarkdown, setBusy, announce };
})();
