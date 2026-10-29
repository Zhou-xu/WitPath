// 手机视口下检查目标页布局与输入字号（iOS 缩放根因）
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
await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true })
await send('Page.navigate', { url: 'http://127.0.0.1:5173/login' })
await sleep(2500)
await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)
await send('Page.navigate', { url: 'http://127.0.0.1:5173/app/goals' })
await sleep(3500)

const result = await evaluate(`(() => {
  const h2 = document.querySelector('.goals__head h2')
  const search = document.querySelector('.goals__search')
  const body = document.querySelector('.goals__body')
  const overflow = document.documentElement.scrollWidth > window.innerWidth
  return {
    innerWidth: window.innerWidth,
    h2Height: h2 ? Math.round(h2.getBoundingClientRect().height) : null,
    h2SingleLine: h2 ? h2.getBoundingClientRect().height < 40 : null,
    searchFontSize: search ? getComputedStyle(search).fontSize : null,
    searchRight: search ? Math.round(search.getBoundingClientRect().right) : null,
    bodyColumns: body ? getComputedStyle(body).gridTemplateColumns : null,
    horizontalOverflow: overflow,
    scrollWidth: document.documentElement.scrollWidth,
  }
})()`)
console.log(JSON.stringify(result, null, 2))

const shot = await send('Page.captureScreenshot', { format: 'png' })
fs.writeFileSync('tools/mobile-goals.png', Buffer.from(shot.data, 'base64'))
console.log('screenshot -> tools/mobile-goals.png')

const ok =
  result.h2SingleLine === true &&
  parseFloat(result.searchFontSize) >= 16 &&
  result.bodyColumns.split(' ').length === 1 &&
  !result.horizontalOverflow
console.log(ok ? '\n[ALL PASS]' : '\n[FAIL]')
ws.close()
process.exit(ok ? 0 : 1)
