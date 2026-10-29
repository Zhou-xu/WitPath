// 用 Edge CDP 真实走一遍「修改密码」弹窗：打开 → 就地校验 → 后端错误回显
// 注意：只提交「错误的当前密码」，不会真的改动账号密码
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

  await send('Page.navigate', { url: `${BASE}/login` })
  await sleep(2000)
  await evaluate(`localStorage.setItem('witpath_token', ${JSON.stringify(TOKEN)})`)

  await send('Page.navigate', { url: `${BASE}/app/settings` })
  await sleep(3000)

  // 1. 卡片与按钮
  const card = await evaluate(`(() => {
    const btns = [...document.querySelectorAll('button')]
    const btn = btns.find((b) => b.textContent.trim() === '修改密码')
    const cards = [...document.querySelectorAll('.card__title')].map((t) => t.textContent.trim())
    return { cards, hasBtn: !!btn }
  })()`)
  check('设置页卡片', card.cards.includes('账号安全'), card.cards.join(' / '))
  check('「修改密码」按钮存在', card.hasBtn)

  // 2. 打开弹窗
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.trim() === '修改密码').click()`)
  await sleep(900)

  const modal = await evaluate(`(() => {
    const form = document.querySelector('.pcm')
    if (!form) return { open: false }
    const inputs = [...form.querySelectorAll('input')].filter((i) => i.type !== 'checkbox')
    return {
      open: true,
      title: document.querySelector('.modal__head h3')?.textContent.trim(),
      count: inputs.length,
      types: inputs.map((i) => i.type),
      labels: [...form.querySelectorAll('.field__label')].map((l) => l.textContent.trim()),
      fontSize: getComputedStyle(inputs[0]).fontSize,
    }
  })()`)
  check('弹窗打开', modal.open && modal.title === '修改密码', modal.title)
  check('三个密码字段', modal.count === 3 && modal.types.slice(0, 3).every((t) => t === 'password'), modal.types.join(','))
  check('字段标签', JSON.stringify(modal.labels) === JSON.stringify(['当前密码 *', '新密码 *', '确认新密码 *']), modal.labels.join('/'))

  const fill = async (idx, value) => {
    await evaluate(`(() => {
      const el = [...document.querySelectorAll('.pcm input')].filter((i) => i.type !== 'checkbox')[${idx}]
      el.value = ${JSON.stringify(value)}
      el.dispatchEvent(new Event('input', { bubbles: true }))
    })()`)
    await sleep(350)
  }
  const snapshot = async () =>
    evaluate(`(() => {
      const submit = [...document.querySelectorAll('.modal__foot button')].find(b => b.textContent.includes('确认修改'))
      const errs = [...document.querySelectorAll('.field__error')].map(e => e.textContent.trim())
      return { disabled: submit ? submit.disabled : null, errs, modalOpen: !!document.querySelector('.pcm') }
    })()`)

  // 3. 空表单：按钮禁用
  let s = await snapshot()
  check('空表单禁止提交', s.disabled === true)

  // 4. 新密码过短
  await fill(0, 'witpath123')
  await fill(1, 'abc')
  s = await snapshot()
  check('新密码过短就地报错', s.errs.some((e) => e.includes('至少 6 位')), s.errs.join('|'))

  // 5. 两次不一致
  await fill(1, 'newpass123')
  await fill(2, 'newpass456')
  s = await snapshot()
  check('两次不一致就地报错', s.errs.some((e) => e.includes('不一致')), s.errs.join('|'))

  // 6. 新旧相同（新密码 = 当前密码）
  await fill(1, 'witpath123')
  await fill(2, 'witpath123')
  s = await snapshot()
  check('新旧相同就地报错', s.errs.some((e) => e.includes('不能与当前密码相同')), s.errs.join('|'))

  // 7. 合法输入 → 按钮可用
  await fill(1, 'newpass123')
  await fill(2, 'newpass123')
  s = await snapshot()
  check('合法输入可提交', s.disabled === false && s.errs.length === 0, s.errs.join('|'))

  // 8. 显示密码开关
  await evaluate(`document.querySelector('.pcm__show input').click()`)
  await sleep(300)
  const typeAfter = await evaluate(`document.querySelectorAll('.pcm input')[1].type`)
  check('显示密码开关生效', typeAfter === 'text', typeAfter)
  await evaluate(`document.querySelector('.pcm__show input').click()`)
  await sleep(200)

  // 9. 提交错误的当前密码 → 后端错误回显（不会真的改密码）
  await fill(0, 'definitely-wrong-pwd')
  s = await snapshot()
  await evaluate(`[...document.querySelectorAll('.modal__foot button')].find(b => b.textContent.includes('确认修改')).click()`)
  await sleep(2500)
  s = await snapshot()
  check('后端错误回显到当前密码字段', s.errs.some((e) => e.includes('原密码不正确')), s.errs.join('|'))
  check('失败后弹窗保持打开', s.modalOpen === true)

  const shot = await send('Page.captureScreenshot', { format: 'png' })
  fs.writeFileSync('tools/password-ui.png', Buffer.from(shot.data, 'base64'))
  console.log('\n截图：tools/password-ui.png')

  const fail = results.filter((r) => !r.ok)
  console.log(`\n通过 ${results.length - fail.length} 项，失败 ${fail.length} 项`)
  process.exit(fail.length ? 1 : 0)
}

main().catch((e) => {
  console.error('脚本失败：', e.message)
  process.exit(1)
})
