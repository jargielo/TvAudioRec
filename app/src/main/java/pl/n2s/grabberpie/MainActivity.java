package pl.n2s.grabberpie;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
  private static final int BLACK=0xff000000, CARD=0xff111111, CARD2=0xff191919, RED=0xffff244d,
   WHITE=0xfff4f4f4, MUTED=0xff9a9a9a, LINE=0xff343434;
  private static final String[] TABS={"NAGRYWANIE","MOJE NAGRANIA","ODTWARZACZ","USTAWIENIA","DIAGNOSTYKA"};
  private final Handler ui=new Handler();
  private final Runnable tick=new Runnable(){ @Override public void run(){ updateNow();ui.postDelayed(this,300); } };
  private LinearLayout root, nav, page, header;
  private TextView state, time, fileCount, playerLabel;
  private Meter meter;
  private SeekBar progress;
  private MediaPlayer player;
  private File currentFile;
  private final List<TextView> tabText=new ArrayList<>();
  private final List<File> items=new ArrayList<>();
  private int tab=0;
  private int sort=0;
  private String search="";
  private SharedPreferences prefs;
  private boolean uiReady;
  private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
  private Typeface thin(){return Typeface.create("sans-serif-light",Typeface.NORMAL);}
  private Typeface regular(){return Typeface.create("sans-serif",Typeface.NORMAL);}
  private Typeface medium(){return Typeface.create("sans-serif-medium",Typeface.NORMAL);}
  private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private GradientDrawable back(int fill,int stroke,int radius){
    GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);return g;
  }
  private TextView label(String value,int size,int color,boolean bold){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
    t.setTypeface(bold?medium():regular());t.setGravity(Gravity.CENTER_VERTICAL);return t;
  }
  private void gap(LinearLayout host,int size){View v=new View(this);host.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
  private TextView addLabel(LinearLayout host,String value,int size,int color,boolean bold){
    TextView t=label(value,size,color,bold);host.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
  }
  private LinearLayout card(LinearLayout into){
    LinearLayout c=vertical();c.setPadding(dp(15),dp(16),dp(15),dp(16));c.setBackground(back(CARD,LINE,15));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);into.addView(c,p);return c;
  }
  private TextView title(LinearLayout into,String s){TextView t=addLabel(into,s,18,WHITE,true);t.setLetterSpacing(.025f);gap(into,14);return t;}
  private TextView micro(LinearLayout into,String s){return addLabel(into,s,12,MUTED,false);}
  private TextView h(LinearLayout into,String s){TextView t=addLabel(into,s,13,RED,true);t.setLetterSpacing(.12f);return t;}
  private void hairline(LinearLayout into){View v=new View(this);v.setBackgroundColor(LINE);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-1,dp(1));l.topMargin=dp(12);l.bottomMargin=dp(12);into.addView(v,l);}
  private Button button(String name, boolean active){
    Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(13);
    b.setTypeface(medium());b.setTextColor(active?BLACK:WHITE);b.setPadding(dp(10),0,dp(10),0);
    b.setBackground(back(active?RED:CARD2,active?RED:LINE,11));return b;
  }
  private Button block(LinearLayout into,String s,boolean accent,Runnable click){
    Button b=button(s,accent);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.topMargin=dp(5);into.addView(b,lp);
    b.setOnClickListener(v->click.run());return b;
  }
  private void actionStrip(LinearLayout into,String[] texts,int selected,java.util.function.IntConsumer change){
    HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);
    LinearLayout line=horizontal();line.setPadding(0,dp(2),0,dp(2));sc.addView(line);
    for(int i=0;i<texts.length;i++){
      final int j=i;Button b=button(texts[i],i==selected);
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));p.rightMargin=dp(7);line.addView(b,p);b.setOnClickListener(v->change.accept(j));
    }
    into.addView(sc,new LinearLayout.LayoutParams(-1,-2));
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BLACK);getWindow().setNavigationBarColor(BLACK);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    prefs=getSharedPreferences("grabber",MODE_PRIVATE);
    Intent launched=getIntent();
    if(launched!=null && launched.hasExtra("tab")) tab=launched.getIntExtra("tab",0);
    buildShell();
    showTab(tab);ui.post(tick);
  }
  private void buildShell(){
    root=vertical();root.setBackgroundColor(BLACK);setContentView(root);
    header=vertical();header.setPadding(dp(18),dp(16),dp(18),dp(15));root.addView(header);
    LinearLayout branding=horizontal();header.addView(branding);
    Ring logo=new Ring(this);LinearLayout.LayoutParams ir=new LinearLayout.LayoutParams(dp(72),dp(72));branding.addView(logo,ir);
    LinearLayout words=vertical();words.setPadding(dp(10),0,0,0);
    branding.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    TextView a=addLabel(words,"AudioRelay",29,WHITE,false);a.setTypeface(thin());a.setIncludeFontPadding(false);
    TextView g=addLabel(words,"Grabber",29,WHITE,false);g.setTypeface(thin());g.setIncludeFontPadding(false);
    TextView rev=addLabel(header,"AMOLED BLACK  •  ANDROID 9 PIE  •  v1.9.3",10,MUTED,false);rev.setGravity(Gravity.END);rev.setLetterSpacing(.08f);
    gap(header,12);
    HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
    nav=horizontal();nav.setPadding(dp(11),dp(7),dp(11),dp(9));scroll.addView(nav);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(58)));
    View divider=new View(this);divider.setBackgroundColor(LINE);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    for(int i=0;i<TABS.length;i++){
      final int index=i;
      TextView t=label(TABS[i],12,MUTED,true);t.setPadding(dp(12),dp(10),dp(12),dp(10));
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.rightMargin=dp(4);nav.addView(t,p);
      tabText.add(t);t.setOnClickListener(v->showTab(index));
    }
    ScrollView body=new ScrollView(this);body.setFillViewport(true);body.setVerticalScrollBarEnabled(false);
    page=vertical();page.setPadding(dp(17),dp(20),dp(17),dp(38));body.addView(page);
    root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    uiReady=true;
  }
  private void showTab(int index){
    tab=index;for(int i=0;i<tabText.size();i++){
      TextView t=tabText.get(i);boolean on=i==index;t.setTextColor(on?RED:MUTED);
      t.setBackground(back(on?0xff221016:BLACK,on?RED:0,11));
    }
    page.removeAllViews();state=null;time=null;meter=null;fileCount=null;playerLabel=null;progress=null;
    switch(index){
      case 0: recorderPage();break;
      case 1: filesPage();break;
      case 2: playerPage();break;
      case 3: settingsPage();break;
      default: diagnosticPage();
    }
    updateNow();
  }
  private File dir(){File d=getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(d==null)d=new File(getFilesDir(),"Music");if(!d.exists())d.mkdirs();return d;}
  private File trash(){File f=new File(dir(),".kosz");if(!f.exists())f.mkdirs();return f;}
  private boolean fav(File f){return prefs.getBoolean("fav_"+f.getAbsolutePath(),false);}
  private String fmt(){return prefs.getString("format","M4A");}
  private int kbps(){return prefs.getInt("rate",128);}
  private int hz(){return prefs.getInt("hz",44100);}
  private void recorderPage(){
    h(page,"REJESTRATOR DŹWIĘKU");gap(page,12);
    LinearLayout c=card(page);
    state=addLabel(c,"GOTOWY DO NAGRYWANIA",19,WHITE,true);
    gap(c,12);
    time=addLabel(c,"00:00:00",40,WHITE,false);time.setTypeface(Typeface.create("sans-serif-light",0));time.setGravity(Gravity.CENTER);
    meter=new Meter(this);c.addView(meter,new LinearLayout.LayoutParams(-1,dp(85)));
    gap(c,9);
    micro(c,"WIZUALIZACJA SYGNAŁU MIKROFONU");gap(c,11);
    block(c,"●   ROZPOCZNIJ NAGRYWANIE",true,this::start);
    block(c,"■   STOP I ZAPISZ",false,this::stop);
    gap(page,8);
    LinearLayout mode=card(page);h(mode,"ŹRÓDŁO DŹWIĘKU");gap(mode,9);
    actionStrip(mode,new String[]{"MIKROFON","WEWNĘTRZNY","MIKROFON + BLUETOOTH"},0,i->{
      if(i==0)toast("Mikrofon urządzenia aktywny");
      else if(i==1)new AlertDialog.Builder(this).setTitle("Nagrywanie wewnętrzne")
        .setMessage("Android 9 Pie nie udostępnia API do nagrywania dźwięku z innych aplikacji. Ta funkcja jest dostępna od Androida 10, o ile źródłowa aplikacja na to pozwala.")
        .setPositiveButton("ROZUMIEM",null).show();
      else toast("Wybór mikrofonu Bluetooth zależy od obsługi zestawu przez EMUI 9.1.");
    });
    gap(mode,12);micro(mode,"Android 9: nagrywanie z mikrofonu. Nie oznaczamy mikrofonu jako „wewnętrzne audio”.");
    LinearLayout quick=card(page);
    h(quick,"SZYBKIE USTAWIENIA NAGRYWANIA");gap(quick,17);
    micro(quick,"FORMAT NAGRANIA");gap(quick,6);
    String[] ff={"M4A","WAV","MP3","FLAC","OGG"};
    actionStrip(quick,ff,Math.max(0,Arrays.asList(ff).indexOf(fmt())),i->{
      if(i>1) {toast(ff[i]+": format niedostępny na Androidzie 9 w tej wersji.");return;}
      prefs.edit().putString("format",ff[i]).apply();showTab(0);
    });
    hairline(quick);micro(quick,"JAKOŚĆ AAC / BITRATE");gap(quick,8);
    int[] rates={96,128,192,256,320};
    String[] rr={"96","128","192","256","320"};
    int ri=1;for(int i=0;i<rates.length;i++)if(rates[i]==kbps())ri=i;
    actionStrip(quick,rr,ri,i->{prefs.edit().putInt("rate",rates[i]).apply();showTab(0);});
    hairline(quick);micro(quick,"CZĘSTOTLIWOŚĆ");gap(quick,8);
    int[] samples={16000,32000,44100,48000};
    String[] ss={"16 kHz","32 kHz","44,1 kHz","48 kHz"};
    int si=2;for(int i=0;i<samples.length;i++)if(samples[i]==hz())si=i;
    actionStrip(quick,ss,si,i->{prefs.edit().putInt("hz",samples[i]).apply();showTab(0);});
    hairline(quick);micro(quick,"KANAŁY");gap(quick,9);
    actionStrip(quick,new String[]{"MONO","STEREO"},0,i->{
      if(i==1)toast("Dla stabilności Huawei P20 Lite używa nagrywania mono.");
    });
    gap(page,11);block(page,"MOJE NAGRANIA  ›",false,()->showTab(1));
  }
  private void start(){
    if(RecordingService.running){toast("Nagrywanie już trwa");return;}
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42);return;
    }
    Intent s=new Intent(this,RecordingService.class).setAction(RecordingService.START);
    s.putExtra("format",fmt());s.putExtra("rate",kbps());s.putExtra("hz",hz());
    startForegroundService(s);ui.postDelayed(this::updateNow,450);
  }
  private void stop(){
    startService(new Intent(this,RecordingService.class).setAction(RecordingService.STOP));
    ui.postDelayed(()->{updateNow();if(tab==1)showTab(1);},600);
  }
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] x){
    super.onRequestPermissionsResult(r,p,x);
    if(r==42){if(x.length>0&&x[0]==PackageManager.PERMISSION_GRANTED)start();
      else toast("Bez pozwolenia na mikrofon nagrywanie nie jest możliwe");}
  }
  private void updateNow(){
    if(!uiReady)return;
    if(state!=null) {
      state.setText(RecordingService.running?"●  TRWA NAGRYWANIE":"GOTOWY DO NAGRYWANIA");
      state.setTextColor(RecordingService.running?RED:WHITE);
      if(!RecordingService.running && RecordingService.lastError!=null && !RecordingService.lastError.isEmpty())
        state.setText("BŁĄD: "+RecordingService.lastError);
    }
    if(time!=null){long sec=RecordingService.running?(System.currentTimeMillis()-RecordingService.startedAt)/1000:0;
      time.setText(String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60));}
    if(meter!=null)meter.invalidate();
    if(player!=null && progress!=null){
      try{progress.setMax(player.getDuration());progress.setProgress(player.getCurrentPosition());}catch(Exception ignored){}
    }
  }
  private void scan(){
    items.clear();File[] f=dir().listFiles();if(f!=null)for(File v:f){
      if(v.isFile()&&(v.getName().toLowerCase(Locale.ROOT).endsWith(".m4a") ||v.getName().toLowerCase(Locale.ROOT).endsWith(".wav")))items.add(v);
    }
    Collections.sort(items,(a,b)->sort==1?a.getName().compareToIgnoreCase(b.getName()):Long.compare(b.lastModified(),a.lastModified()));
  }
  private void filesPage(){
    scan();
    h(page,"MOJE NAGRANIA");gap(page,12);
    LinearLayout c=card(page);
    fileCount=addLabel(c,items.size()+" plików  •  Music/AudioRelay Grabber",15,WHITE,true);
    gap(c,14);
    actionStrip(c,new String[]{"NAJNOWSZE","NAZWA","ULUBIONE","KOSZ"},sort==1?1:0,i->{
      if(i==2){favoritesPage();return;}if(i==3){trashPage();return;}
      sort=i;showTab(1);
    });
    gap(c,12);
    EditText find=new EditText(this);find.setSingleLine(true);find.setTextSize(15);find.setHint("Szukaj nagrania…");find.setTextColor(WHITE);
    find.setHintTextColor(MUTED);find.setBackground(back(CARD2,LINE,10));find.setPadding(dp(13),0,dp(13),0);
    find.setText(search);c.addView(find,new LinearLayout.LayoutParams(-1,dp(48)));
    find.setOnEditorActionListener((v,act,event)->{search=find.getText().toString();showTab(1);return true;});
    gap(c,11);
    block(c,"＋   IMPORTUJ NAGRANIE Z INNEJ APLIKACJI",false,()->{
      Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.setType("audio/*");
      picker.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(picker,211);
    });
    if(items.isEmpty()){micro(page,"Brak nagrań — rozpocznij pierwsze nagranie.");return;}
    for(File f:items){
      if(!search.trim().isEmpty()&&!f.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
      fileRow(page,f,false);
    }
  }
  private void fileRow(LinearLayout into,File f,boolean trashed){
    LinearLayout c=card(into);c.setPadding(dp(12),dp(13),dp(12),dp(13));
    LinearLayout line=horizontal();c.addView(line);
    TextView info=label((fav(f)?"★  ":"▶  ")+f.getName(),14,WHITE,true);info.setMaxLines(2);info.setEllipsize(android.text.TextUtils.TruncateAt.END);
    line.addView(info,new LinearLayout.LayoutParams(0,-2,1));info.setOnClickListener(v->{currentFile=f;showTab(2);play(f);});
    TextView sz=label(String.format(Locale.US,"%.1f MB",f.length()/1048576f),12,MUTED,false);
    line.addView(sz);hairline(c);
    LinearLayout controls=horizontal();c.addView(controls);
    chip(controls,"ODTWÓRZ",()->{currentFile=f;showTab(2);play(f);});
    chip(controls,trashed?"PRZYWRÓĆ":"★",()->{
      if(trashed) {File to=new File(dir(),f.getName());if(f.renameTo(to)){toast("Przywrócono");trashPage();}}
      else {prefs.edit().putBoolean("fav_"+f.getAbsolutePath(),!fav(f)).apply();showTab(1);}
    });
    chip(controls,trashed?"USUŃ":"WIĘCEJ",()->{
      if(trashed)confirm("Usunąć na stałe?",f.getName(),()->{f.delete();trashPage();});
      else fileMenu(f);
    });
  }
  private void chip(LinearLayout parent,String title,Runnable click){
    TextView b=label(title,11,WHITE,true);b.setGravity(Gravity.CENTER);
    b.setBackground(back(CARD2,LINE,8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1);p.rightMargin=dp(5);
    parent.addView(b,p);b.setOnClickListener(v->click.run());
  }
  private void fileMenu(File f){
    new AlertDialog.Builder(this).setTitle(f.getName())
      .setItems(new String[]{"Odtwórz","Zmień nazwę","Udostępnij","Przenieś do kosza"},(d,i)->{
        if(i==0){currentFile=f;showTab(2);play(f);}
        if(i==1)rename(f);
        if(i==2)share(f);
        if(i==3)confirm("Przenieść do kosza?",f.getName(),()->{
          if(f.renameTo(new File(trash(),f.getName()))){toast("Przeniesiono do kosza");showTab(1);}
          else toast("Nie można przenieść pliku.");
        });
      }).show();
  }
  private void confirm(String title,String desc,Runnable yes){
    new AlertDialog.Builder(this).setTitle(title).setMessage(desc)
      .setNegativeButton("ANULUJ",null).setPositiveButton("TAK",(d,w)->yes.run()).show();
  }
  private void rename(File f){
    EditText field=new EditText(this);field.setSingleLine(true);
    String old=f.getName(),extension=old.substring(old.lastIndexOf('.'));
    field.setText(old.substring(0,old.lastIndexOf('.')));
    new AlertDialog.Builder(this).setTitle("Zmień nazwę").setView(field)
      .setNegativeButton("ANULUJ",null).setPositiveButton("ZAPISZ",(d,w)->{
        String s=field.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]","_");
        if(s.isEmpty())return;
        if(f.renameTo(new File(dir(),s+extension)))showTab(1);
        else toast("Nie można zmienić nazwy.");
      }).show();
  }
  private void share(File f){
    try {
      Uri u=new Uri.Builder().scheme("content").authority(getPackageName()+".file").appendPath(f.getName()).build();
      Intent intent=new Intent(Intent.ACTION_SEND);intent.setType(f.getName().endsWith(".wav")?"audio/wav":"audio/mp4");
      intent.putExtra(Intent.EXTRA_STREAM,u);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setClipData(ClipData.newUri(getContentResolver(),f.getName(),u));
      startActivity(Intent.createChooser(intent,"Udostępnij nagranie"));
    }catch(Exception e){toast("Nie można udostępnić: "+e.getMessage());}
  }
  private void favoritesPage(){
    page.removeAllViews();h(page,"ULUBIONE");gap(page,12);scan();int n=0;
    for(File f:items)if(fav(f)){fileRow(page,f,false);n++;}
    if(n==0)micro(page,"Brak ulubionych nagrań.");
    block(page,"‹  MOJE NAGRANIA",false,()->showTab(1));
  }
  private void trashPage(){
    page.removeAllViews();h(page,"KOSZ");gap(page,12);
    File[] ff=trash().listFiles();if(ff==null||ff.length==0)micro(page,"Kosz jest pusty.");
    else for(File f:ff)if(f.isFile())fileRow(page,f,true);
    gap(page,10);block(page,"‹  MOJE NAGRANIA",false,()->showTab(1));
  }
  @Override protected void onActivityResult(int req,int res,Intent data){
    super.onActivityResult(req,res,data);
    if(req==211 && res==RESULT_OK && data!=null && data.getData()!=null){
      Uri uri=data.getData();String name="Import_"+System.currentTimeMillis()+".m4a";
      try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
        if(c!=null && c.moveToFirst()){
          int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)name=c.getString(n);
        }
      }catch(Exception ignored){}
      name=name.replaceAll("[\\\\/:*?\"<>|]","_");
      if(!name.toLowerCase(Locale.ROOT).endsWith(".m4a")&&!name.toLowerCase(Locale.ROOT).endsWith(".wav"))
        name+=".m4a";
      File dst=new File(dir(),name);
      try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(dst)){
        byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
        toast("Zaimportowano: "+dst.getName());showTab(1);
      }catch(Exception e){toast("Błąd importu: "+e.getMessage());dst.delete();}
    }
  }
  private void playerPage(){
    h(page,"ODTWARZACZ");gap(page,12);LinearLayout c=card(page);
    playerLabel=addLabel(c,currentFile==null?"Wybierz nagranie z listy.":currentFile.getName(),16,WHITE,true);
    gap(c,18);
    Meter waveform=new Meter(this);c.addView(waveform,new LinearLayout.LayoutParams(-1,dp(95)));
    progress=new SeekBar(this);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
    progress.setThumbTintList(android.content.res.ColorStateList.valueOf(RED));
    c.addView(progress);progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      @Override public void onProgressChanged(SeekBar b,int value,boolean user){if(user&&player!=null)player.seekTo(value);}
      @Override public void onStartTrackingTouch(SeekBar b){} @Override public void onStopTrackingTouch(SeekBar b){}
    });
    actionStrip(c,new String[]{"▶   ODTWÓRZ","Ⅱ   PAUZA","■   STOP"},-1,i->{
      if(i==0){if(player!=null)player.start();else if(currentFile!=null)play(currentFile);else showTab(1);}
      else if(i==1){if(player!=null && player.isPlaying())player.pause();}
      else stopPlayer();
    });
    gap(page,11);block(page,"PRZEJDŹ DO MOICH NAGRAŃ  ›",false,()->showTab(1));
  }
  private void play(File f){
    stopPlayer();currentFile=f;
    try{
      player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.prepare();player.start();
      player.setOnCompletionListener(mp->{stopPlayer();if(playerLabel!=null)playerLabel.setText("Zakończono odtwarzanie");});
      if(playerLabel!=null)playerLabel.setText("▶  "+f.getName());
    }catch(Exception e){stopPlayer();toast("Błąd odtwarzania: "+e.getMessage());}
  }
  private void stopPlayer(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
  private void settingsPage(){
    h(page,"USTAWIENIA NAGRYWANIA");gap(page,14);
    LinearLayout theme=card(page);title(theme,"WYGLĄD");micro(theme,"Motyw interfejsu");
    gap(theme,8);actionStrip(theme,new String[]{"AMOLED BLACK","DARK","LIGHT"},0,i->{
      if(i==0)toast("AMOLED BLACK");else toast("Ta wersja zachowuje motyw AMOLED Black oryginalnego Grabbera.");
    });
    hairline(theme);micro(theme,"Typografia");gap(theme,8);
    actionStrip(theme,new String[]{"LEKKA","STANDARD","DUŻA"},0,i->toast("Zachowany styl typografii AudioRelay Grabber."));
    LinearLayout audio=card(page);title(audio,"JAKOŚĆ I FORMAT");
    micro(audio,"Wybrane ustawienia");gap(audio,8);
    addLabel(audio,fmt()+"  ·  "+kbps()+" kb/s  ·  "+String.format(Locale.US,"%.1f",hz()/1000.0)+" kHz  ·  MONO",14,WHITE,true);
    gap(audio,15);block(audio,"ZMIENIAJ NA EKRANIE NAGRYWANIA",false,()->showTab(0));
    LinearLayout storage=card(page);title(storage,"ZAPIS NAGRAŃ");micro(storage,dir().getAbsolutePath());
    gap(storage,13);block(storage,"OTWÓRZ MOJE NAGRANIA",false,()->showTab(1));
    LinearLayout about=card(page);title(about,"O APLIKACJI");
    micro(about,"AudioRelay Grabber 1.9.3  •  n2s  •  Android 9 Pie");
    gap(about,8);micro(about,"Wersja przeniesiona dla Huawei P20 Lite, z pełnym interfejsem rejestratora. Nagrywanie wewnętrzne aplikacji nie jest obsługiwane przez Android 9.");
  }
  private void diagnosticPage(){
    h(page,"DIAGNOSTYKA NAGRAŃ");gap(page,14);
    LinearLayout c=card(page);title(c,"URZĄDZENIE");
    item(c,"MODEL",Build.MANUFACTURER+" "+Build.MODEL);
    item(c,"ANDROID",Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT);
    item(c,"WERSJA","AudioRelay Grabber 1.9.3");
    item(c,"UPRAWNIENIE MIKROFONU",checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"PRZYZNANE":"BRAK");
    item(c,"KATALOG NAGRAŃ",dir().getAbsolutePath());
    item(c,"WOLNE MIEJSCE",String.format(Locale.US,"%.2f GB",dir().getUsableSpace()/1073741824f));
    LinearLayout test=card(page);title(test,"TEST ZAPISU I ODCZYTU");
    micro(test,"Test sprawdza możliwość utworzenia oraz odczytu pliku WAV w katalogu nagrań.");
    gap(test,9);block(test,"WYKONAJ TEST PLIKU WAV",true,()->{
      File f=new File(dir(),"TEST_"+System.currentTimeMillis()+".wav");
      try(FileOutputStream out=new FileOutputStream(f)){
        byte[] wav=WaveHelper.header(8820,44100,1);
        out.write(wav);out.write(new byte[8820]);
        toast("TEST OK — zapisano "+f.getName());
      }catch(Exception e){toast("TEST NIEUDANY — "+e.getMessage());}
    });
    gap(page,9);
    LinearLayout notes=card(page);title(notes,"ZGODNOŚĆ Z ANDROIDEM 9");
    micro(notes,"Na Androidzie 9 dostępny jest zapis mikrofonu. Systemowe AudioPlaybackCapture (nagrywanie innych aplikacji) wymaga co najmniej Androida 10.");
    gap(notes,10);micro(notes,"EMUI 9.1: w Ustawieniach → Bateria → Uruchamianie aplikacji zezwól Grabberowi na działanie w tle.");
  }
  private void item(LinearLayout c,String left,String right){
    micro(c,left);gap(c,4);addLabel(c,right,14,WHITE,true);hairline(c);
  }
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override public void onBackPressed(){if(tab!=0)showTab(0);else super.onBackPressed();}
  @Override protected void onDestroy(){ui.removeCallbacks(tick);stopPlayer();super.onDestroy();}
  private static final class Ring extends View{
    private final Paint paint=new Paint(3);
    Ring(Activity a){super(a);}
    @Override protected void onDraw(Canvas c){
      float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.38f;
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,getWidth()*.037f));paint.setColor(WHITE);
      c.drawCircle(x,y,r,paint);
      paint.setStyle(Paint.Style.FILL);paint.setColor(RED);c.drawCircle(x-r,y,Math.max(4,getWidth()*.075f),paint);
    }
  }
  private static final class Meter extends View{
    private final Paint p=new Paint(3);Meter(Activity a){super(a);}
    @Override protected void onDraw(Canvas c){
      int n=49;float w=getWidth(),h=getHeight();float spacing=w/n;
      float v=RecordingService.running?Math.min(1f,RecordingService.reportedLevel()/20000f):0f;
      for(int i=0;i<n;i++){
        double wave=0.08+Math.abs(Math.sin(i*.31+System.currentTimeMillis()*.008))*(.15+v*.85);
        float size=(float)(wave*h*.9f);
        p.setStrokeWidth(Math.max(2,spacing*.42f));p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(i<n*(.15+v*.75)?RED:0xff464646);
        float xx=spacing*(i+.5f);c.drawLine(xx,h/2-size/2,xx,h/2+size/2,p);
      }
    }
  }
}
