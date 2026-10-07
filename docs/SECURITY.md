# Security and trust boundaries

Bo loads untrusted web content. The page, extracted text and model output are all untrusted inputs.

Implemented controls:

- HTTPS navigation, invalid TLS certificates canceled, mixed content blocked.
- No `addJavascriptInterface`, no localhost control port, no exported automation service.
- No arbitrary-script tool or model-generated HTML execution.
- WebView file/content access disabled, third-party cookies disabled, media permissions denied.
- Native confirmation for every agent click and fill. SDK defaults to denying these actions.
- Snapshot-bound element references and validation after confirmation.
- Passwords, hidden inputs, payment/OTP/login-like fields excluded by type/name heuristics; input values are never extracted.
- Main-frame visible text only; hidden nodes, scripts, textarea values and editable text excluded.
- Explicit per-request consent before the standalone app sends content to OpenAI.
- Model key only in process memory; no logging, persistence, remote page exposure or hardcoded credentials.
- No automatic execution from shared text or deep links other than a validated HTTPS navigation.
- `allowBackup=false` in the standalone app.

Limits:

- A hostile website can misrepresent labels, prices, links and content. DOM validation is not an adversarial proof.
- Visible page text can contain personal or confidential data. Excluding input values is not general PII redaction.
- Prompt wording is not a complete prompt-injection defense. Native action confirmation and limited tools are the execution boundary.
- A click can have arbitrary site-defined side effects. Do not install a confirmation callback that blindly approves model actions.
- Top-level allowlists do not filter every page subresource.
- Cookies and storage are shared across WebViews in the host process/app profile. This is not per-agent isolation.
- Only the app's own WebView is controllable; Bo cannot inspect Chrome, other apps, or cross-origin iframe content.
- Key strings are not guaranteed to be zeroed in a managed runtime. Disconnect stops use by new requests; an in-flight request may finish.
- The included APK is a development preview. No physical-device test, security audit or live authenticated-site compatibility certification has been performed.

Before distributing a production host: use an authenticated backend for model access, persist an appropriate release signing key, test supported sites/devices and define your own domain/action/data-sharing policy.
