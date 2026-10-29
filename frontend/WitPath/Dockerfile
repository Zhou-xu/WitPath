# syntax=docker/dockerfile:1
# =============================================================================
# WitPath 前端镜像（多阶段：Node 构建 → Nginx 托管 + 反代后端）
# 构建上下文 = frontend/WitPath
# 反代规则与 vite.config.ts 的 dev proxy 保持一致（路径原样透传）
# =============================================================================

# ---------- 阶段一：构建静态资源 ----------
FROM node:20-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 阶段二：Nginx 运行 ----------
FROM nginx:1.27-alpine
ENV TZ=Asia/Shanghai

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80
