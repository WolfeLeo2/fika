package com.wolfeleo2.fika

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform