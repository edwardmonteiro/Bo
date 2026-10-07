package com.bo.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.bo.core.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final int BG=Color.rgb(246,245,239), INK=Color.rgb(25,47,42), MUTED=Color.rgb(104,118,111), GREEN=Color.rgb(8,127,112), LINE=Color.rgb(223,227,217);
    private LinearLayout root,home,readerPage,agentPage,readerBody,agentBody;
    private FrameLayout content;
    private EditText address,taskInput;
    private TextView status;
    private ProgressBar progress;
    private BoBrowser browser;
    private JSONObject currentSnapshot;
    private String key="",model="gpt-4o-mini",lastTask="",view="home";
    private boolean busy=false,destroyed=false;
    private int requestId=0;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("bo",MODE_PRIVATE); model=prefs.getString("model","gpt-4o-mini");
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) { android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom); }
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        LinearLayout top=row();top.setPadding(dp(16),dp(8),dp(12),dp(4));
        TextView brand=label("bo",30,INK,true);brand.setPadding(0,0,dp(16),0);brand.setOnClickListener(v->show("home"));brand.setContentDescription("Bo início");top.addView(brand);
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(15);address.setHint("Buscar ou abrir endereço");address.setTextColor(INK);address.setHintTextColor(MUTED);address.setBackground(round(Color.WHITE,18));address.setPadding(dp(14),0,dp(10),0);address.setSelectAllOnFocus(true);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);top.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        address.setOnEditorActionListener((v,a,e)->{openInput(address.getText().toString());return true;});
        top.addView(button("⋯",v->menu(),false),new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(top);
        status=label("SEU NAVEGADOR, PRONTO PARA IA",10,MUTED,true);status.setPadding(dp(22),dp(4),dp(16),dp(7));root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));progress.setVisibility(View.INVISIBLE);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        browser=new BoBrowser(this);content.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        browser.setListener(new BoBrowser.Listener(){
            public void onPage(String url,String title,boolean loading){
                if(!address.hasFocus())address.setText(url!=null&&url.equals("https://bo.demo/")?"bo.demo":url);
                status.setText(loading?"ABRINDO PÁGINA…":(title==null?"PÁGINA":title));
                if(loading){currentSnapshot=null;requestId++;busy=false;}
            }
            public void onProgress(int p){progress.setProgress(p);progress.setVisibility(p<100?View.VISIBLE:View.INVISIBLE);}
            public void onError(String msg){status.setText(msg);toast(msg);}
        });
        browser.setConfirmation((action,approval)->{
            String target=elementName(action.optString("elementId"));
            new AlertDialog.Builder(this).setTitle("Confirmar ação")
                .setMessage("Página: "+browser.getUrl()+"\n\n"+action.optString("type")+" → "+target+"\n"+action.optString("value")+"\n\n"+action.optString("reason"))
                .setPositiveButton("Executar",(d,w)->approval.decide(true)).setNegativeButton("Cancelar",(d,w)->approval.decide(false))
                .setOnCancelListener(d->approval.decide(false)).show();
        });
        home=column();ScrollView homeScroll=scroll(home);content.addView(homeScroll);homeScroll.setTag("home");buildHome();
        readerBody=column();readerPage=column();readerPage.addView(scroll(readerBody),new LinearLayout.LayoutParams(-1,-1));content.addView(readerPage);
        agentBody=column();agentPage=column();agentPage.addView(scroll(agentBody),new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout prompt=row();prompt.setPadding(dp(12),dp(8),dp(12),dp(8));
        taskInput=new EditText(this);taskInput.setTextSize(15);taskInput.setHint("O que quer descobrir nesta página?");taskInput.setMinLines(1);taskInput.setMaxLines(3);taskInput.setBackground(round(Color.WHITE,16));taskInput.setPadding(dp(12),dp(10),dp(12),dp(10));prompt.addView(taskInput,new LinearLayout.LayoutParams(0,-2,1));prompt.addView(button("Enviar",v->analyze(),true));agentPage.addView(prompt);content.addView(agentPage);agentIntro();
        LinearLayout nav=row();nav.setPadding(dp(8),dp(6),dp(8),dp(6));
        nav.addView(button("‹",v->{browser.back();show("web");},false),new LinearLayout.LayoutParams(dp(44),dp(48)));
        nav.addView(button("Página",v->show(browser.getUrl()==null?"home":"web"),false),new LinearLayout.LayoutParams(0,dp(48),1));
        nav.addView(button("Ler",v->capture(false),false),new LinearLayout.LayoutParams(0,dp(48),1));
        nav.addView(button("✦ Agente",v->show("agent"),true),new LinearLayout.LayoutParams(0,dp(48),1));root.addView(nav);
        show("home"); handleIntent(getIntent());
    }
    private void buildHome(){
        home.removeAllViews();home.setPadding(dp(24),dp(20),dp(24),dp(20));
        TextView eyebrow=label("BO / BROWSER FOR AGENTS",11,GREEN,true);home.addView(eyebrow);
        TextView title=label("Menos navegador.\nMais descoberta.",38,INK,true);title.setLineSpacing(dp(3),1);space(home,20);home.addView(title);
        space(home,12);home.addView(label("Abra a web. Leia o que importa.\nLeve os resultados para seus apps.",17,MUTED,false));space(home,28);
        LinearLayout hero=card(home);hero.setBackground(round(INK,22));hero.addView(label("UMA WEB QUE SUA IA CONSEGUE LER",11,0xffd7efa7,true));space(hero,12);hero.addView(label("Navegue → Capture → Analise",20,Color.WHITE,true));space(hero,10);hero.addView(label("Texto, links e preços com a fonte original.",14,0xffd7dfd5,false));space(hero,16);hero.addView(button("Experimentar com uma página demo  ↗",v->demo(),true));
        space(home,24);home.addView(label("COMECE UMA BUSCA",11,MUTED,true));space(home,10);
        home.addView(button("↗  SW4 blindada em leilões de São Paulo",v->openInput("SW4 blindada leilão São Paulo"),false));
        home.addView(button("↗  Pesquisar um produto",v->{address.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT);},false));
        space(home,18);home.addView(label("CHROMIUM DO ANDROID  •  UMA ABA  •  SEM CONTA BO",10,MUTED,true));
        space(home,16);home.addView(button("Configurar IA opcional",v->settings(),false));
    }
    private void agentIntro(){agentBody.removeAllViews();agentBody.setPadding(dp(22),dp(22),dp(22),dp(18));agentBody.addView(label("Uma página.\nNovas possibilidades.",30,INK,true));space(agentBody,12);agentBody.addView(label("Abra um site e pergunte. O Bo usa o conteúdo carregado para montar uma resposta com fontes.",16,MUTED,false));space(agentBody,16);agentBody.addView(button("Resumir e destacar os pontos importantes",v->taskInput.setText("Resuma esta página e destaque os pontos importantes."),false));agentBody.addView(button("Encontrar preços e condições",v->taskInput.setText("Extraia preços, condições e limitações visíveis, com suas fontes."),false));space(agentBody,18);agentBody.addView(label("Sem chave: navegação, leitura e exportação locais.\nCom OpenAI: a captura e sua pergunta são enviadas somente após sua confirmação.",13,MUTED,false));agentBody.addView(button("Conectar OpenAI",v->settings(),false));}
    private void demo(){browser.loadDemo(BoBrowser.readAsset(this,"bo/demo.html"));show("web");}
    private void show(String name){view=name;for(int i=0;i<content.getChildCount();i++){View c=content.getChildAt(i);boolean yes=(c==browser&&name.equals("web"))||(c==readerPage&&name.equals("reader"))||(c==agentPage&&name.equals("agent"))||("home".equals(c.getTag())&&name.equals("home"));c.setVisibility(yes?View.VISIBLE:View.GONE);}hideKeyboard();address.clearFocus();}
    private void openInput(String raw){String q=raw.trim();if(q.isEmpty())return;show("web");if(q.startsWith("https://")){browser.navigate(q);}else if(q.matches("(?i)^[a-z][a-z0-9+.-]*:.*")){toast("Apenas endereços HTTPS são aceitos.");}else if(!q.contains(" ")&&q.contains(".")){browser.navigate("https://"+q);}else browser.search(q);}
    private void capture(boolean share){
        if(browser.getUrl()==null){toast("Abra uma página ou experimente a demo.");return;}
        browser.snapshot(s->{if(s.has("error")){toast("Aguarde a página carregar e tente novamente. "+s.optString("error"));return;}currentSnapshot=s;if(share)share(s.toString());else renderReader(s);});
    }
    private void renderReader(JSONObject s){
        readerBody.removeAllViews();readerBody.setPadding(dp(22),dp(18),dp(22),dp(20));readerBody.addView(label("CAPTURA LOCAL",11,GREEN,true));space(readerBody,10);readerBody.addView(label(s.optString("title","Página"),28,INK,true));readerBody.addView(label(s.optString("url"),12,MUTED,false));space(readerBody,12);
        if(!"ready".equals(s.optString("status")))readerBody.addView(label("Esta página pode exigir interação manual ou ainda não tem conteúdo legível.",15,0xffa65c24,true));
        JSONArray prices=s.optJSONArray("prices"),links=s.optJSONArray("links");
        readerBody.addView(label((links==null?0:links.length())+" links  ·  "+(prices==null?0:prices.length())+" preços detectados",13,GREEN,true));
        if(prices!=null&&prices.length()>0){LinearLayout c=card(readerBody);c.addView(label("PREÇOS NO TEXTO",11,MUTED,true));c.addView(label(prices.toString().replace("\"","").replace("[","").replace("]",""),20,INK,true));c.addView(label("Valores extraídos, sem validação de disponibilidade.",12,MUTED,false));}
        LinearLayout actions=row();actions.addView(button("Exportar JSON",v->share(s.toString()),true));actions.addView(button("Perguntar à IA",v->show("agent"),false));readerBody.addView(actions);
        if(getCallingPackage()!=null)readerBody.addView(button("Devolver captura ao app",v->new AlertDialog.Builder(this).setTitle("Enviar captura?").setMessage("Destino: "+getCallingPackage()+"\n\nInclui texto e links desta página.").setPositiveButton("Enviar",(d,w)->{setResult(RESULT_OK,new Intent().putExtra("bo.snapshot",s.toString()));finish();}).setNegativeButton("Cancelar",null).show(),false));
        space(readerBody,20);TextView text=label(s.optString("text"),16,INK,false);text.setTextIsSelectable(true);text.setLineSpacing(dp(5),1);readerBody.addView(text);
        space(readerBody,20);readerBody.addView(label("LINKS DA PÁGINA",11,GREEN,true));if(links!=null)for(int i=0;i<Math.min(links.length(),30);i++){JSONObject l=links.optJSONObject(i);readerBody.addView(button(l.optString("title")+"  ↗",v->openInput(l.optString("url")),false));}show("reader");
    }
    private void analyze(){
        if(busy){toast("A análise já está em andamento.");return;}
        if(key.isEmpty()){settings();return;}if(browser.getUrl()==null){toast("Abra uma página primeiro.");return;}
        String task=taskInput.getText().toString().trim();if(task.isEmpty()){toast("Escreva o que deseja descobrir.");return;}
        browser.snapshot(s->{if(s.has("error")){toast("Aguarde a página carregar e tente novamente.");return;}currentSnapshot=s;
            new AlertDialog.Builder(this).setTitle("Analisar com OpenAI?").setMessage("Será enviada uma captura de "+s.optString("url")+" com texto, links e sua pergunta. Ela pode conter dados pessoais visíveis.\n\nModelo: "+model+". Uso cobrado na sua conta da API.")
                .setPositiveButton("Analisar",(d,w)->runAnalysis(task,s)).setNegativeButton("Cancelar",null).show();
        });
    }
    private void runAnalysis(String task,JSONObject snapshot){
        if(busy)return;busy=true;lastTask=task;int id=++requestId;OpenAiProvider provider=new OpenAiProvider(key,model);
        agentBody.removeAllViews();agentBody.addView(label("Lendo a página…",26,INK,true));space(agentBody,12);agentBody.addView(label("A análise usa apenas esta captura. Você pode continuar navegando.",14,MUTED,false));show("agent");
        worker.execute(()->{try{JSONObject result=provider.analyze(task,snapshot);runOnUiThread(()->{if(destroyed||id!=requestId)return;busy=false;renderAnalysis(result,snapshot);});}catch(Exception e){runOnUiThread(()->{if(destroyed||id!=requestId)return;busy=false;agentBody.removeAllViews();agentBody.addView(label("Não foi possível analisar",24,INK,true));space(agentBody,12);agentBody.addView(label(e.getMessage()==null?"Falha de conexão.":e.getMessage(),16,MUTED,false));agentBody.addView(button("Tentar novamente",v->analyze(),true));});}});
    }
    private void renderAnalysis(JSONObject result,JSONObject s){
        agentBody.removeAllViews();agentBody.addView(label("ANÁLISE · "+model.toUpperCase(Locale.ROOT),10,GREEN,true));space(agentBody,10);agentBody.addView(label(result.optString("title"),27,INK,true));space(agentBody,12);agentBody.addView(label(result.optString("summary"),16,INK,false));
        JSONArray cards=result.optJSONArray("cards");if(cards!=null)for(int i=0;i<Math.min(cards.length(),6);i++){JSONObject c=cards.optJSONObject(i);LinearLayout box=card(agentBody);box.addView(label(c.optString("title"),18,INK,true));space(box,6);box.addView(label(c.optString("body"),15,MUTED,false));String url=c.optString("source_url");if(knownSource(url,s))box.addView(button("Ver fonte ↗",v->openInput(url),false));else box.addView(label("Fonte não reconhecida na captura.",12,0xffa65c24,false));}
        JSONObject a=result.optJSONObject("next_action");if(a!=null&&!"none".equals(a.optString("type"))){try{a.put("snapshotId",s.optString("snapshotId"));}catch(Exception ignored){}LinearLayout box=card(agentBody);box.addView(label("PRÓXIMO PASSO SUGERIDO",11,GREEN,true));box.addView(label(a.optString("reason"),16,INK,false));box.addView(button("Revisar ação",v->browser.act(a,r->{toast(r.optBoolean("ok")?"Ação executada. Você pode analisar a nova página.":"Ação não executada: "+r.optString("error"));if(r.optBoolean("ok"))show("web");}),true));}
        space(agentBody,14);agentBody.addView(label("Capturado em "+s.optString("capturedAt")+"\nConfira a fonte antes de decidir.",12,MUTED,false));
    }
    private boolean knownSource(String url,JSONObject s){if(!BoBrowser.isHttps(url))return false;if(url.equals(s.optString("url")))return true;JSONArray ls=s.optJSONArray("links");if(ls!=null)for(int i=0;i<ls.length();i++)if(url.equals(ls.optJSONObject(i).optString("url")))return true;return false;}
    private String elementName(String id){if(currentSnapshot!=null){JSONArray es=currentSnapshot.optJSONArray("elements");if(es!=null)for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(id.equals(e.optString("id")))return e.optString("name");}}return id;}
    private void settings(){
        LinearLayout box=column();box.setPadding(dp(22),dp(12),dp(22),0);box.addView(label("A chave fica somente na memória e é apagada ao encerrar o processo. Navegação e leitura funcionam sem IA.",14,MUTED,false));
        EditText k=new EditText(this);k.setHint("Chave da API OpenAI");k.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);k.setSingleLine(true);k.setText(key);k.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);box.addView(k);
        EditText m=new EditText(this);m.setHint("Modelo compatível com Structured Outputs");m.setSingleLine(true);m.setText(model);box.addView(m);
        new AlertDialog.Builder(this).setTitle("Conectar IA").setView(box).setPositiveButton("Salvar nesta sessão",(d,w)->{key=k.getText().toString().trim();String chosen=m.getText().toString().trim();if(!chosen.isEmpty())model=chosen;prefs.edit().putString("model",model).apply();toast(key.isEmpty()?"Modo local ativo.":"IA configurada nesta sessão.");}).setNeutralButton("Desconectar",(d,w)->{key="";requestId++;busy=false;agentIntro();toast("Chave removida.");}).setNegativeButton("Cancelar",null).show();
    }
    private void menu(){String[] items={"Início","Avançar","Recarregar","Ler / extrair página","Exportar captura JSON","Compartilhar endereço","Abrir em outro navegador","Conectar IA","Limpar dados de navegação","Sobre o Bo"};new AlertDialog.Builder(this).setTitle("bo / seu navegador leve").setItems(items,(d,w)->{switch(w){case 0:show("home");break;case 1:browser.forward();show("web");break;case 2:browser.reload();show("web");break;case 3:capture(false);break;case 4:capture(true);break;case 5:if(browser.getUrl()!=null)share(browser.getUrl());break;case 6:external();break;case 7:settings();break;case 8:new AlertDialog.Builder(this).setTitle("Limpar sessão?").setMessage("Remove cookies, cache e dados dos sites usados pelo Bo. Isso encerra seus logins no Bo.").setPositiveButton("Limpar",(a,b)->{requestId++;busy=false;key="";currentSnapshot=null;readerBody.removeAllViews();agentIntro();browser.clearSession(()->{show("home");address.setText("");toast("Sessão limpa.");});}).setNegativeButton("Cancelar",null).show();break;case 9:new AlertDialog.Builder(this).setTitle("Bo 0.1.0").setMessage("Chromium via Android System WebView. Uma aba, leitura local, SDK embutível e IA opcional.\n\nNão compartilha logins com o Chrome. Sites podem bloquear WebView; CAPTCHA e login precisam de você. Sem extensões, downloads ou monitoramento em segundo plano nesta versão.").setPositiveButton("Entendi",null).show();break;}}).show();}
    private void external(){String url=browser.getUrl();if(!BoBrowser.isHttps(url))return;try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));i.setPackage("com.android.chrome");startActivity(i);}catch(Exception e){toast("Chrome não encontrado. Compartilhe o endereço com outro navegador.");}}
    private void handleIntent(Intent i){if(i==null)return;String url=i.getDataString();if(url!=null&&BoBrowser.isHttps(url))openInput(url);else if(Intent.ACTION_SEND.equals(i.getAction())){String text=i.getStringExtra(Intent.EXTRA_TEXT);if(text!=null){address.setText(text.substring(0,Math.min(2000,text.length())));toast("Toque em Ir para abrir ou pesquisar o texto recebido.");}}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);}
    @Override protected void onPause(){super.onPause();if(browser!=null)browser.pause();}
    @Override protected void onResume(){super.onResume();if(browser!=null)browser.resume();}
    @Override protected void onDestroy(){destroyed=true;requestId++;key="";worker.shutdownNow();if(browser!=null)browser.close();super.onDestroy();}
    @Override public void onBackPressed(){if(!view.equals("web")&&!view.equals("home")){show("web");}else if(browser.canGoBack()){browser.back();show("web");}else if(!view.equals("home")){show("home");}else super.onBackPressed();}
    private void share(String text){Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"Compartilhar com…"));}
    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private ScrollView scroll(View child){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(child);return s;}
    private void space(LinearLayout l,int n){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private TextView label(String text,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    private GradientDrawable round(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private Button button(String text,View.OnClickListener click,boolean solid){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(solid?Color.WHITE:INK);b.setBackground(round(solid?GREEN:Color.TRANSPARENT,14));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setOnClickListener(click);return b;}
    private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(round(Color.WHITE,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);parent.addView(c,p);return c;}
}
