// 用 Edge CDP 实测「个人设置」排版：卡片是否成行对齐、同行等高、卡片内间距是否收紧
// 顺带确认「修改模型配置」弹窗已去掉密码确认（打开即表单，无密码输入框）
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
    width: 1440,
    height: 900,
    deviceScaleFactor: 1,
    mobile: false,
  })

  await send('Page.navigate', { url: `${BASE}/login` })
  await sleep(2000)
  await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)

  await send('Page.navigate', { url: `${BASE}/app/settings` })
  await sleep(3500)

  const diag = await evaluate(`({
    url: location.href,
    hasSettings: Boolean(document.querySelector('.settings')),
    cardCount: document.querySelectorAll('.settings__card').length,
    bodyHead: document.body.innerText.slice(0, 200),
  })`)
  console.log('诊断：', JSON.stringify(diag))
  if (!diag.hasSettings) throw new Error(`设置页未渲染：${diag.url} / ${diag.bodyHead}`)

  const layout = await evaluate(`(() => {
    const cards = [...document.querySelectorAll('.settings__card')]
    return {
      cardCount: cards.length,
      cards: cards.map((c) => {
        const r = c.getBoundingClientRect()
        const header = c.querySelector('.card__header')
        const headStyle = header ? getComputedStyle(header) : null
        const body = [...c.children].filter((el) => el !== header)
        const firstBody = body[0]
        let headGap = null
        if (header && firstBody) {
          headGap = Math.round(
            firstBody.getBoundingClientRect().top - header.getBoundingClientRect().bottom
          )
        }
        return {
          title: c.querySelector('.card__title')?.textContent?.trim() ?? '?',
          x: Math.round(r.x), y: Math.round(r.y + window.scrollY),
          w: Math.round(r.width), h: Math.round(r.height),
          headerMarginBottom: headStyle ? headStyle.marginBottom : null,
          headGap,
          padding: getComputedStyle(c).padding,
          gap: getComputedStyle(c).gap,
        }
      }),
      gridCols: getComputedStyle(document.querySelector('.settings__grid')).gridTemplateColumns,
      gridGap: getComputedStyle(document.querySelector('.settings__grid')).gap,
    }
  })()`)

  console.log('\n--- 卡片盒子 ---')
  for (const c of layout.cards) {
    console.log(
      `${c.title.padEnd(6)} x=${String(c.x).padStart(4)} y=${String(c.y).padStart(4)} w=${String(c.w).padStart(4)} h=${String(c.h).padStart(4)} header→内容=${c.headGap}px`
    )
  }
  console.log(`grid: ${layout.gridCols} gap=${layout.gridGap}`)

  check('设置页渲染出 5 张卡片', layout.cardCount === 5, `实际 ${layout.cardCount}`)

  // 按 y 分组成行
  const rows = []
  for (const c of layout.cards) {
    const row = rows.find((r) => Math.abs(r[0].y - c.y) < 4)
    row ? row.push(c) : rows.push([c])
  }
  console.log(`\n--- 行分布 ---\n${rows.map((r, i) => `第 ${i + 1} 行：${r.map((c) => `${c.title}(h=${c.h})`).join(' | ')}`).join('\n')}`)

  check('没有"半行空缺"（每行都排满）', rows.every((r) => r.length === 2 || r[0].w > 700), rows.map((r) => r.length).join('+'))
  check(
    '同行卡片顶边对齐',
    rows.every((r) => r.every((c) => Math.abs(c.y - r[0].y) < 2)),
    ''
  )
  check(
    '同行卡片等高（左右一致）',
    rows.every((r) => r.every((c) => Math.abs(c.h - r[0].h) <= 1)),
    rows.map((r) => `[${r.map((c) => c.h).join(',')}]`).join(' ')
  )
  check(
    '两列左边缘对齐成列',
    new Set(layout.cards.filter((c) => c.w < 700).map((c) => c.x)).size <= 2,
    [...new Set(layout.cards.map((c) => c.x))].join(',')
  )
  check(
    '标题与内容间距已收敛到 12px（原为 32px）',
    layout.cards.every((c) => c.headGap !== null && c.headGap <= 14),
    layout.cards.map((c) => c.headGap).join(',')
  )
  check('卡片内间距为 12px', layout.cards.every((c) => c.gap === '12px'), layout.cards[0].gap)

  // 截图：整页
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  fs.mkdirSync('tools/.cache', { recursive: true })
  fs.writeFileSync('tools/.cache/settings-layout.png', Buffer.from(shot.data, 'base64'))
  console.log('\n截图：tools/.cache/settings-layout.png')

  /* ---------------- 模型配置弹窗：确认已去掉密码确认 ---------------- */
  await evaluate(`[...document.querySelectorAll('button')].find((b) => b.textContent.includes('修改模型配置'))?.click()`)
  await sleep(1500)
  const modal = await evaluate(`(() => {
    const root = document.querySelector('.modal, [class*="modal"]')
    if (!root) return null
    const text = root.textContent
    return {
      hasUnlockBlock: Boolean(root.querySelector('.aic__unlock')),
      passwordInputs: root.querySelectorAll('input[type=password]').length,
      hasApiKeyInput: text.includes('API Key'),
      hasAdvanced: text.includes('高级配置'),
      hasFetchButton: [...root.querySelectorAll('button')].some((b) => b.textContent.includes('获取模型')),
      hasSaveButton: [...root.querySelectorAll('button')].some((b) => b.textContent.includes('确认修改')),
      mentionsPassword: text.includes('登录密码'),
    }
  })()`)

  if (!modal) {
    check('模型配置弹窗已打开', false, '未找到弹窗')
  } else {
    check('弹窗内无「登录密码」确认入口', !modal.hasUnlockBlock && !modal.mentionsPassword, JSON.stringify(modal))
    check('打开即展示配置表单（地址 / API Key / 获取模型）', modal.hasApiKeyInput && modal.hasFetchButton)
    check('保留高级配置与确认修改', modal.hasAdvanced && modal.hasSaveButton)
    check('密码框只剩 API Key 与向量 Key 两处（未展开高级配置时为 1）', modal.passwordInputs === 1, `实际 ${modal.passwordInputs}`)
  }

  await send('Page.captureScreenshot', { format: 'png' }).then((s) =>
    fs.writeFileSync('tools/.cache/ai-config-modal.png', Buffer.from(s.data, 'base64'))
  )
  console.log('截图：tools/.cache/ai-config-modal.png')

  await evaluate(`[...document.querySelectorAll('button')].find((b) => b.textContent.trim() === '取消')?.click()`)

  const failed = results.filter((r) => !r.ok)
  console.log(`\n结果：${results.length - failed.length}/${results.length} 通过`)
  process.exit(failed.length ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本异常：', e.message)
  process.exit(1)
})
