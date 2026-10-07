# Validation — 0.1.0

Build date: 2026-10-07.

## Completed

- Java compilation against Android API 36 with Java 8 bytecode.
- Android resource compilation and linking.
- DEX packaging, ZIP alignment, APK signing and signature verification.
- APK manifest inspected: `com.bo.app`, minimum API 28, target API 35, Internet permission only.
- Embeddable AAR assembled with classes, manifest and reader assets.
- 14 extractor/action checks executed against a real headless Chromium browser on Linux.

Checks covered visible text, source links, headings, prices, sensitive field exclusion, hidden content, valid fill/click, changed elements, stale snapshots, navigation changes, unsafe links, challenges, empty pages, output bounds and the local demo.

## Not verified

- Installation and native UI execution on a physical Android device or emulator.
- Samsung keyboard, gesture-navigation and accessibility behavior on device.
- A live OpenAI request with a working user key.
- Specific auction-site compatibility, logins, CAPTCHA completion or hostile-site security audit.
- CI workflow execution on GitHub; the source workflow is provided.

The Playwright checks exercise the DOM reader in desktop Chromium with a mobile viewport. They do not prove Android WebView or native UI behavior.

## Artifact metrics

- APK: 41,549 bytes (about 42 KB).
- AAR: 20,278 bytes (about 20 KB).
- APK SHA-256: `bb7d7c11aedee9101f48bef7375312529a9a39fcc190aee9729affc690f5b5d5`.
- AAR SHA-256: `14be71dd8f4b2bed9853db006a4d13891e7cf2a7103af9c0a3e7dd57115551ad`.

These are download artifact sizes, not total installed size, RAM usage, engine size or a performance benchmark. The APK uses a development signing certificate; it is not a production-certified release.

## Repeat the reader checks

```bash
npm install --no-save playwright@1.51.1
npx playwright install chromium
node tests/reader.test.cjs
```

For an existing Chromium executable, set `BO_CHROMIUM=/absolute/path/to/chromium`. Output includes an optional local demo screenshot under ignored `tests/output/`.

## First device smoke test

1. Install the APK and open the built-in demo.
2. Tap **Ler**: verify two extracted prices and exported JSON.
3. Search a real term; open a result and capture again.
4. Try back, forward, reload, keyboard input and device rotation.
5. Configure a personal OpenAI key, consent to one analysis, verify source cards.
6. Test an action, cancel it, then retry and explicitly approve it.
7. Clear the session and confirm the current login and model key are removed.
