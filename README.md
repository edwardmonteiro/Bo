# bo

**A small Android browser. A reusable web capability for your agents.**

Bo is a native Android app plus an embeddable AAR. It uses the device's **Android System WebView, powered by Chromium**. It does not bundle Chrome or a second browser engine.

## Try v0.1.0

- [Install the development APK](releases/Bo-v0.1.0.apk)
- [Download the embeddable SDK](sdk/bo-core-0.1.0.aar)
- Android 9 / API 28 or later, with an updated WebView provider.
- Open Bo → **Experimentar com uma página demo** → **Ler** → export JSON.
- For real research, enter a query or HTTPS URL, wait for the page, then use **Ler** or **Agente**.

No Bo account. No analytics SDK. No bundled model. One visible tab.

## What this version does

| Capability | Implementation |
|---|---|
| Browse | Real on-device WebView, JavaScript, first-party cookies, local web storage |
| Search | Opens Google search in the browser; no paid search API |
| Read | Main-frame visible text, headings, links, detected prices, labeled controls |
| Source tracking | URL, title, capture time, snapshot ID |
| AI analysis | Optional OpenAI Responses adapter; user-supplied key held in process memory |
| Generated presentation | Native cards with validated source links and proposed next action |
| Agent tools | Open, search, snapshot, click, fill, scroll |
| Approval | Native host confirmation for every agent click/fill; denied by default in SDK |
| Reuse | `bo-core` AAR or source integration, no runtime third-party dependencies |
| Inter-app | Explicit Android activity intent; explicit user action returns `bo.snapshot` JSON |
| Export | Android share sheet for a captured JSON document or page URL |

**This is an assisted browser foundation, not an autonomous crawler.** The demo app analyzes one capture at a time and proposes one step. The host app can implement its own bounded loop using the SDK. No promise of CAPTCHA bypass, universal website compatibility, or background monitoring.

## Why this architecture

Your auction, product-research and personal-assistant apps can reuse a visible browser component. A host agent observes a compact snapshot, proposes an action, obtains approval when required and observes again. The page remains usable by a person for login and challenges.

- Small APK download: the browser engine is already supplied by Android.
- APK size is **not** total RAM use or installed WebView size. WebView has its own rendering process and memory cost.
- Cookies are isolated from Chrome. Embedded SDK instances use their **host app's** WebView storage; they are not isolated per BoBrowser instance.
- Keeping WebView up to date remains essential.

## Embed it

Copy `sdk/bo-core-0.1.0.aar` into the host app's `app/libs/`, then:

```gradle
dependencies {
    implementation files('libs/bo-core-0.1.0.aar')
}
```

Set the host's `minSdk` to 28+, enable Java 8 source compatibility, add Internet permission and disallow cleartext traffic. See [the complete integration guide](docs/INTEGRATION.md).

```java
BoBrowser browser = new BoBrowser(activity);
container.addView(browser);
browser.setConfirmation((action, decision) -> {
    // Show YOUR native approval UI with the current origin and full action.
    // Call decision.decide(true) only after the user confirms it.
    decision.decide(false); // Safe default until approval UI is implemented.
});
browser.setListener(new BoBrowser.Listener() {
    public void onPage(String url, String title, boolean loading) {
        if (!loading) browser.snapshot(snapshot -> hostAgent.observe(snapshot));
    }
});
browser.navigate("https://example.com");
```

`hostAgent` above represents your app's adapter. Implement `AgentProvider` for your existing backend or local model. Bo does not download Gemma, JEV or any other weights.

## AI configuration

**Agente → Conectar OpenAI** accepts a personal API key and a compatible model ID. The default is `gpt-4o-mini`; account access and availability are not guaranteed. Each analysis asks permission before sending the visible-page snapshot and prompt. API usage is charged to that account. The app sets `store:false`; provider data policies still apply.

The key is never written to preferences, web storage, assets or source. It lives in memory for the process lifetime. An already-started network request may finish after disconnecting, but its result is discarded. Use your own authenticated backend for distributed commercial apps; do not bake a shared API key into an APK.

## Build

Requires JDK 17+, Python 3, Android platform API 35+ and Android build tools with `aapt2`, `d8`, `zipalign` and `apksigner`.

```bash
export ANDROID_JAR="$ANDROID_HOME/platforms/android-36/android.jar"
export BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
./scripts/build.sh
```

Outputs: `build/Bo-v0.1.0.apk` and `build/bo-core-0.1.0.aar`.

The default build generates a private development signing key under `~/.android/`. Keep that key for compatible updates. To use your production key, set `BO_KEYSTORE`, `BO_STOREPASS`, and `BO_KEY_ALIAS`. Never commit keys. CI builds use disposable development keys and cannot necessarily update this APK without uninstalling it.

## Validation and boundaries

See [validation results](docs/VALIDATION.md) and [security assumptions](docs/SECURITY.md).

Current limits: one tab; main frame only; no shadow-DOM traversal; no downloads, file upload, extension support, camera/microphone access, OAuth handoff or continuous background agent. JavaScript-heavy pages may need a second capture after rendering. Some sites reject WebView. Use **Abrir em outro navegador** to hand off to Chrome manually; Bo cannot inspect Chrome's resulting page.

## References

- [Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview)
- [WebView API](https://developer.android.com/reference/android/webkit/WebView)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)

Project version: 0.1.0. Development preview.
