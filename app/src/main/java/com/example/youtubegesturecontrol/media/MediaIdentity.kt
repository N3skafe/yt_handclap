package com.example.youtubegesturecontrol.media

/**
 * 다음·이전이 다른 영상으로 넘어갔는지 볼 때 쓰는 식별자입니다.
 * 미디어 ID가 있으면 그것을 쓰고, 비어 있거나 없으면 제목을 씁니다.
 */
object MediaIdentity {
    fun of(mediaId: String?, title: String?): String? {
        val id = mediaId?.takeIf { it.isNotEmpty() }
        if (id != null) {
            return id
        }
        return title?.takeIf { it.isNotEmpty() }
    }

    fun changed(before: String?, after: String?): Boolean {
        val next = after ?: return false
        return before != next
    }
}
