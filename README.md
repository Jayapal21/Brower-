# Obscura (Android)

**Obscura** is a stealth mobile browser, DOM extractor, parallel web scraper, and local MCP/CDP bridge built natively for Android using Kotlin, Jetpack Compose, and Material Design 3.

## Features Ported to Android

- **Stealth Browser (`BrowserScreen`)**:
  - Live Android WebView with anti-fingerprinting script injection (`navigator.webdriver = false`, `navigator.platform`, `navigator.userAgentData`) and 8 selectable desktop Chrome 143–146 Windows/macOS profiles.
  - Built-in **3,521-domain tracker blocklist** (`pgl_domains.txt`) intercepting subresources in real time.
  - **SSRF Protection (`SsrfGuard`)**: blocks loopback, RFC1918 private networks, link-local / cloud metadata (`169.254.169.254`), CGNAT, and IPv4-mapped/Teredo/NAT64 IPv6 targets (with toggleable `--allow-private-network`).
  - **RFC 9309 `robots.txt` Cache (`RobotsCache`)**: optional enforcement (`--obey-robots`) with longest-match precedence and LRU eviction.
  - **Screenshot & PDF Export**: capture the live rendered page as a shareable PNG or print/export to PDF via Android's `PrintManager`.
- **DOM Extraction & JS Console (`ExtractScreen`)**:
  - `--dump` formats: **Markdown** (`HTML_TO_MARKDOWN_JS`), **Text**, **Links**, **HTML**, **Assets**, **Cookies**, **Snapshot** (interactive `e1`..`eN` element refs), and **Original** raw HTTP body.
  - Interactive `--eval` JavaScript console with preset queries and JSON result inspection.
- **Parallel Batch Scraper (`ScrapeScreen`)**:
  - Concurrent multi-URL scraping with configurable worker concurrency (1–10), extraction modes, SSRF/Tracker/robots.txt gates, and JSON/TSV export.
- **Network Inspector & Local MCP/CDP Bridge (`NetworkScreen`)**:
  - Real-time request, tracker-block, SSRF-block, and JS console telemetry.
  - Loopback HTTP + JSON-RPC 2.0 server on `127.0.0.1` exposing `/json/version` and `/mcp` tools (`browser_navigate`, `browser_snapshot`, `browser_evaluate`, `browser_network_requests`, `browser_console_messages`).
- **Stealth Controls & Diagnostics (`StealthScreen`)**:
  - Persistent DataStore preferences, HTTP/SOCKS5 proxy overrides, custom User-Agent, and an interactive SSRF & Blocklist diagnostic tester.

## Architecture

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3, Navigation Compose (`@Serializable` routes), Adaptive `NavigationBar` / `NavigationRail`.
- **Networking & Persistence**: OkHttp 4 (custom SSRF-guarded DNS resolver & proxy support), Android `WebView`, Jetpack DataStore Preferences, Kotlin Coroutines & Flow.
- **Build**: Gradle Kotlin DSL, Android SDK 36, JDK 21.

## License

Apache 2.0 — see `LICENSE`.
