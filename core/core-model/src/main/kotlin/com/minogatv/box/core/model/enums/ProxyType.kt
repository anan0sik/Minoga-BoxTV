package com.minogatv.box.core.model.enums

/**
 * Network proxy type for a playlist/provider.
 *
 * - [NONE]   – Direct connection (default)
 * - [HTTP]   – Plain HTTP CONNECT proxy
 * - [SOCKS5] – SOCKS5 proxy (OkHttp java.net.Proxy + custom DNS)
 */
enum class ProxyType {
    NONE,
    HTTP,
    SOCKS5,
}
