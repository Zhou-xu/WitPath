<template>
  <svg viewBox="0 0 400 320" class="mountain-illustration" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="山间学途插画">
    <defs>
      <!-- 晨曦天空：冷调天光渐变为暖调地平线 -->
      <linearGradient id="wpSky" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#d9e7ee" />
        <stop offset="52%" stop-color="#efe6da" />
        <stop offset="100%" stop-color="#f7e7d3" />
      </linearGradient>
      <!-- 太阳光晕 -->
      <radialGradient id="wpSunGlow" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stop-color="#f2a86e" stop-opacity="0.5" />
        <stop offset="45%" stop-color="#f2a86e" stop-opacity="0.18" />
        <stop offset="100%" stop-color="#f2a86e" stop-opacity="0" />
      </radialGradient>
      <!-- 远山：轻透冷灰（空气透视） -->
      <linearGradient id="wpFar" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#c3d1d9" />
        <stop offset="100%" stop-color="#a6bbc7" />
      </linearGradient>
      <!-- 中山 -->
      <linearGradient id="wpMid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#93aebd" />
        <stop offset="100%" stop-color="#6d8d9e" />
      </linearGradient>
      <!-- 近山：深岩青 -->
      <linearGradient id="wpNear" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#55718a" />
        <stop offset="100%" stop-color="#3d5568" />
      </linearGradient>
      <!-- 湖水：上亮下深 -->
      <linearGradient id="wpWater" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#c6d8df" />
        <stop offset="100%" stop-color="#9cb9c7" />
      </linearGradient>
      <!-- 湖岸草坡 -->
      <linearGradient id="wpShore" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#41605a" />
        <stop offset="100%" stop-color="#2f4a48" />
      </linearGradient>
      <!-- 学途小路：暖沙色 -->
      <linearGradient id="wpPath" x1="0" y1="1" x2="0" y2="0">
        <stop offset="0%" stop-color="#e9dcc2" />
        <stop offset="100%" stop-color="#d2c1a0" />
      </linearGradient>
      <!-- 山间雾气 -->
      <linearGradient id="wpMist" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#ffffff" stop-opacity="0" />
        <stop offset="100%" stop-color="#ffffff" stop-opacity="0.34" />
      </linearGradient>
      <!-- 四角暗角，增加照片质感 -->
      <radialGradient id="wpVignette" cx="50%" cy="44%" r="72%">
        <stop offset="58%" stop-color="#17212b" stop-opacity="0" />
        <stop offset="100%" stop-color="#17212b" stop-opacity="0.12" />
      </radialGradient>
    </defs>

    <!-- 天空 -->
    <rect width="400" height="320" fill="url(#wpSky)" />

    <!-- 太阳：光晕 + 本体 -->
    <circle cx="304" cy="86" r="54" fill="url(#wpSunGlow)" />
    <circle cx="304" cy="86" r="20" fill="#f3ad74" />
    <circle cx="304" cy="86" r="13" fill="#f9cfA6" />

    <!-- 云絮 -->
    <g fill="#ffffff">
      <ellipse cx="96" cy="66" rx="34" ry="7" opacity="0.55" />
      <ellipse cx="118" cy="58" rx="20" ry="5.5" opacity="0.45" />
      <ellipse cx="238" cy="118" rx="42" ry="6.5" opacity="0.4" />
      <ellipse cx="70" cy="104" rx="24" ry="5" opacity="0.3" />
    </g>

    <!-- 飞鸟 -->
    <g class="wp-birds" stroke="#5a7688" stroke-width="2" stroke-linecap="round" fill="none">
      <path d="M64 62 q7 -7 14 0 q7 -7 14 0" opacity="0.7" />
      <path d="M100 48 q5.5 -5.5 11 0 q5.5 -5.5 11 0" opacity="0.5" />
      <path d="M120 72 q4.5 -4.5 9 0 q4.5 -4.5 9 0" opacity="0.6" />
    </g>

    <!-- 远山 -->
    <polygon points="0,198 48,138 96,174 152,118 210,166 262,132 318,160 400,126 400,236 0,236" fill="url(#wpFar)" />

    <!-- 山脚雾气一 -->
    <rect x="0" y="206" width="400" height="30" fill="url(#wpMist)" />

    <!-- 中山 -->
    <polygon points="0,228 44,172 102,202 164,146 226,192 288,154 346,186 400,160 400,254 0,254" fill="url(#wpMid)" />
    <!-- 中山雪顶 -->
    <polygon points="164,146 175,161 153,161" fill="#f4f6f7" />
    <polygon points="164,146 170,155 158,155" fill="#ffffff" />
    <polygon points="288,154 297,167 279,167" fill="#f4f6f7" />
    <polygon points="288,154 293,161 283,161" fill="#ffffff" />

    <!-- 山脚雾气二 -->
    <rect x="0" y="238" width="400" height="26" fill="url(#wpMist)" />

    <!-- 近山 -->
    <polygon points="0,254 52,196 118,226 182,170 244,210 306,178 358,206 400,188 400,278 0,278" fill="url(#wpNear)" />
    <!-- 近山雪顶 -->
    <polygon points="182,170 194,187 170,187" fill="#f4f6f7" />
    <polygon points="182,170 188,179 176,179" fill="#ffffff" />
    <!-- 近山松林 -->
    <polygon points="72,240 77,220 82,240" fill="#2d4a5a" />
    <polygon points="102,248 107,226 112,248" fill="#2d4a5a" />
    <polygon points="330,234 335,214 340,234" fill="#2d4a5a" />
    <polygon points="364,242 369,220 374,242" fill="#2d4a5a" />

    <!-- 湖水 -->
    <rect x="0" y="262" width="400" height="36" fill="url(#wpWater)" />
    <!-- 太阳在湖面的倒影 -->
    <ellipse cx="304" cy="270" rx="26" ry="3.5" fill="#f6cfa2" opacity="0.55" />
    <ellipse cx="302" cy="279" rx="17" ry="2.5" fill="#f6cfa2" opacity="0.4" />
    <ellipse cx="306" cy="288" rx="11" ry="2" fill="#f6cfa2" opacity="0.28" />
    <!-- 涟漪 -->
    <g stroke="#ffffff" stroke-width="2" stroke-linecap="round" opacity="0.35">
      <line x1="108" y1="274" x2="182" y2="274" />
      <line x1="228" y1="284" x2="312" y2="284" />
      <line x1="56" y1="290" x2="128" y2="290" />
      <line x1="250" y1="293" x2="330" y2="293" />
    </g>

    <!-- 湖岸草坡 -->
    <path d="M0 298 C 90 290, 300 290, 400 296 L400 320 L0 320 Z" fill="url(#wpShore)" />

    <!-- 学途小路：由宽阔湖岸渐隐至山脚，呼应「既明前路」 -->
    <path d="M164 320 C 176 310 188 302 195 297 L207 297 C 214 303 226 311 238 320 Z" fill="url(#wpPath)" />
    <path d="M195 297 L207 297" stroke="#b8a888" stroke-width="1" opacity="0.55" />
    <!-- 路边碎石 -->
    <ellipse cx="178" cy="312" rx="3" ry="1.6" fill="#c4b391" opacity="0.8" />
    <ellipse cx="222" cy="308" rx="2.4" ry="1.4" fill="#c4b391" opacity="0.7" />
    <ellipse cx="204" cy="316" rx="2.8" ry="1.5" fill="#c4b391" opacity="0.75" />

    <!-- 四角暗角 -->
    <rect width="400" height="320" fill="url(#wpVignette)" />
  </svg>
</template>

<script setup>
</script>

<style scoped>
.mountain-illustration {
  display: block;
  width: 100%;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow:
    0 2px 4px rgba(16, 24, 40, 0.05),
    0 24px 56px rgba(16, 24, 40, 0.14);
}

/* 飞鸟缓缓横移 */
@keyframes wp-bird-drift {
  0%,
  100% {
    transform: translateX(0);
  }
  50% {
    transform: translateX(10px);
  }
}

.wp-birds {
  animation: wp-bird-drift 9s ease-in-out infinite;
}

@media (prefers-reduced-motion: reduce) {
  .wp-birds {
    animation: none;
  }
}
</style>
