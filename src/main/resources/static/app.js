const $ = (id) => document.getElementById(id);
const nf = new Intl.NumberFormat('tr-TR');
const pf = new Intl.NumberFormat('tr-TR', {style: 'percent', maximumFractionDigits: 2});

const useBloom = () => $('use-bloom').checked;

function formatDuration(micros) {
    if (micros < 1000) return `${nf.format(Math.round(micros))} µs`;
    if (micros < 1_000_000) return `${(micros / 1000).toLocaleString('tr-TR', {maximumFractionDigits: 2})} ms`;
    return `${(micros / 1_000_000).toLocaleString('tr-TR', {maximumFractionDigits: 2})} sn`;
}

async function api(path, options = {}) {
    const response = await fetch(path, {headers: {'Content-Type': 'application/json'}, ...options});
    const body = await response.json().catch(() => ({}));
    return {ok: response.ok, status: response.status, body};
}

/* ---------- stats ---------- */

async function refreshStats() {
    const {ok, body} = await api('/api/stats');
    if (!ok) return;
    $('stat-users').textContent = nf.format(body.registeredUsers);
    $('stat-size').textContent = `${nf.format(Math.round(body.bloomFilter.sizeInBytes / 1024))} KB`;
    $('stat-fpp').textContent = pf.format(body.bloomFilter.currentFalsePositiveRate);
    $('stat-skipped').textContent = nf.format(body.databaseLookupsSkipped);
    $('stat-db').textContent = `${nf.format(body.databaseLookups)} / ${nf.format(body.falsePositives)}`;
}

/* ---------- live availability ---------- */

function setPipeline(result) {
    const steps = Object.fromEntries([...document.querySelectorAll('.step')].map((el) => [el.dataset.step, el]));
    Object.values(steps).forEach((el) => (el.className = 'step'));
    if (!result) return;

    if (result.bloomVerdict === 'NOT_USED') steps.bloom.classList.add('skipped');
    else steps.bloom.classList.add('hit');
    steps.db.classList.add(result.databaseQueried ? 'hit' : 'skipped');
    steps.result.classList.add(result.available ? 'ok' : 'bad');
}

function describe(result) {
    const time = formatDuration(result.elapsedMicros);
    if (result.bloomVerdict === 'DEFINITELY_ABSENT') {
        return {cls: 'ok', text: `✓ Müsait — Bloom "kesinlikle yok" dedi, veritabanına gidilmedi (${time})`};
    }
    if (result.falsePositive) {
        return {cls: 'warn', text: `✓ Müsait — yanlış pozitif: Bloom "olabilir" dedi, veritabanı "yok" dedi (${time})`};
    }
    if (result.bloomVerdict === 'MIGHT_EXIST') {
        return {cls: 'bad', text: `✗ Alınmış — Bloom "olabilir" dedi, veritabanı doğruladı (${time})`};
    }
    return result.available
        ? {cls: 'ok', text: `✓ Müsait — Bloom kapalı, veritabanı sorgulandı (${time})`}
        : {cls: 'bad', text: `✗ Alınmış — Bloom kapalı, veritabanı sorgulandı (${time})`};
}

const logRows = [];

function addLog(result) {
    const verdict = {
        DEFINITELY_ABSENT: ['kesinlikle yok', 'ok'],
        MIGHT_EXIST: result.falsePositive ? ['yanlış pozitif', 'warn'] : ['olabilir', 'neutral'],
        NOT_USED: ['kapalı', 'neutral'],
    }[result.bloomVerdict];
    logRows.unshift(`<tr>
        <td title="${escapeHtml(result.username)}">${escapeHtml(result.username)}</td>
        <td><span class="tag ${verdict[1]}">${verdict[0]}</span></td>
        <td>${result.databaseQueried ? 'sorgulandı' : '<span class="muted">atlandı</span>'}</td>
        <td><span class="tag ${result.available ? 'ok' : 'bad'}">${result.available ? 'müsait' : 'alınmış'}</span></td>
        <td class="num">${formatDuration(result.elapsedMicros)}</td>
    </tr>`);
    logRows.length = Math.min(logRows.length, 12);
    $('log-body').innerHTML = logRows.join('');
}

function escapeHtml(value) {
    return value.replace(/[&<>"']/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
}

let checkTimer;
let checkSeq = 0;

function scheduleCheck() {
    clearTimeout(checkTimer);
    const username = $('username').value.trim();
    clearFieldError('username');
    $('form-message').textContent = '';
    if (username.length < 3) {
        setPipeline(null);
        setStatus('', username ? 'En az 3 karakter girin.' : 'Yazmaya başlayın; her tuşta müsaitlik kontrol edilir.');
        return;
    }
    checkTimer = setTimeout(() => checkAvailability(username), 200);
}

async function checkAvailability(username) {
    const seq = ++checkSeq;
    const params = new URLSearchParams({username, useBloom: useBloom()});
    const {ok, body} = await api(`/api/users/availability?${params}`);
    if (!ok || seq !== checkSeq) return;
    setPipeline(body);
    const {cls, text} = describe(body);
    setStatus(cls, text);
    addLog(body);
    refreshStats();
}

function setStatus(cls, text) {
    const el = $('username-status');
    el.className = `hint ${cls}`;
    el.textContent = text;
}

/* ---------- registration ---------- */

function clearFieldError(field) {
    const el = document.querySelector(`[data-error-for="${field}"]`);
    if (el) el.textContent = '';
    $(field)?.removeAttribute('aria-invalid');
}

function showFieldErrors(errors = {}) {
    for (const [field, message] of Object.entries(errors)) {
        const el = document.querySelector(`[data-error-for="${field}"]`);
        if (el) el.textContent = message;
        $(field)?.setAttribute('aria-invalid', 'true');
    }
}

async function register(event) {
    event.preventDefault();
    ['username', 'email', 'password'].forEach(clearFieldError);
    const message = $('form-message');
    message.className = 'form-message';
    message.textContent = '';

    const payload = {
        username: $('username').value.trim(),
        email: $('email').value.trim(),
        password: $('password').value,
    };
    $('submit').disabled = true;
    try {
        const {ok, status, body} = await api(`/api/users?useBloom=${useBloom()}`, {method: 'POST', body: JSON.stringify(payload)});
        if (ok) {
            message.className = 'form-message ok';
            message.textContent = `Kayıt başarılı: ${body.username}. Bu isim artık Bloom filter'da — tekrar yazarsanız "alınmış" görünecek.`;
            addLog(body.availabilityCheck);
            $('register-form').reset();
            setPipeline(null);
            setStatus('', 'Yazmaya başlayın; her tuşta müsaitlik kontrol edilir.');
        } else {
            message.className = 'form-message bad';
            message.textContent = status === 409 ? 'Bu kullanıcı adı zaten alınmış.' : (body.message || 'Kayıt başarısız.');
            showFieldErrors(body.fieldErrors);
            if (body.availabilityCheck) addLog(body.availabilityCheck);
        }
    } finally {
        $('submit').disabled = false;
        refreshStats();
    }
}

async function loadSamples() {
    const {ok, body} = await api('/api/users/samples?count=4');
    if (!ok) return;
    $('sample-list').replaceChildren(...body.map((name) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'sample';
        button.textContent = name;
        button.addEventListener('click', () => {
            $('username').value = name;
            scheduleCheck();
            $('username').focus();
        });
        return button;
    }));
}

/* ---------- benchmark ---------- */

async function runBenchmark(event) {
    event.preventDefault();
    const button = $('bench-run');
    button.disabled = true;
    button.textContent = 'Çalışıyor…';
    try {
        const payload = {
            lookups: Number($('bench-lookups').value),
            takenRatio: Number($('bench-ratio').value) / 100,
            latencyMicros: Number($('bench-latency').value),
        };
        const {ok, body} = await api('/api/benchmark', {method: 'POST', body: JSON.stringify(payload)});
        if (ok) renderBenchmark(body);
    } finally {
        button.disabled = false;
        button.textContent = 'Çalıştır';
        refreshStats();
    }
}

function renderBenchmark(r) {
    $('bench-result').hidden = false;
    const off = r.withoutBloom;
    const on = r.withBloom;

    const speed = r.speedup.toLocaleString('tr-TR', {maximumFractionDigits: 1});
    $('bench-summary').innerHTML =
        `Bloom filter veritabanı sorgularının <strong>%${r.databaseLookupsSavedPercent.toLocaleString('tr-TR', {maximumFractionDigits: 1})}</strong>'ini atladı ` +
        `ve kontrolleri <strong>${speed}×</strong> hızlandırdı. Yanlış cevap: <strong>${nf.format(r.mismatchedAnswers)}</strong>.`;

    const maxDb = Math.max(off.databaseLookups, on.databaseLookups, 1);
    const maxTime = Math.max(off.elapsedMillis, on.elapsedMillis, 0.001);
    $('bar-db-off').style.width = `${(off.databaseLookups / maxDb) * 100}%`;
    $('bar-db-on').style.width = `${(on.databaseLookups / maxDb) * 100}%`;
    $('bar-time-off').style.width = `${(off.elapsedMillis / maxTime) * 100}%`;
    $('bar-time-on').style.width = `${(on.elapsedMillis / maxTime) * 100}%`;
    $('val-db-off').textContent = nf.format(off.databaseLookups);
    $('val-db-on').textContent = nf.format(on.databaseLookups);
    $('val-time-off').textContent = formatDuration(off.elapsedMillis * 1000);
    $('val-time-on').textContent = formatDuration(on.elapsedMillis * 1000);

    const facts = [
        ['Kontrol edilen isim', nf.format(r.lookups)],
        ['Gerçekten alınmış', nf.format(r.takenProbes)],
        ['Yanlış pozitif', nf.format(r.falsePositives)],
        ['Gözlenen yanlış pozitif oranı', pf.format(r.observedFalsePositiveRate)],
        ['Ort. kontrol (Bloom\'suz)', formatDuration(off.averageMicrosPerCheck)],
        ['Ort. kontrol (Bloom\'lu)', formatDuration(on.averageMicrosPerCheck)],
    ];
    $('bench-facts').innerHTML = facts.map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('');
}

/* ---------- wiring ---------- */

$('username').addEventListener('input', scheduleCheck);
$('use-bloom').addEventListener('change', scheduleCheck);
$('register-form').addEventListener('submit', register);
$('bench-form').addEventListener('submit', runBenchmark);
$('bench-ratio').addEventListener('input', (e) => ($('bench-ratio-out').textContent = `%${e.target.value}`));

refreshStats();
loadSamples();
