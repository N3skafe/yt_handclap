package com.example.youtubegesturecontrol.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * 카메라 세션만 켜고 끄기 위한 수명 주기입니다.
 *
 * CameraX는 LifecycleOwner가 STARTED 이상일 때 카메라를 열고, CREATED로 내려가면 닫습니다.
 * LifecycleService의 수명 주기는 서비스가 살아있는 동안 STARTED로 남아,
 * 한 번 바인드한 카메라가 호출어 대기 중에도 계속 열릴 수 있습니다.
 * 그래서 서비스 안에 별도의 LifecycleRegistry를 두고, 인식 중에만 RESUMED로 올립니다.
 * Activity의 수명 주기에 묶으면 사용자가 유튜브를 여는 순간 카메라가 닫힙니다.
 */
class ServiceCameraLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun moveTo(target: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        registry.currentState = target
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        // 호출어만 기다리다 중지하면 카메라를 열지 않아 상태가 INITIALIZED로 남는다.
        // 그 상태에서 바로 DESTROYED로 내리면 LifecycleRegistry가 예외를 던지고 앱이 죽는다.
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.CREATED
        }
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
