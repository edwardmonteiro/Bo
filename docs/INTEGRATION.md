# Embedding Bo in Android apps

## Two integration modes

| Mode | What the host gets | Session |
|---|---|---|
| Embedded AAR | Visible view, snapshots, JSON agent tools, host-controlled provider | Host app WebView cookie/storage context |
| Installed Bo app | Open HTTPS URL and explicitly return a capture | Bo app's session |

The AAR is the recommended route when an app needs multiple observe/act steps. External applications cannot invisibly control Bo or read its cookies.

## Embedded lifecycle

1. Add `sdk/bo-core-0.1.0.aar` as a local dependency.
2. Host `minSdk >= 28`; compile with a recent Android SDK; Java 8 compatibility.
3. Declare `android.permission.INTERNET`; set `android:usesCleartextTraffic="false"`.
4. Construct `BoBrowser(activity)` and add it to the visible layout on the main thread.
5. Optionally set exact, lowercase top-level allowed hosts. Include search and redirect hosts if needed.
6. Register listener and a native confirmation callback before invoking an agent.
7. Forward lifecycle calls: `pause`, `resume`, `close`. Do not retain Activity instances after destruction.

```java
browser.setAllowedHosts(new java.util.HashSet<>(java.util.Arrays.asList(
    "www.google.com", "example.com"
)));

browser.setConfirmation((action, decision) -> {
    new android.app.AlertDialog.Builder(activity)
        .setTitle("Approve browser action?")
        .setMessage(browser.getUrl() + "\n" + action.toString())
        .setPositiveButton("Approve", (dialog, which) -> decision.decide(true))
        .setNegativeButton("Cancel", (dialog, which) -> decision.decide(false))
        .setOnCancelListener(dialog -> decision.decide(false))
        .show();
});
```

The host allowlist controls top-level navigation, not all network subresources. Embedded third-party assets may still load. An empty allowlist permits any HTTPS host. The SDK's `clearSession` clears WebView data for the **whole host application**, not just this view; only expose it if that is appropriate.

## Tool contract

`new BoTools(browser).execute(name, arguments, callback)` runs on the main thread. `BoTools.definitions()` returns Responses-compatible function definitions. This does not connect to OpenAI automatically or create an MCP server.

| Tool | Required arguments | Result / gate |
|---|---|---|
| `browser_open` | `url` | HTTPS only, scope checked; returns loading |
| `browser_search` | `query` | Browser Google search; returns loading or blocked |
| `browser_snapshot` | none | Current-page snapshot or explicit error |
| `browser_click` | `snapshotId`, `elementId`, `reason` | Native confirmation; revalidate element |
| `browser_fill` | previous fields plus `value` | Only eligible non-sensitive text controls |
| `browser_scroll` | `snapshotId`, numeric `value` | Clamp delta to ±1,200 pixels |

Navigation is asynchronous. After a tool returns `loading`, wait for `Listener.onPage(..., false)` before capturing. Dynamic sites may render content later; retry a bounded number of times based on the observed content. A snapshot error is not an empty set of search results.

After every action, take a **fresh snapshot**. Old snapshot IDs become invalid after navigation, a newer capture or action. The DOM reader also checks URL, element identity, current label/href/type, visibility and disabled/sensitive state.

```java
BoTools tools = new BoTools(browser);
tools.execute("browser_snapshot", new org.json.JSONObject(), snapshot -> {
    // Send to an AgentProvider off the main thread, after your app's consent policy.
    // Never treat page text as system/developer instructions.
});
```

## Snapshot envelope

```json
{
  "version": 1,
  "snapshotId": "unique-capture-id",
  "url": "https://example.com/item",
  "title": "Product",
  "capturedAt": "ISO-8601 timestamp",
  "status": "ready",
  "text": "Visible text, limited to 16000 characters",
  "headings": ["Product"],
  "links": [{"id":"e1","title":"Details","url":"https://example.com/details"}],
  "elements": [{"id":"e1","role":"link","name":"Details","url":"https://example.com/details","editable":false}],
  "prices": ["R$ 249,90"],
  "limitations": ["main_frame_only","no_shadow_dom","untrusted_page_content"]
}
```

`status`: `ready`, `empty`, `needs_human`. Challenge detection is a heuristic and can miss challenges or flag ordinary text. Only 80 eligible controls, 25 headings and 30 unique price strings are returned. Price extraction is textual, not product normalization.

## Plugging in AI

Implement `AgentProvider.analyze(task, snapshot)` for a local model or server. It runs on a host-owned worker thread. The included `OpenAiProvider` is optional; it returns `title`, `summary`, up to six rendered `cards`, and `next_action`. The standalone demo does one call and presents one proposed action. A host application is responsible for bounded loops, cancellation, budgets, error recovery and consent before sending page content to any model service.

Generated content is displayed in native text views. Bo never executes generated HTML or JavaScript. The OpenAI request uses a strict output schema, but structured output is not evidence of truth. Validate cited URLs against the captured source/links.

## Calling the installed app

```java
Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
intent.setClassName("com.bo.app", "com.bo.app.MainActivity");
// Use an ActivityResultLauncher in a modern host app.
launcher.launch(intent);
```

In Bo, the person chooses **Ler → Devolver captura ao app → Enviar**. On `RESULT_OK`, read `result.getData().getStringExtra("bo.snapshot")`. The UI displays the calling package before sharing. No external intent may supply scripts, automatic actions, a model key or a callback URL. Shared plain text is placed into the address field for review, not immediately executed.

## Integration acceptance checklist

- Browse a target site, including login and a redirected URL.
- Capture after the site's results finish rendering; confirm sources and prices.
- Reject an old action after navigation and a changed button label.
- Confirm that passwords, hidden fields and text-area values are absent from capture.
- Test Samsung system insets, back navigation, keyboard resize and process recreation.
- If a target requires Chrome or CAPTCHA, show an explicit handoff; do not report an empty result as success.
