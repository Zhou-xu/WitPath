// 模型配置弹窗 UI 校验（不依赖文本输入模拟的路径）
//
// 前提：已通过接口预置一份个人配置（见 tools/ai_config_check.py 或等价脚本）。
// 覆盖：卡片展示生效模型 → 弹窗展示当前配置 → 高级配置展开 → 「使用默认 API」
//      → 切换后卡片回到系统默认 → 改回个人配置 → 截图。
//
// 说明：无头浏览器对 Vue 受控输入框的模拟输入无效（DOM 值会变但 v-model 不同步），
//      因此「获取模型/填 Key」这条路径由后端端到端脚本（tools/ai_config_check.py）验证。
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

async function main() {
  const list = JSON.parse(await httpGet(`${CDP}/json/list`))
  const page = list.find((t) => t.type === 'page')
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
      const i = ++id
      pending.set(i, { resolve, reject })
      ws.send(JSON.stringify({ id: i, method, params }))
    })
  const evaluate = async (expression) => {
    const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || 'eval failed')
    return r.result.value
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 900, deviceScaleFactor: 1, mobile: false })

  await send('Page.navigate', { url: `${BASE}/login` })
  for (let i = 0; i < 30; i++) {
    const s = await evaluate(`document.readyState+'|'+location.href`).catch(() => '')
    if (String(s).includes('complete') && String(s).includes(BASE)) break
    await sleep(500)
  }
  await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)
  await send('Page.navigate', { url: `${BASE}/app/settings` })
  await sleep(3200)

  const cardText = async () =>
    evaluate(`(() => {
      const card = [...document.querySelectorAll('.settings__card')].find(c => c.textContent.includes('AI 模型配置'))
      return card ? card.textContent.replace(/\\s+/g, ' ').trim() : null
    })()`)

  // 1. 卡片展示当前生效模型
  const t1 = await cardText()
  check('卡片展示已生效模型', /当前使用/.test(t1 || ''), (t1 || '').slice(0, 90))

  // 2. 打开弹窗
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.trim() === '修改模型配置').click()`)
  await sleep(1500)
  const modal = await evaluate(`(() => {
    const aic = document.querySelector('.aic')
    const current = document.querySelector('.aic__current')
    const btns = [...document.querySelectorAll('.modal__foot button')].map(b => b.textContent.trim())
    return {
      open: !!aic,
      current: current ? current.textContent.replace(/\\s+/g, ' ').trim().slice(0, 80) : null,
      btns,
      modelOption: document.querySelector('.aic .asel__trigger')?.textContent.trim() || null,
    }
  })()`)
  check('弹窗打开并展示当前配置', modal.open && /当前：/.test(modal.current || ''), modal.current)
  check('底部含「使用默认 API」按钮', modal.btns.some((b) => b.includes('使用默认 API')), modal.btns.join(' / '))
  check('模型下拉回填当前模型', /（当前）/.test(modal.modelOption || '') || (modal.modelOption || '').length > 0, modal.modelOption)

  // 3. 高级配置展开
  await evaluate(`document.querySelector('.aic__toggle').click()`)
  await sleep(700)
  const advanced = await evaluate(`(() => {
    const box = document.querySelector('.aic__advanced')
    return box ? [...box.querySelectorAll('.field__label')].map(l => l.textContent.trim()) : []
  })()`)
  check('高级配置展开向量模型字段', advanced.length === 4, advanced.join(' / '))

  const shot1 = await send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('tools/ai-config-ui.png', Buffer.from(shot1.data, 'base64'))

  // 4. 使用默认 API
  await evaluate(`[...document.querySelectorAll('.modal__foot button')].find(b => b.textContent.includes('使用默认 API')).click()`)
  await sleep(3000)
  const t2 = await cardText()
  check('切换默认后卡片提示系统默认', /系统默认/.test(t2 || ''), (t2 || '').slice(0, 100))

  // 5. 改回个人配置（Key 留空表示不修改，模型下拉已回填）
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.trim() === '修改模型配置').click()`)
  await sleep(1500)
  const hasDefaultNow = await evaluate(
    `[...document.querySelectorAll('.modal__foot button')].some(b => b.textContent.includes('使用默认 API'))`,
  )
  check('处于默认态时隐藏「使用默认 API」', hasDefaultNow === false)
  await evaluate(`[...document.querySelectorAll('.modal__foot button')].find(b => b.textContent.includes('确认修改')).click()`)
  await sleep(3000)
  const t3 = await cardText()
  check('保存后重新启用个人模型', /当前使用/.test(t3 || ''), (t3 || '').slice(0, 90))

  const shot2 = await send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('tools/ai-config-ui-2.png', Buffer.from(shot2.data, 'base64'))
  console.log('\n截图：tools/ai-config-ui.png、tools/ai-config-ui-2.png')

  const fail = results.filter((r) => !r.ok)
  console.log(`\n通过 ${results.length - fail.length} 项，失败 ${fail.length} 项`)
  process.exit(fail.length ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本失败：', e.message)
  process.exit(1)
})
