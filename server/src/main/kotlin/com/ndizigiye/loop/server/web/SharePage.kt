package com.ndizigiye.loop.server.web

import com.ndizigiye.loop.api.VideoDto
import com.ndizigiye.loop.common.formatCount

/** Self-contained HTML for `/v/{id}`, the target of the app's share links. No external assets. */
object SharePage {
    fun render(video: VideoDto): String {
        val author = video.author
        val title = "${author.displayName} (@${author.username}) on Loop"
        val description = video.caption.ifBlank { "A video by @${author.username}" }
        val poster = video.thumbnailUrl?.let { """ poster="${it.escape()}"""" }.orEmpty()
        val initial = author.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatar = author.avatarUrl
            ?.let { """<img class="avatar" src="${it.escape()}" alt="">""" }
            ?: """<span class="avatar" style="background:${author.avatarColor.escape()}">${initial.escape()}</span>"""
        return """
            |<!doctype html>
            |<html lang="en">
            |<head>
            |<meta charset="utf-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1">
            |<title>${title.escape()}</title>
            |<meta name="description" content="${description.escape()}">
            |<meta property="og:title" content="${title.escape()}">
            |<meta property="og:description" content="${description.escape()}">
            |<style>
            |  :root { color-scheme: dark; }
            |  * { box-sizing: border-box; }
            |  body { margin: 0; min-height: 100vh; background: #0f1115; color: #f2f2f2;
            |         font: 16px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
            |         display: flex; justify-content: center; align-items: center; padding: 16px; }
            |  main { width: 100%; max-width: 420px; }
            |  .frame { position: relative; width: 100%; aspect-ratio: 9 / 16; background: #000;
            |           border-radius: 16px; overflow: hidden; }
            |  video { width: 100%; height: 100%; object-fit: contain; background: #000; display: block; }
            |  .author { display: flex; align-items: center; gap: 12px; margin: 16px 0 8px; }
            |  .avatar { width: 44px; height: 44px; border-radius: 50%; object-fit: cover; display: inline-flex;
            |            align-items: center; justify-content: center; font-weight: 700; color: #fff; flex: none; }
            |  .name { font-weight: 700; }
            |  .handle, .stats, .sound { color: #a3a8b3; font-size: 14px; }
            |  .caption { margin: 8px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
            |  .stats span + span::before { content: " · "; }
            |  .brand { margin-top: 20px; font-weight: 800; letter-spacing: .04em; color: #8f7cff; }
            |</style>
            |</head>
            |<body>
            |<main>
            |  <div class="frame">
            |    <video src="${video.videoUrl.escape()}"$poster controls playsinline loop preload="metadata"></video>
            |  </div>
            |  <div class="author">
            |    $avatar
            |    <div><div class="name">${author.displayName.escape()}</div><div class="handle">@${author.username.escape()}</div></div>
            |  </div>
            |  <p class="caption">${video.caption.escape()}</p>
            |  <div class="sound">&#9835; ${video.sound.title.escape()}</div>
            |  <div class="stats"><span>${formatCount(video.viewCount)} views</span><span>${formatCount(video.likeCount)} likes</span><span>${formatCount(video.commentCount)} comments</span></div>
            |  <div class="brand">Loop</div>
            |</main>
            |</body>
            |</html>
            |
        """.trimMargin()
    }

    private fun String.escape(): String = buildString(length) {
        for (c in this@escape) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
