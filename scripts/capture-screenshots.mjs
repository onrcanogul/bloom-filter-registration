// Drives headless Chrome over CDP and saves PNG screenshots of real UI flows.
// usage: node --experimental-websocket scripts/capture-screenshots.mjs <cdpPort> <appUrl> <outDir> <main|at-capacity>
import fs from 'node:fs';

const [port, appUrl, outDir, scenario] = process.argv.slice(2);
const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const page = targets.find((t) => t.type === 'page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener('open', r, {once: true}));

let id = 0;
const pending = new Map();
ws.addEventListener('message', (e) => {
    const msg = JSON.parse(e.data);
    if (msg.id && pending.has(msg.id)) {
        pending.get(msg.id)(msg);
        pending.delete(msg.id);
    }
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
    const msgId = ++id;
    pending.set(msgId, (m) => (m.error ? reject(new Error(`${method}: ${m.error.message}`)) : resolve(m.result)));
    ws.send(JSON.stringify({id: msgId, method, params}));
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const js = async (expression) => {
    const r = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + ' ' + expression);
    return r.result.value;
};
const waitFor = async (expression, timeout = 120000) => {
    const start = Date.now();
    while (!(await js(expression))) {
        if (Date.now() - start > timeout) throw new Error('timeout: ' + expression);
        await sleep(100);
    }
};
const shot = async (name, selector) => {
    let clip;
    if (selector) {
        const rect = await js(`(() => { const r = document.querySelector('${selector}').getBoundingClientRect();
            return {x: r.x + scrollX - 16, y: r.y + scrollY - 16, width: r.width + 32, height: r.height + 32}; })()`);
        clip = {...rect, scale: 1};
    }
    const {data} = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: true, ...(clip ? {clip} : {})});
    fs.writeFileSync(`${outDir}/${name}.png`, Buffer.from(data, 'base64'));
    console.log('saved', name);
};
const typeUsername = async (value) => {
    await js(`(() => { const el = document.getElementById('username'); el.focus(); el.value = ${JSON.stringify(value)};
        el.dispatchEvent(new Event('input', {bubbles: true})); })()`);
    await sleep(700);
};
const setBloom = async (on) => {
    await js(`(() => { const el = document.getElementById('use-bloom'); if (el.checked !== ${on}) el.click(); })()`);
    await sleep(700);
};
const runBenchmark = async (lookups, ratio, latency) => {
    await js(`(() => { document.getElementById('bench-lookups').value = ${lookups};
        const r = document.getElementById('bench-ratio'); r.value = ${ratio}; r.dispatchEvent(new Event('input'));
        document.getElementById('bench-latency').value = ${latency};
        document.getElementById('bench-form').requestSubmit(); })()`);
    await sleep(300);
    await waitFor(`!document.getElementById('bench-run').disabled && !document.getElementById('bench-result').hidden`);
    await sleep(700); // bar transition
};

await send('Page.enable');
await send('Runtime.enable');
await send('Emulation.setDeviceMetricsOverride', {width: 1280, height: 860, deviceScaleFactor: 2, mobile: false});
await send('Emulation.setEmulatedMedia', {features: [{name: 'prefers-color-scheme', value: 'light'}]});
await send('Page.navigate', {url: appUrl});
await waitFor(`document.readyState === 'complete' && document.querySelectorAll('.sample').length > 0
    && document.getElementById('stat-users').textContent !== '–'`);
await sleep(300);

if (scenario === 'main') {
    await shot('01-overview');

    await typeUsername('onurcan.dev');
    await shot('02-new-username-db-skipped', '.grid');

    await js(`document.querySelector('.sample').click()`);
    await sleep(700);
    await shot('03-taken-username-db-confirmed', '.grid');

    await setBloom(false);
    await shot('04-bloom-disabled-always-db', '.grid');

    await setBloom(true);
    await typeUsername('onurcan.dev');
    await js(`(() => { document.getElementById('email').value = 'onurcan.dev@example.com';
        document.getElementById('password').value = 'demo-pass-123';
        document.getElementById('register-form').requestSubmit(); })()`);
    await waitFor(`document.getElementById('form-message').textContent.length > 0`);
    await sleep(400);
    await typeUsername('onurcan.dev');
    await shot('05-registered-then-taken', '.grid');

    await runBenchmark(2000, 20, 1000);
    await shot('06-benchmark-20pct-taken-1ms', '.card.wide');

    await runBenchmark(2000, 5, 1000);
    await shot('07-benchmark-5pct-taken-1ms', '.card.wide');
} else if (scenario === 'at-capacity') {
    await runBenchmark(5000, 20, 0);
    await shot('09-filter-at-capacity-false-positives', '.card.wide');
    await shot('08-filter-at-capacity-stats', '.stats');
}
ws.close();
