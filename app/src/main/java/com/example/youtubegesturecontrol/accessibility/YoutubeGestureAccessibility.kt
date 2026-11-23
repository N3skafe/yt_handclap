package com.example.youtubegesturecontrol.accessibility

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * 시스템이 연결한 접근성 서비스입니다. 서비스가 없으면 null입니다.
 */
object YoutubeGestureAccessibility {
    private val current = AtomicReference<WeakReference<YoutubeGestureAccessibilityService>?>(null)

    fun attach(service: YoutubeGestureAccessibilityService) {
        current.set(WeakReference(service))
    }

    fun detach(service: YoutubeGestureAccessibilityService) {
        current.getAndUpdate { existing ->
            if (existing?.get() === service) null else existing
        }
    }

    fun connectedService(): YoutubeGestureAccessibilityService? = current.get()?.get()
}
