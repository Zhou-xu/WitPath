// 用 Edge CDP 在手机视口下真实点击汉堡按钮，检查抽屉是否打开
import fs from 'node:fs'
import http from 'node:http'

const TOKEN = fs.readFileSync('tools/.token.tmp', 'utf8').trim()
const BASE = 'http://127.0.0.1:5173'
const CDP = 'http://127.0.0.1:9222'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function httpGet(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, { headers: { Connection: 'close' } }, (res) => {
        let body = ''
        res.setEncoding('utf8')
        res.on('data', (c) => (body += c))
        res.on('end', () => resolve(body))
      })
      .on('error', reject)
  })
}

async function main() {
  const list = JSON.parse(await httpGet(`${CDP}/json/list`))
  const page = list.find((t) => t.type === 'page')
  if (!page) throw new Error('没有可用的 page target')

  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((res, rej) => {
    ws.onopen = res
    ws.onerror = rej
  })

  let id = 0
  const pending = new Map()
  const events = []
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data)
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id)
      pending.delete(msg.id)
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result)
    } else if (msg.method) {
      events.push(msg.method)
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
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || 'eval failed')
    return r.result.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 390, height: 844, deviceScaleFactor: 2, mobile: true,
  })

  // 1. 先到登录页写 token（同源 localStorage）
  await send('Page.navigate', { url: `${BASE}/login` })
  await sleep(2500)
  await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)

  // 2. 进工作台
  await send('Page.navigate', { url: `${BASE}/app/dashboard` })
  await sleep(3500)

  const before = await evaluate(`(() => {
    const burger = document.querySelector('.top__burger')
    const side = document.querySelector('.side')
    const cs = burger ? getComputedStyle(burger) : null
    const ss = side ? getComputedStyle(side) : null
    return {
      path: location.pathname,
      innerWidth: window.innerWidth,
      burgerExists: !!burger,
      burgerDisplay: cs ? cs.display : null,
      burgerRect: burger ? burger.getBoundingClientRect().toJSON() : null,
      sideClass: side ? side.className : null,
      sidePosition: ss ? ss.position : null,
      sideTransform: ss ? ss.transform : null,
      sideRect: side ? side.getBoundingClientRect().toJSON() : null,
      backdrop: !!document.querySelector('.shell__backdrop'),
    }
  })()`)
  console.log('--- 点击前 ---')
  console.log(JSON.stringify(before, null, 2))

  // 3. 真实点击汉堡按钮
  await evaluate(`document.querySelector('.top__burger')?.click()`)
  await sleep(700)

  const after = await evaluate(`(() => {
    const side = document.querySelector('.side')
    const ss = side ? getComputedStyle(side) : null
    return {
      sideClass: side ? side.className : null,
      sideTransform: ss ? ss.transform : null,
      sideLeft: side ? Math.round(side.getBoundingClientRect().left) : null,
      sideWidth: side ? Math.round(side.getBoundingClientRect().width) : null,
      backdrop: !!document.querySelector('.shell__backdrop'),
      firstLinkText: document.querySelector('.side__link')?.textContent?.trim() ?? null,
    }
  })()`)
  console.log('--- 点击后 ---')
  console.log(JSON.stringify(after, null, 2))

  const shot = await send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('tools/mobile-drawer.png', Buffer.from(shot.data, 'base64'))
  console.log('screenshot -> tools/mobile-drawer.png')

  ws.close()
}

main().catch((e) => {
  console.error('FAILED:', e.message)
  process.exit(1)
})
