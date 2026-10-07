package com.bo.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Looper;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Visible, embeddable Chromium browser. All public operations run on the UI thread. */
public final class BoBrowser extends FrameLayout {
    public interface Result { void complete(JSONObject result); }
    public interface Approval { void decide(boolean approved); }
    public interface Confirmation { void request(JSONObject action, Approval approval); }
    public interface Listener {
        default void onPage(String url, String title, boolean loading) {}
        default void onProgress(int progress) {}
        default void onError(String message) {}
    }
    private final WebView web;
    private final String reader;
    private Listener listener = new Listener() {};
    private Confirmation confirmation = (a, d) -> d.decide(false);
    private Set<String> allowedHosts = Collections.emptySet();
    private String snapshotId = "", snapshotUrl = "";
    private JSONObject snapshot;
    private boolean closed, loading;
    private long generation;

    @SuppressLint("SetJavaScriptEnabled")
    public BoBrowser(Context context) {
        super(context);
        reader = readAsset(context, "bo/reader.js");
        web = new WebView(context);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true); s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebView.setWebContentsDebuggingEnabled(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!allowed(r.getUrl().toString())) { listener.onError("Endereço bloqueado. Use um link HTTPS permitido."); return true; }
                return false;
            }
            @Override public void onPageStarted(WebView v,String url,Bitmap icon) {
                invalidateSnapshot(); loading=true; listener.onPage(url,"Carregando…",true);
            }
            @Override public void onPageFinished(WebView v,String url) {
                loading=false; CookieManager.getInstance().flush(); listener.onPage(url,v.getTitle(),false);
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) {
                if(r.isForMainFrame()) { loading=false; listener.onError("Não foi possível abrir a página: " + e.getDescription()); }
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e) {
                if(r.isForMainFrame()) listener.onError("O site respondeu HTTP " + e.getStatusCode() + ". Pode exigir acesso manual.");
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e) {
                h.cancel(); listener.onError("Certificado inválido. Conexão cancelada.");
            }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d) {
                closed=true; removeView(web); web.destroy(); listener.onError("O motor do navegador encerrou. Feche e reabra o Bo."); return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v,int p) { listener.onProgress(p); }
            @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
        });
        addView(web,new LayoutParams(-1,-1));
    }
    public void setListener(Listener l) { listener=l; }
    public void setConfirmation(Confirmation c) { confirmation=c; }
    /** Top-level navigation scope; subresources are still controlled by the page. */
    public void setAllowedHosts(Set<String> hosts) { allowedHosts=new HashSet<>(); for(String h:hosts) allowedHosts.add(h.toLowerCase(Locale.ROOT)); }
    public static boolean isHttps(String value) {
        try { Uri u=Uri.parse(value); return "https".equalsIgnoreCase(u.getScheme()) && u.getHost()!=null && !u.getHost().isEmpty() && u.getUserInfo()==null; } catch(Exception e) { return false; }
    }
    private boolean allowed(String url) { return isHttps(url) && (allowedHosts.isEmpty() || allowedHosts.contains(Uri.parse(url).getHost().toLowerCase(Locale.ROOT))); }
    public boolean navigate(String url) {
        main(); if(closed || !allowed(url)) { listener.onError("Use um endereço HTTPS permitido."); return false; }
        invalidateSnapshot(); loading=true; web.loadUrl(url); return true;
    }
    public void search(String query) { navigate("https://www.google.com/search?q="+Uri.encode(query)); }
    public String getUrl() { return closed ? "" : web.getUrl(); }
    public void back() { main(); if(!closed && web.canGoBack()) { invalidateSnapshot(); web.goBack(); } }
    public boolean canGoBack() { return !closed && web.canGoBack(); }
    public void forward() { main(); if(!closed && web.canGoForward()) { invalidateSnapshot(); web.goForward(); } }
    public void reload() { main(); if(!closed) { invalidateSnapshot(); web.reload(); } }
    public void pause() { if(!closed) web.onPause(); }
    public void resume() { if(!closed) web.onResume(); }
    public void close() { main(); if(!closed) { closed=true; removeView(web); web.stopLoading(); web.destroy(); } }
    public void clearSession(Runnable done) {
        main(); if(closed) return;
        invalidateSnapshot(); web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.clearCache(true);
        WebStorage.getInstance().deleteAllData(); CookieManager.getInstance().removeAllCookies(ok -> { CookieManager.getInstance().flush(); done.run(); });
    }
    /** Host-owned HTML only. Never pass generated model HTML or arbitrary website HTML. */
    public void loadDemo(String html) { main(); if(!closed) { invalidateSnapshot(); web.loadDataWithBaseURL("https://bo.demo/",html,"text/html","UTF-8",null); } }
    public void snapshot(Result callback) {
        main(); if(closed || loading) { callback.complete(error(closed?"closed":"page_loading")); return; }
        String id=UUID.randomUUID().toString(), url=web.getUrl(); long g=generation;
        evaluate("snapshot",json("id",id), result -> {
            if(g!=generation || !Objects.equals(url,web.getUrl())) { callback.complete(error("navigation_changed")); return; }
            if(result.has("snapshotId")) { snapshotId=id; snapshotUrl=url; snapshot=result; }
            callback.complete(result);
        });
    }
    /** Model-proposed actions; click/fill always use the host's native confirmation callback. */
    public void act(JSONObject action, Result callback) {
        main();
        final JSONObject copy;
        try { copy=new JSONObject(action.toString()); } catch(Exception e) { callback.complete(error("invalid_action")); return; }
        if(!validAction(copy)) { callback.complete(error("invalid_or_stale_action")); return; }
        if("scroll".equals(copy.optString("type"))) { execute(copy,callback); return; }
        final boolean[] resolved={false};
        confirmation.request(copy, approved -> post(() -> {
            if(resolved[0]) return; resolved[0]=true;
            if(!approved) { callback.complete(error("user_cancelled")); return; }
            if(!validAction(copy)) { callback.complete(error("stale_snapshot")); return; }
            execute(copy,callback);
        }));
    }
    private boolean validAction(JSONObject a) {
        if(closed || loading || snapshot==null || !snapshotId.equals(a.optString("snapshotId")) || !Objects.equals(snapshotUrl,web.getUrl())) return false;
        String type=a.optString("type");
        if("scroll".equals(type)) return true;
        if(!"click".equals(type) && !"fill".equals(type)) return false;
        JSONArray es=snapshot.optJSONArray("elements");
        for(int i=0;i<es.length();i++) { JSONObject el=es.optJSONObject(i); if(el.optString("id").equals(a.optString("elementId"))) return !"fill".equals(type) || el.optBoolean("editable"); }
        return false;
    }
    private void execute(JSONObject a,Result cb) { evaluate("action",a,r -> { invalidateSnapshot(); cb.complete(r); }); }
    private void evaluate(String mode,JSONObject arg,Result cb) {
        if(closed) { cb.complete(error("closed")); return; }
        web.evaluateJavascript(reader+"("+JSONObject.quote(mode)+","+arg.toString()+")", raw -> {
            try { Object value=new JSONTokener(raw).nextValue(); cb.complete(value instanceof JSONObject ? (JSONObject)value : error("invalid_page_result")); }
            catch(Exception e) { cb.complete(error("extraction_failed")); }
        });
    }
    private void invalidateSnapshot() { generation++; snapshotId=""; snapshotUrl=""; snapshot=null; }
    private static void main() { if(Looper.myLooper()!=Looper.getMainLooper()) throw new IllegalStateException("BoBrowser requires the main thread"); }
    public static JSONObject json(String key,Object value) { JSONObject o=new JSONObject(); try{o.put(key,value);}catch(Exception ignored){} return o; }
    public static JSONObject error(String msg) { JSONObject o=json("ok",false); try{o.put("error",msg);}catch(Exception ignored){} return o; }
    public static String readAsset(Context c,String name) {
        try(InputStream in=c.getAssets().open(name); ByteArrayOutputStream b=new ByteArrayOutputStream()) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1)b.write(buf,0,n); return b.toString("UTF-8"); } catch(Exception e) { throw new IllegalStateException("Missing Bo asset: "+name,e); }
    }
}
