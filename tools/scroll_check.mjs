// 验证：滚动时侧边栏与顶栏固定，仅内容区滚动
import fs from 'node:fs'
import http from 'node:http'

const TOKEN = fs.readFileSync('tools/.token.tmp', 'utf8').trim()
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function httpGet(url) {
  return new Promise((resolve, reject) => {
    http.get(url, { headers: { Connection: 'close' } }, (res) => {
      let body = ''
      res.setEncoding('utf8')
      res.on('data', (c) => (body += c))
      res.on('end', () => resolve(body))
    }).on('error', reject)
  })
}

const list = JSON.parse(await httpGet('http://127.0.0.1:9222/json/list'))
const page = list.find((t) => t.type === 'page')
const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })

let id = 0
const pending = new Map()
ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data)
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id)
    pending.delete(msg.id)
    msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
  }
}
const send = (method, params = {}) =>
  new Promise((resolve, reject) => {
    const msgId = ++id
    pending.set(msgId, { resolve, reject })
    ws.send(JSON.stringify({ id: msgId, method, params }))
  })
const evaluate = async (expression) => {
  const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || 'eval error')
  return r.result.value
}

await send('Page.enable')
await send('Runtime.enable')
await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 611, deviceScaleFactor: 1, mobile: false })
await send('Page.navigate', { url: 'http://127.0.0.1:5173/login' })
await sleep(2200)
await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)
await send('Page.navigate', { url: 'http://127.0.0.1:5173/app/assistant' })
await sleep(3500)

const before = await evaluate(`(() => {
  const side = document.querySelector('.side')
  const top = document.querySelector('.top')
  const content = document.querySelector('.shell__content')
  return {
    pageScrollable: document.documentElement.scrollHeight > window.innerHeight,
    contentScrollable: content ? content.scrollHeight > content.clientHeight : null,
    sideTop: Math.round(side.getBoundingClientRect().top),
    topTop: Math.round(top.getBoundingClientRect().top),
    sideRect: side.getBoundingClientRect().toJSON(),
  }
})()`)
console.log('滚动前:', JSON.stringify(before))

await evaluate(`document.querySelector('.shell__content').scrollTop = 400`)
await sleep(400)

const after = await evaluate(`(() => {
  const side = document.querySelector('.side')
  const top = document.querySelector('.top')
  return {
    contentScrollTop: Math.round(document.querySelector('.shell__content').scrollTop),
    sideTop: Math.round(side.getBoundingClientRect().top),
    topTop: Math.round(top.getBoundingClientRect().top),
    sideBrandVisible: !!document.querySelector('.side__brand'),
    navLinksVisible: document.querySelectorAll('.side__link').length,
  }
})()`)
console.log('内容区滚动 400px 后:', JSON.stringify(after))

const ok =
  before.pageScrollable === false &&
  before.contentScrollable === true &&
  after.contentScrollTop === 400 &&
  after.sideTop === before.sideTop &&
  after.topTop === before.topTop &&
  after.navLinksVisible === 6
console.log(ok ? '\n[PASS] 导航固定，仅内容区滚动' : '\n[FAIL]')
const shot = await send('Page.captureScreenshot', { format: 'png' })
fs.writeFileSync('tools/scroll-fix.png', Buffer.from(shot.data, 'base64'))
ws.close()
process.exit(ok ? 0 : 1)
