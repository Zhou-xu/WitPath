// 用 Edge CDP 真实走一遍「修改头像」弹窗：打开 → 选在线头像 → 应用 → 验证 OSS 直链
import fs from 'node:fs'
import http from 'node:http'

const TOKEN = fs.readFileSync('tools/.token.tmp', 'utf8').trim()
const BASE = 'http://127.0.0.1:5173'
const CDP = 'http://127.0.0.1:9222'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const results = []
const check = (name, ok, detail = '') => {
  results.push({ name, ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ' - ' + detail : ''}`)
}

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
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || 'eval failed')
    return r.result.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1366, height: 860, deviceScaleFactor: 1, mobile: false,
  })

  // 1. 登录态注入
  await send('Page.navigate', { url: `${BASE}/login` })
  await sleep(2000)
  await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)

  // 2. 进设置页
  await send('Page.navigate', { url: `${BASE}/app/settings` })
  await sleep(3000)

  const settings = await evaluate(`(() => {
    const av = document.querySelector('.settings__avatar')
    const btn = document.querySelector('.settings__avatar-btn')
    const img = av ? av.querySelector('img') : null
    return {
      path: location.pathname,
      avatarBlock: !!av,
      btnText: btn ? btn.textContent.trim() : null,
      currentImg: img ? img.src.slice(0, 90) : null,
    }
  })()`)
  check('设置页含头像区块', settings.avatarBlock, settings.path)
  check('「修改头像」按钮存在', settings.btnText === '修改头像', settings.btnText)

  // 3. 打开弹窗
  await evaluate(`document.querySelector('.settings__avatar-btn').click()`)
  await sleep(1200)

  const modal = await evaluate(`(() => {
    const dlg = document.querySelector('.apm')
    const tabs = [...document.querySelectorAll('.apm__tab')].map((b) => b.textContent.trim())
    const items = document.querySelectorAll('.apm__item').length
    return { open: !!dlg, tabs, presetCount: items }
  })()`)
  check('弹窗打开', modal.open)
  check('两个标签页', JSON.stringify(modal.tabs) === JSON.stringify(['在线头像', '本地头像']), modal.tabs.join('/'))
  check('在线头像 12 款', modal.presetCount === 12, `实际 ${modal.presetCount}`)

  // 4. 选中第一款并应用
  await evaluate(`document.querySelectorAll('.apm__item')[1].click()`)
  await sleep(300)
  const active = await evaluate(`document.querySelectorAll('.apm__item.is-active').length`)
  check('预设可选中', active === 1)

  const btns = await evaluate(`(() => {
    const all = [...document.querySelectorAll('.apm button, .apm ~ * button')]
    return null
  })()`)
  // footer 的确认按钮：文本「使用所选头像」
  await evaluate(`
    const cands = [...document.querySelectorAll('button')].filter((b) => b.textContent.includes('使用所选头像'))
    cands[cands.length - 1].click()
  `)
  await sleep(3000)

  const after = await evaluate(`(() => {
    const img = document.querySelector('.settings__avatar img')
    const modalGone = !document.querySelector('.apm')
    return { modalGone, avatar: img ? img.src : null, natural: img ? img.naturalWidth : 0 }
  })()`)
  check('应用后弹窗关闭', after.modalGone)
  check(
    '头像为 OSS 永久直链',
    (after.avatar || '').startsWith('https://witpath-avatar.oss-cn-shanghai.aliyuncs.com/avatars/'),
    after.avatar,
  )
  check('头像图片加载成功', after.natural > 0, `naturalWidth=${after.natural}`)

  // 5. 顶栏/侧栏同步
  const sync = await evaluate(`(() => {
    const imgs = [...document.querySelectorAll('img')].filter((i) =>
      (i.src || '').includes('witpath-avatar'))
    return imgs.length
  })()`)
  check('页面头像同步渲染', sync >= 2, `共 ${sync} 处`)

  // 6. 截图留档
  const shot = await send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('tools/avatar-ui.png', Buffer.from(shot.data, 'base64'))
  console.log('\n截图：tools/avatar-ui.png')

  const fail = results.filter((r) => !r.ok)
  console.log(`\n通过 ${results.length - fail.length} 项，失败 ${fail.length} 项`)
  process.exit(fail.length ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本失败：', e.message)
  process.exit(1)
})
