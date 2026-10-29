/**
 * 公网 IP 探测
 *
 * 浏览器无法直接拿到本机公网 IP，只能借助公共探测服务。依次尝试多个供应商，
 * 任一成功即返回；全部失败返回空串（后端会从请求头兜底解析，注册流程不受影响）。
 */

const IP_TIMEOUT_MS = 3000

interface IpProvider {
  url: string
  parse: (body: string) => string
}

const providers: IpProvider[] = [
  {
    url: 'https://api.ipify.org?format=json',
    parse: (body) => JSON.parse(body)?.ip ?? '',
  },
  {
    url: 'https://api.ip.sb/ip',
    parse: (body) => body.trim(),
  },
  {
    url: 'https://ipapi.co/json/',
    parse: (body) => JSON.parse(body)?.ip ?? '',
  },
]

async function probe(provider: IpProvider): Promise<string> {
  const res = await fetch(provider.url, { signal: AbortSignal.timeout(IP_TIMEOUT_MS) })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const ip = provider.parse(await res.text())
  // 简单过滤明显非法的结果，避免把错误页内容当 IP 传给后端
  if (!/^[0-9a-fA-F:.]{3,45}$/.test(ip)) throw new Error('invalid ip')
  return ip
}

/** 获取用户公网 IP；所有供应商都失败时返回 ''，绝不抛错 */
export async function fetchPublicIp(): Promise<string> {
  for (const provider of providers) {
    try {
      return await probe(provider)
    } catch {
      // 静默降级到下一个供应商
    }
  }
  return ''
}
