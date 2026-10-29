// 最小探测：确认「填入地址后按钮是否解禁」，并读取 Vue 组件内部状态定位原因
import fs from 'node:fs'
import http from 'node:http'

const TOKEN = fs.readFileSync('tools/.token.tmp', 'utf8').trim()
const BASE = 'http://127.0.0.1:5173'
const CDP = 'http://127.0.0.1:9222'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const httpGet = (u) =>
  new Promise((res, rej) =>
    http
      .get(u, { headers: { Connection: 'close' } }, (r) => {
        let b = ''
        r.setEncoding('utf8')
        r.on('data', (c) => (b += c))
        r.on('end', () => res(b))
      })
      .on('error', rej),
  )

const list = JSON.parse(await httpGet(`${CDP}/json/list`))
const page = list.find((t) => t.type === 'page')
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((r, j) => {
  ws.onopen = r
  ws.onerror = j
})
let id = 0
const pending = new Map()
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data)
  if (m.id && pending.has(m.id)) {
    const { resolve, reject } = pending.get(m.id)
    pending.delete(m.id)
    m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result)
  }
}
const send = (method, params = {}) =>
  new Promise((res, rej) => {
    const i = ++id
    pending.set(i, { resolve: res, reject: rej })
    ws.send(JSON.stringify({ id: i, method, params }))
  })
const evaluate = async (e) => {
  const r = await send('Runtime.evaluate', { expression: e, awaitPromise: true, returnByValue: true })
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description)
  return r.result.value
}

await send('Page.enable')
await send('Runtime.enable')
await send('Page.navigate', { url: `${BASE}/login` })
for (let i = 0; i < 30; i++) {
  const s = await evaluate(`document.readyState+'|'+location.href`).catch(() => '')
  if (String(s).includes('complete') && String(s).includes(BASE)) break
  await sleep(500)
}
await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)
await send('Page.navigate', { url: `${BASE}/app/settings` })
await sleep(3000)
await evaluate(`[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='修改模型配置').click()`)
await sleep(1500)

// 用原生 setter 写入（绕过框架的 value tracker）
const probe = await evaluate(`(() => {
  const fields = [...document.querySelectorAll('.aic .field, .aic__unlock .field')]
  const target = fields.find(f => (f.querySelector('.field__label')?.textContent || '').includes('请求地址'))
  const el = target?.querySelector('input')
  let before = null, after = null
  if (el) {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set
    setter.call(el, 'https://example.com/v1')
    el.dispatchEvent(new Event('input', { bubbles: true }))
    before = el.value.length
  }
  const comp = el ? el.__vueParentComponent : null
  const proxy = comp ? comp.proxy : null
  let assigned = null
  if (proxy && proxy.form) {
    proxy.form.baseUrl = 'https://example.com/v1'
    proxy.form.apiKey = 'sk-probe-1234567890'
    assigned = String(proxy.form.baseUrl || '').length + '/' + String(proxy.form.apiKey || '').length
  }
  const btn = [...document.querySelectorAll('.aic button')].find(b => b.textContent.includes('获取模型'))
  return new Promise(resolve => setTimeout(() => resolve({
    domValueLen: before,
    hasProxy: !!proxy,
    formVisible: proxy ? !!proxy.form : null,
    assigned,
    btnDisabled: btn ? btn.disabled : null,
  }), 400))
})()`)
console.log(JSON.stringify(probe, null, 2))
process.exit(0)
