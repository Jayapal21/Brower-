package com.example.engine

/**
 * Port of `crates/obscura-browser/src/profiles.rs` and stealth anti-fingerprinting bootstrap.
 */
data class BrowserProfile(
    val id: Int,
    val label: String,
    val userAgent: String,
    val platform: String,
    val uaPlatform: String,
    val uaPlatformVersion: String,
    val chromeMajor: String,
)

object BrowserProfiles {

    val PROFILES: List<BrowserProfile> = listOf(
        BrowserProfile(
            id = 0,
            label = "Chrome 143 · Windows 10",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            platform = "Win32",
            uaPlatform = "Windows",
            uaPlatformVersion = "10.0.0",
            chromeMajor = "143",
        ),
        BrowserProfile(
            id = 1,
            label = "Chrome 144 · Windows 10",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
            platform = "Win32",
            uaPlatform = "Windows",
            uaPlatformVersion = "10.0.0",
            chromeMajor = "144",
        ),
        BrowserProfile(
            id = 2,
            label = "Chrome 145 · Windows 11",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
            platform = "Win32",
            uaPlatform = "Windows",
            uaPlatformVersion = "15.0.0",
            chromeMajor = "145",
        ),
        BrowserProfile(
            id = 3,
            label = "Chrome 146 · Windows 11",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
            platform = "Win32",
            uaPlatform = "Windows",
            uaPlatformVersion = "15.0.0",
            chromeMajor = "146",
        ),
        BrowserProfile(
            id = 4,
            label = "Chrome 143 · macOS Ventura",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            platform = "MacIntel",
            uaPlatform = "macOS",
            uaPlatformVersion = "13.6.7",
            chromeMajor = "143",
        ),
        BrowserProfile(
            id = 5,
            label = "Chrome 144 · macOS Sonoma",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
            platform = "MacIntel",
            uaPlatform = "macOS",
            uaPlatformVersion = "14.4.1",
            chromeMajor = "144",
        ),
        BrowserProfile(
            id = 6,
            label = "Chrome 145 · macOS Sonoma",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
            platform = "MacIntel",
            uaPlatform = "macOS",
            uaPlatformVersion = "14.5.0",
            chromeMajor = "145",
        ),
        BrowserProfile(
            id = 7,
            label = "Chrome 146 · macOS Sequoia",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
            platform = "MacIntel",
            uaPlatform = "macOS",
            uaPlatformVersion = "14.6.0",
            chromeMajor = "146",
        ),
    )

    fun getProfile(index: Int): BrowserProfile =
        PROFILES.getOrElse(index) { PROFILES[0] }

    fun effectiveUserAgent(index: Int, customUserAgent: String): String =
        customUserAgent.trim().ifEmpty { getProfile(index).userAgent }

    /**
     * Generates anti-fingerprinting JavaScript injected at page load when Stealth Mode is active.
     */
    fun buildStealthBootstrapJs(profile: BrowserProfile): String = """
        (function() {
          try {
            Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true });
            Object.defineProperty(navigator, 'platform', { get: () => '${profile.platform}', configurable: true });
            if (!window.chrome) {
              Object.defineProperty(window, 'chrome', { value: { runtime: {} }, configurable: true });
            }
            const uaData = {
              brands: [
                { brand: 'Not(A:Brand', version: '99' },
                { brand: 'Chromium', version: '${profile.chromeMajor}' },
                { brand: 'Google Chrome', version: '${profile.chromeMajor}' }
              ],
              mobile: false,
              platform: '${profile.uaPlatform}',
              getHighEntropyValues: function(hints) {
                return Promise.resolve({
                  architecture: 'x86',
                  bitness: '64',
                  model: '',
                  platform: '${profile.uaPlatform}',
                  platformVersion: '${profile.uaPlatformVersion}',
                  uaFullVersion: '${profile.chromeMajor}.0.0.0',
                  brands: this.brands,
                  mobile: false
                });
              },
              toJSON: function() {
                return { brands: this.brands, mobile: this.mobile, platform: this.platform };
              }
            };
            Object.defineProperty(navigator, 'userAgentData', { get: () => uaData, configurable: true });
          } catch (_) {}
        })();
    """.trimIndent()
}
