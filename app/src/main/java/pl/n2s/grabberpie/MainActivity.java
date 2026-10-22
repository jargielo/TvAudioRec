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
import android.media.audiofx.Visualizer;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
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
  private static final int RED=0xffff244d;
  private int BLACK=0xff000000, CARD=0xff111111, CARD2=0xff191919,
    WHITE=0xfff4f4f4, MUTED=0xff9a9a9a, LINE=0xff343434;
  private boolean lightTheme=false;
  private void applyPalette(){
    String choice=prefs.getString("theme","dark");
    boolean systemNight=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
      ==Configuration.UI_MODE_NIGHT_YES;
    lightTheme="light".equals(choice) || ("system".equals(choice) && !systemNight);
    BLACK=lightTheme?0xfff8f8fa:0xff000000;
    CARD=lightTheme?0xffffffff:0xff111111;
    CARD2=lightTheme?0xffefeff1:0xff191919;
    WHITE=lightTheme?0xff141419:0xfff4f4f4;
    MUTED=lightTheme?0xff63636d:0xff9a9a9a;
    LINE=lightTheme?0xffd7d7dd:0xff343434;
    getWindow().setStatusBarColor(BLACK);
    getWindow().setNavigationBarColor(BLACK);
    int flags=getWindow().getDecorView().getSystemUiVisibility();
    if(lightTheme)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    else flags&=~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    getWindow().getDecorView().setSystemUiVisibility(flags);
  }
  private String themeName(){
    switch(prefs.getString("theme","dark")){
      case "system":return "Systemowy";
      case "light":return "Light";
      default:return "Dark";
    }
  }
  private int themedColor(int color){
    if(!lightTheme)return color;
    switch(color){
      case 0xff222124:return 0xfff3f3f5;
      case 0xff363337:return 0xffd6d6dc;
      case 0xff29282a:return 0xffededf0;
      case 0xff414042:return 0xffd5d5db;
      case 0xff41252c:case 0xff44212a:return 0xffffe5e9;
      case 0xff656565:return 0xffaaaaaf;
      case 0xff000000:return BLACK;
      case 0xff111111:return CARD;
      case 0xff191919:return CARD2;
      case 0xff343434:return LINE;
      default:return color;
    }
  }
  private void chooseTheme(){
    String[] names={"Systemowy","Dark","Light"};
    String[] values={"system","dark","light"};
    int current=Arrays.asList(values).indexOf(prefs.getString("theme","dark"));
    new AlertDialog.Builder(this).setTitle("Motyw PureCapture")
      .setSingleChoiceItems(names,current,(dialog,which)->{
        dialog.dismiss();
        prefs.edit().putString("theme",values[which]).apply();
        drawerOpen=false;
        applyPalette();
        buildShell();
        showTab(tab);
      }).setNegativeButton("Anuluj",null).show();
  }
  private static final String[] TABS={"Rejestrator dźwięku","Odtwarzacz","Moje nagrania","Źródło dźwięku","Szybkie ustawienia","Jakość nagrywania","Ustawienia","Diagnostyka"};
  private static final int REQUEST_CAPTURE=907;
  private final Handler ui=new Handler();
  private final Runnable tick=new Runnable(){ @Override public void run(){ updateNow();ui.postDelayed(this,90); } };
  private LinearLayout root, page, header, drawerPanel, homeTabs;
  private ScrollView bodyScroll;
  private FrameLayout outer, drawerOverlay;
  private boolean drawerOpen=false;
  private TextView state, time, fileCount, playerLabel;
  private Button recordToggle;
  private boolean recordingActionPending=false;
  private Meter meter;
  private SeekBar progress;
  private MediaPlayer player;
  private Visualizer visualizer;
  private volatile float playbackLevel=0f;
  private Meter playerMeter;
  private File currentFile;

  private final List<File> items=new ArrayList<>();
  private int tab=0;
  private int sort=0;
  private String search="";
  private int expandedConfig=-1;
  private SharedPreferences prefs;
  private boolean uiReady;
  private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
  private Typeface thin(){return Typeface.create("sans-serif-light",Typeface.NORMAL);}
  private Typeface regular(){return Typeface.create("sans-serif",Typeface.NORMAL);}
  private Typeface medium(){return Typeface.create("sans-serif-medium",Typeface.NORMAL);}
  private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private GradientDrawable back(int fill,int stroke,int radius){
    GradientDrawable g=new GradientDrawable();g.setColor(themedColor(fill));g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1,themedColor(stroke));return g;
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
    applyPalette();
    Intent launched=getIntent();
    if(launched!=null && launched.hasExtra("tab")) tab=launched.getIntExtra("tab",0);
    buildShell();
    showTab(tab);ui.post(tick);
  }
  private void buildShell(){
    outer=new FrameLayout(this);
    outer.setBackgroundColor(BLACK);
    setContentView(outer);
    root=vertical();root.setBackgroundColor(BLACK);
    outer.addView(root,new FrameLayout.LayoutParams(-1,-1));
    header=vertical();header.setPadding(dp(17),dp(15),dp(12),dp(12));
    root.addView(header,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout branding=horizontal();header.addView(branding);
    ImageView logo=new ImageView(this);
    logo.setImageResource(R.drawable.cyberpunk_foreground);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    branding.addView(logo,new LinearLayout.LayoutParams(dp(62),dp(62)));
    LinearLayout words=vertical();words.setPadding(dp(7),0,0,0);
    branding.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    TextView a=addLabel(words,"PureCapture",27,WHITE,false);
    a.setTypeface(thin());a.setIncludeFontPadding(false);
    TextView dots=label("⋮",34,WHITE,true);
    dots.setContentDescription("Otwórz menu");
    dots.setGravity(Gravity.CENTER);
    branding.addView(dots,new LinearLayout.LayoutParams(dp(49),dp(60)));
    dots.setOnClickListener(v->openDrawer());
    TextView rev=addLabel(header,"CYBERPUNK  •  "+themeName().toUpperCase(Locale.ROOT)+"  •  v2.0.2",10,MUTED,false);
    rev.setLetterSpacing(.06f);rev.setGravity(Gravity.END);
    gap(header,12);
    homeTabs=horizontal();
    header.addView(homeTabs,new LinearLayout.LayoutParams(-1,dp(51)));
    View divider=new View(this);divider.setBackgroundColor(LINE);
    root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    ScrollView body=new ScrollView(this);
    bodyScroll=body;
    body.setFillViewport(true);body.setVerticalScrollBarEnabled(false);
    page=vertical();page.setPadding(dp(17),dp(20),dp(17),dp(38));
    body.addView(page);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    buildDrawer();
    uiReady=true;
  }
  private void buildDrawer(){
    drawerOverlay=new FrameLayout(this);
    drawerOverlay.setVisibility(View.GONE);
    outer.addView(drawerOverlay,new FrameLayout.LayoutParams(-1,-1));
    View scrim=new View(this);scrim.setBackgroundColor(0xb8000000);
    scrim.setAlpha(0f);scrim.setOnClickListener(v->closeDrawer());
    drawerOverlay.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
    int width=Math.min(dp(330),(int)(getResources().getDisplayMetrics().widthPixels*.84f));
    drawerPanel=vertical();
    drawerPanel.setPadding(dp(19),dp(18),dp(19),dp(23));
    drawerPanel.setBackground(back(0xff222124,0xff363337,26));
    FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,-1,Gravity.START);
    lp.leftMargin=dp(3);lp.topMargin=dp(3);lp.bottomMargin=dp(3);
    drawerOverlay.addView(drawerPanel,lp);
    LinearLayout brand=horizontal();
    drawerPanel.addView(brand,new LinearLayout.LayoutParams(-1,dp(83)));
    ImageView logo=new ImageView(this);
    logo.setImageResource(R.drawable.cyberpunk_foreground);
    logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
    brand.addView(logo,new LinearLayout.LayoutParams(dp(73),dp(73)));
    LinearLayout texts=vertical();texts.setPadding(dp(5),0,0,0);
    brand.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
    addLabel(texts,"PureCapture",21,WHITE,true);
    addLabel(texts,"n2s",16,MUTED,false);
    gap(drawerPanel,27);
    String[] names={"Powrót do rejestratora","Moje nagrania","Konfiguracja nagrania",
      "Ustawienia","Diagnostyka","Motyw: "+themeName()};
    String[] glyphs={"●","▢","☷","⚙","⌁","◐"};
    int[] destinations={0,2,3,6,7,-1};
    for(int i=0;i<names.length;i++){
      if(i==1 || i==3 || i==5){gap(drawerPanel,7);hairline(drawerPanel);gap(drawerPanel,6);}
      final int idx=destinations[i];
      LinearLayout row=horizontal();row.setPadding(dp(13),0,dp(12),0);
      row.setBackground(back(0xff29282a,0,12));
      LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(64));
      rp.bottomMargin=dp(7);drawerPanel.addView(row,rp);
      if(i!=0){
        TextView ico=label(glyphs[i],25,WHITE,false);
        ico.setGravity(Gravity.CENTER);
        row.addView(ico,new LinearLayout.LayoutParams(dp(42),-1));
      }
      TextView name=label(names[i],17,WHITE,false);
      LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-1,1);
      np.leftMargin=dp(i==0?10:7);row.addView(name,np);
      if(i!=0){
        TextView arrow=label("›",27,MUTED,false);
        row.addView(arrow);
      }
      row.setOnClickListener(v->{if(idx==-1)chooseTheme();else {closeDrawer();showTab(idx);}});
    }
    View spacer=new View(this);
    drawerPanel.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
    TextView foot=label("PureCapture  •  n2s\nAndroid 9 i nowsze (API 28+)  •  v2.0.2",12,MUTED,false);
    foot.setGravity(Gravity.CENTER);
    foot.setBackground(back(0xff29282a,0xff414042,18));
    drawerPanel.addView(foot,new LinearLayout.LayoutParams(-1,dp(76)));
    drawerPanel.setTranslationX(-width-dp(15));
  }
  private void openDrawer(){
    if(drawerOpen)return;
    drawerOpen=true;drawerOverlay.setVisibility(View.VISIBLE);
    View scrim=drawerOverlay.getChildAt(0);
    scrim.animate().alpha(1f).setDuration(180).start();
    drawerPanel.animate().translationX(0).setDuration(220).start();
  }
  private void closeDrawer(){
    if(!drawerOpen)return;
    drawerOpen=false;
    View scrim=drawerOverlay.getChildAt(0);
    scrim.animate().alpha(0f).setDuration(180).start();
    int width=drawerPanel.getLayoutParams().width;
    drawerPanel.animate().translationX(-width-dp(15)).setDuration(200)
      .withEndAction(()->{if(!drawerOpen)drawerOverlay.setVisibility(View.GONE);}).start();
  }
  private void showTab(int index){
    if(index<0 || index>=TABS.length)index=0;
    tab=index;
    homeTabs.removeAllViews();
    homeTabs.setVisibility(index<2?View.VISIBLE:View.GONE);
    if(index<2){
      for(int i=0;i<2;i++){
        final int target=i;
        TextView choice=label(TABS[i],14,i==index?0xff000000:WHITE,true);
        choice.setGravity(Gravity.CENTER);
        choice.setBackground(back(i==index?RED:CARD2,i==index?RED:LINE,13));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);
        if(i==0)lp.rightMargin=dp(8);
        homeTabs.addView(choice,lp);
        choice.setOnClickListener(v->showTab(target));
      }
    }
    page.removeAllViews();
    state=null;time=null;meter=null;playerMeter=null;fileCount=null;playerLabel=null;progress=null;recordToggle=null;
    switch(index){
      case 0: recorderPage();break;
      case 1: playerPage();break;
      case 2: filesPage();break;
      case 3: configPage();break;
      case 4: configPage();break;
      case 5: configPage();break;
      case 6: settingsPage();break;
      default: diagnosticPage();break;
    }
    updateNow();
  }
  private File dir(){File d=getExternalFilesDir(Environment.DIRECTORY_MUSIC);if(d==null)d=new File(getFilesDir(),"Music");if(!d.exists())d.mkdirs();return d;}
  private File trash(){File f=new File(dir(),".kosz");if(!f.exists())f.mkdirs();return f;}
  private boolean fav(File f){return prefs.getBoolean("fav_"+f.getAbsolutePath(),false);}
  private String fmt(){return prefs.getString("format","M4A");}
  private int kbps(){return prefs.getInt("rate",128);}
  private int hz(){return prefs.getInt("hz",44100);}
  private int source(){return prefs.getInt("source",0);}
  private String sourceName(){
    switch(source()){
      case 1:return "Dźwięk wewnętrzny";
      case 2:return "Mikrofon urządzenia + dźwięk wewnętrzny";
      case 3:return "Mikrofon Bluetooth + dźwięk wewnętrzny";
      default:return "Mikrofon urządzenia";
    }
  }
  private void recorderPage(){
    h(page,"REJESTRATOR DŹWIĘKU");gap(page,12);
    LinearLayout c=card(page);
    state=addLabel(c,"GOTOWY DO NAGRYWANIA",19,WHITE,true);
    gap(c,12);
    time=addLabel(c,"00:00:00",40,WHITE,false);
    time.setTypeface(Typeface.create("sans-serif-light",0));
    time.setGravity(Gravity.CENTER);
    meter=new Meter(this,false);c.addView(meter,new LinearLayout.LayoutParams(-1,dp(92)));
    gap(c,11);
    micro(c,"WIZUALIZACJA SYGNAŁU");gap(c,14);
    recordToggle=block(c,"●   ROZPOCZNIJ NAGRYWANIE",true,()->{
      if(recordingActionPending)return;
      if(RecordingService.running){
        recordingActionPending=true;
        stop();
      } else {
        recordingActionPending=true;
        start();
      }
    });
    gap(page,6);
    micro(page,"Źródło: "+sourceName()+"     •     "+fmt()+"     •     "+kbps()+" kb/s");
  }

  private int channels(){return prefs.getInt("channels",1)==2?2:1;}
  private LinearLayout configCard(int index,String heading,String selected){
    LinearLayout section=card(page);
    TextView title=label(heading+"   "+(expandedConfig==index?"⌃":"⌄"),16,WHITE,true);
    title.setPadding(dp(3),dp(5),dp(3),dp(5));
    section.addView(title,new LinearLayout.LayoutParams(-1,dp(39)));
    TextView summary=label(selected,13,MUTED,false);
    summary.setPadding(dp(3),0,dp(3),dp(7));
    section.addView(summary,new LinearLayout.LayoutParams(-1,-2));
    View.OnClickListener expand=v->{
      int y=bodyScroll.getScrollY();
      expandedConfig=expandedConfig==index?-1:index;
      showTab(3);
      bodyScroll.post(()->bodyScroll.scrollTo(0,y));
    };
    title.setOnClickListener(expand);summary.setOnClickListener(expand);
    return section;
  }
  private void configOption(LinearLayout section,String label,boolean checked,Runnable callback){
    TextView option=label((checked?"●   ":"○   ")+label,14,WHITE,checked);
    option.setGravity(Gravity.CENTER_VERTICAL);
    option.setPadding(dp(13),0,dp(10),0);
    option.setBackground(back(checked?0xff41252c:CARD2,checked?RED:LINE,9));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));
    lp.topMargin=dp(6);
    section.addView(option,lp);
    option.setOnClickListener(v->{
      int y=bodyScroll.getScrollY();
      callback.run();
      expandedConfig=-1;
      showTab(3);
      bodyScroll.post(()->bodyScroll.scrollTo(0,y));
    });
  }
  private String saveFolderLabel(){
    String value=prefs.getString("save_tree_uri","");
    if(value.isEmpty())return "/storage/emulated/0/Music/PureCapture";
    try{
      String id=DocumentsContract.getTreeDocumentId(Uri.parse(value));
      return Uri.decode(id);
    }catch(Exception e){return "Wybrany folder zewnętrzny";}
  }
  private void chooseSaveFolder(){
    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
      |Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(picker,208);
  }
  private void configPage(){
    h(page,"KONFIGURACJA NAGRANIA");gap(page,11);
    micro(page,"Dotknij opcji, aby rozwinąć jej ustawienia poniżej.");
    gap(page,16);
    LinearLayout sourceSection=configCard(0,"Źródło dźwięku",sourceName());
    if(expandedConfig==0){
      String[] names={"Mikrofon urządzenia","Dźwięk wewnętrzny",
        "Mikrofon urządzenia + dźwięk wewnętrzny",
        "Mikrofon Bluetooth + dźwięk wewnętrzny"};
      for(int i=0;i<names.length;i++){
        final int chosen=i;
        configOption(sourceSection,names[i],source()==i,()->{
          if(chosen>0 && Build.VERSION.SDK_INT<29){
            new AlertDialog.Builder(this).setTitle("Android 9 Pie")
              .setMessage("Dźwięk wewnętrzny wymaga Androida 10 lub nowszego. Na tym urządzeniu nagrywanie mikrofonu pozostaje dostępne.")
              .setPositiveButton("OK",null).show();
            return;
          }
          prefs.edit().putInt("source",chosen).apply();
        });
      }
    }
    LinearLayout formats=configCard(1,"Format zapisu",fmt());
    if(expandedConfig==1){
      for(String f:new String[]{"M4A","WAV","FLAC","MP3","AAC","3GP","OGG"}){
        configOption(formats,f,fmt().equals(f),()->prefs.edit().putString("format",f).apply());
      }
    }
    LinearLayout frequency=configCard(2,"Częstotliwość",hz()+" Hz");
    if(expandedConfig==2){
      int[] freqs={16000,32000,44100,48000};
      String[] labels={"16 kHz","32 kHz","44,1 kHz","48 kHz"};
      for(int i=0;i<freqs.length;i++){
        final int selected=freqs[i];
        configOption(frequency,labels[i],hz()==selected,()->prefs.edit().putInt("hz",selected).apply());
      }
    }
    LinearLayout quality=configCard(3,"Jakość nagrania",kbps()+" kb/s");
    if(expandedConfig==3){
      for(int rate:new int[]{96,128,192,256,320}){
        configOption(quality,rate+" kb/s",kbps()==rate,()->prefs.edit().putInt("rate",rate).apply());
      }
      gap(quality,9);micro(quality,"Przepływność dotyczy formatów stratnych. FLAC i WAV są bezstratne.");
    }
    LinearLayout channel=configCard(4,"Kanały",channels()==2?"Stereo (2 kanały)":"Mono (1 kanał)");
    if(expandedConfig==4){
      configOption(channel,"Mono (1 kanał)",channels()==1,()->prefs.edit().putInt("channels",1).apply());
      configOption(channel,"Stereo (2 kanały)",channels()==2,()->prefs.edit().putInt("channels",2).apply());
      gap(channel,8);
      micro(channel,"Dwa kanały są zapisywane, gdy pozwala na to wejście. Mikrofon Bluetooth SCO zazwyczaj zapewnia dźwięk mono.");
    }
    LinearLayout location=configCard(5,"Miejsce zapisu plików",saveFolderLabel());
    if(expandedConfig==5){
      micro(location,"Domyślny folder: /storage/emulated/0/Music/PureCapture. Możesz wskazać inny folder; nagrania pozostają także dostępne w bibliotece aplikacji.");
      gap(location,11);
      block(location,"WYBIERZ FOLDER ZAPISU",true,this::chooseSaveFolder);
      block(location,"PRZYWRÓĆ DOMYŚLNY FOLDER",false,()->{
        prefs.edit().remove("save_tree_uri").apply();
        expandedConfig=-1;
        showTab(3);
      });
      gap(location,9);
      micro(location,"Na Androidzie 11+ system może ograniczać wybór katalogów głównych i folderów Android/data. W razie błędu zapisu plik zostanie zachowany w pamięci aplikacji.");
    }
    gap(page,10);block(page,"‹   POWRÓT DO REJESTRATORA",false,()->showTab(0));
  }
  private void start(){
    if(RecordingService.running || RecordingService.processing){recordingActionPending=false;toast("Nagranie jest w toku lub trwa zapis pliku");return;}
    if(Build.VERSION.SDK_INT<=28 &&
       prefs.getString("save_tree_uri","").isEmpty() &&
       checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
      recordingActionPending=false;
      requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},44);
      return;
    }
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      recordingActionPending=false;
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42);return;
    }
    if(source()>0){
      if(Build.VERSION.SDK_INT<29){
        new AlertDialog.Builder(this).setTitle("Android 9 Pie")
          .setMessage("Nagrywanie dźwięku wewnętrznego wymaga Androida 10 lub nowszego.")
          .setPositiveButton("OK",null).show();
        recordingActionPending=false;
        return;
      }
      if(source()==3 && Build.VERSION.SDK_INT>=31 &&
         checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED){
        recordingActionPending=false;
        requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},43);
        return;
      }
      android.media.projection.MediaProjectionManager mgr=
        (android.media.projection.MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      startActivityForResult(mgr.createScreenCaptureIntent(),REQUEST_CAPTURE);
      return;
    }
    startMic();
  }
  private void startMic(){
    Intent s=new Intent(this,RecordingService.class).setAction(RecordingService.START);
    s.putExtra("source","microphone");
    s.putExtra("format",fmt());s.putExtra("rate",kbps());s.putExtra("hz",hz());s.putExtra("channels",channels());
    startForegroundService(s);ui.postDelayed(this::updateNow,450);
  }
  @Override protected void onActivityResult(int req,int result,Intent data){
    super.onActivityResult(req,result,data);
    if(req==REQUEST_CAPTURE){
      if(result!=RESULT_OK||data==null){
        recordingActionPending=false;
        toast("Nie udzielono zgody na nagrywanie wewnętrzne.");return;
      }
      Intent s=new Intent(this,RecordingService.class).setAction(RecordingService.START);
      s.putExtra("source",source()==3?"btmix":source()==2?"mix":"internal");s.putExtra("projectionCode",result);
      s.putExtra("projectionData",data);
      s.putExtra("format",fmt());s.putExtra("rate",kbps());s.putExtra("hz",hz());s.putExtra("channels",channels());
      startForegroundService(s);
      ui.postDelayed(this::updateNow,450);
    }
    if(req==211 && result==RESULT_OK && data!=null && data.getData()!=null)importAudio(data.getData());
    if(req==208 && result==RESULT_OK && data!=null && data.getData()!=null){
      Uri chosen=data.getData();
      try{
        int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(chosen,flags);
        prefs.edit().putString("save_tree_uri",chosen.toString()).apply();
        expandedConfig=5;
        showTab(3);
        toast("Zmieniono miejsce zapisu nagrań.");
      }catch(Exception error){
        toast("Nie udało się zapisać dostępu do folderu: "+error.getMessage());
      }
    }
  }
  private void stop(){
    startService(new Intent(this,RecordingService.class).setAction(RecordingService.STOP));
    if(recordToggle!=null)recordToggle.setText("■   ZAPISYWANIE...");
    ui.postDelayed(()->{
      updateNow();
      if(recordingActionPending && !RecordingService.running){
        recordingActionPending=false;
        updateNow();
      }
    },650);
  }
  @Override public void onRequestPermissionsResult(int r,String[] p,int[] x){
    super.onRequestPermissionsResult(r,p,x);
    if(r==42){if(x.length>0&&x[0]==PackageManager.PERMISSION_GRANTED)start();
      else toast("Bez pozwolenia na mikrofon nagrywanie nie jest możliwe");}
    if(r==43){
      if(x.length>0&&x[0]==PackageManager.PERMISSION_GRANTED)start();
      else toast("Nagrywanie przez Bluetooth wymaga uprawnienia do urządzeń Bluetooth");
    }
    if(r==44){
      if(x.length>0&&x[0]==PackageManager.PERMISSION_GRANTED)start();
      else toast("Aby zapisywać w Music/PureCapture na Androidzie 9, nadaj uprawnienie pamięci.");
    }
  }
  private void updateNow(){
    if(!uiReady)return;
    if(state!=null) {
      state.setText(RecordingService.processing?"ZAPISYWANIE PLIKU...":
        RecordingService.running?"●  TRWA NAGRYWANIE":"GOTOWY DO NAGRYWANIA");
      state.setTextColor(RecordingService.running?RED:WHITE);
      if(!RecordingService.running && RecordingService.lastError!=null && !RecordingService.lastError.isEmpty())
        state.setText("BŁĄD: "+RecordingService.lastError);
    }
    if(time!=null){long sec=RecordingService.running?(System.currentTimeMillis()-RecordingService.startedAt)/1000:0;
      time.setText(String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60));}
    if(recordToggle!=null){
      boolean recording=RecordingService.running;
      if(recording)recordingActionPending=false;
      if(!recording && !recordingActionPending){
        recordToggle.setText("●   ROZPOCZNIJ NAGRYWANIE");
      }else if(recording){
        recordToggle.setText("■   STOP I ZAPISZ");
      }else{
        recordToggle.setText("●   URUCHAMIANIE...");
      }
      recordToggle.setEnabled(!RecordingService.processing && (recording || !recordingActionPending));
      if(RecordingService.processing)recordToggle.setText("ZAPISYWANIE...");
      recordToggle.setAlpha(recordToggle.isEnabled()?1f:0.65f);
    }
    if(meter!=null)meter.invalidate();
    if(playerMeter!=null)playerMeter.invalidate();
    if(player!=null && progress!=null){
      try{progress.setMax(player.getDuration());progress.setProgress(player.getCurrentPosition());}catch(Exception ignored){}
    }
  }
  private void scan(){
    items.clear();File[] f=dir().listFiles();if(f!=null)for(File v:f){
      if(v.isFile()&&!v.getName().endsWith(".partial.wav")&&(
        v.getName().endsWith(".m4a")||v.getName().endsWith(".wav")||
        v.getName().endsWith(".mp3")||v.getName().endsWith(".flac")||
        v.getName().endsWith(".aac")||v.getName().endsWith(".3gp")||
        v.getName().endsWith(".ogg")))items.add(v);
    }
    Collections.sort(items,(a,b)->sort==1?a.getName().compareToIgnoreCase(b.getName()):Long.compare(b.lastModified(),a.lastModified()));
  }
  private void filesPage(){
    scan();
    h(page,"MOJE NAGRANIA");gap(page,10);
    block(page,"‹   POWRÓT DO REJESTRATORA",false,()->showTab(0));
    gap(page,12);
    LinearLayout c=card(page);
    fileCount=addLabel(c,items.size()+" plików  •  PureCapture",15,WHITE,true);
    gap(c,14);
    LinearLayout filters=horizontal();
    c.addView(filters,new LinearLayout.LayoutParams(-1,dp(43)));
    String[] filtersNames={"Najnowsze","Nazwa","Ulubione","Kosz"};
    for(int i=0;i<filtersNames.length;i++){
      final int chosen=i;
      TextView b=label(filtersNames[i],11,WHITE,i==(sort==1?1:0));
      b.setSingleLine(true);
      b.setGravity(Gravity.CENTER);
      b.setPadding(dp(1),0,dp(1),0);
      b.setBackground(back(i==(sort==1?1:0)?0xff44212a:CARD2,LINE,8));
      LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(42),1);
      if(i>0)fp.leftMargin=dp(4);
      filters.addView(b,fp);
      b.setOnClickListener(v->{
        if(chosen==2){favoritesPage();return;}
        if(chosen==3){trashPage();return;}
        sort=chosen;showTab(2);
      });
    }
    gap(c,12);
    EditText find=new EditText(this);find.setSingleLine(true);find.setTextSize(15);find.setHint("Szukaj nagrania…");find.setTextColor(WHITE);
    find.setHintTextColor(MUTED);find.setBackground(back(CARD2,LINE,10));find.setPadding(dp(13),0,dp(13),0);
    find.setText(search);c.addView(find,new LinearLayout.LayoutParams(-1,dp(48)));
    find.setOnEditorActionListener((v,act,event)->{search=find.getText().toString();showTab(2);return true;});
    gap(c,11);
    block(c,"＋   IMPORTUJ NAGRANIE Z INNEJ APLIKACJI",false,()->{
      Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);picker.setType("audio/*");
      picker.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(picker,211);
    });
    if(items.isEmpty()){micro(page,RecordingService.processing?"Trwa kodowanie i zapis pliku...":"Brak nagrań — rozpocznij pierwsze nagranie.");return;}
    for(File f:items){
      if(!search.trim().isEmpty()&&!f.getName().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))continue;
      fileRow(page,f,false);
    }
  }
  private void fileRow(LinearLayout into,File f,boolean trashed){
    LinearLayout item=card(into);
    item.setPadding(dp(12),dp(9),dp(12),dp(9));
    LinearLayout row=horizontal();item.addView(row,new LinearLayout.LayoutParams(-1,dp(50)));
    TextView filename=label("♪   "+f.getName(),14,WHITE,true);
    filename.setSingleLine(true);
    filename.setEllipsize(android.text.TextUtils.TruncateAt.END);
    row.addView(filename,new LinearLayout.LayoutParams(0,-1,1));
    TextView more=label(String.format(Locale.US,"%.1f MB  ›",f.length()/1048576f),12,MUTED,false);
    row.addView(more);
    item.setOnClickListener(v->{
      if(trashed){
        new AlertDialog.Builder(this).setTitle("Nagranie w koszu")
          .setItems(new String[]{"Przywróć","Usuń na stałe"},(d,which)->{
            if(which==0 && f.renameTo(new File(dir(),f.getName())))trashPage();
            if(which==1)confirm("Usunąć trwale?",f.getName(),()->{f.delete();trashPage();});
          }).show();
      }else{
        currentFile=f;showTab(1);play(f);
      }
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
        if(i==0){currentFile=f;showTab(1);play(f);}
        if(i==1)rename(f);
        if(i==2)share(f);
        if(i==3)confirm("Przenieść do kosza?",f.getName(),()->{
          if(f.renameTo(new File(trash(),f.getName()))){toast("Przeniesiono do kosza");showTab(2);}
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
        if(f.renameTo(new File(dir(),s+extension)))showTab(2);
        else toast("Nie można zmienić nazwy.");
      }).show();
  }
  private void share(File f){
    try {
      Uri u=new Uri.Builder().scheme("content").authority(getPackageName()+".file").appendPath(f.getName()).build();
      Intent intent=new Intent(Intent.ACTION_SEND);intent.setType(getContentResolver().getType(u));
      intent.putExtra(Intent.EXTRA_STREAM,u);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setClipData(ClipData.newUri(getContentResolver(),f.getName(),u));
      startActivity(Intent.createChooser(intent,"Udostępnij nagranie"));
    }catch(Exception e){toast("Nie można udostępnić: "+e.getMessage());}
  }
  private void favoritesPage(){
    page.removeAllViews();h(page,"ULUBIONE");gap(page,8);
    block(page,"‹   POWRÓT DO REJESTRATORA",false,()->showTab(0));
    gap(page,10);scan();int n=0;
    for(File f:items)if(fav(f)){fileRow(page,f,false);n++;}
    if(n==0)micro(page,"Brak ulubionych nagrań.");
    block(page,"‹  MOJE NAGRANIA",false,()->showTab(2));
  }
  private void trashPage(){
    page.removeAllViews();h(page,"KOSZ");gap(page,8);
    block(page,"‹   POWRÓT DO REJESTRATORA",false,()->showTab(0));
    gap(page,10);
    File[] ff=trash().listFiles();if(ff==null||ff.length==0)micro(page,"Kosz jest pusty.");
    else for(File f:ff)if(f.isFile())fileRow(page,f,true);
    gap(page,10);block(page,"‹  MOJE NAGRANIA",false,()->showTab(2));
  }
  private void importAudio(Uri uri){String name="Import_"+System.currentTimeMillis()+".m4a";
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
        toast("Zaimportowano: "+dst.getName());showTab(2);
      }catch(Exception e){toast("Błąd importu: "+e.getMessage());dst.delete();}
  }
  private void playerPage(){
    LinearLayout c=card(page);
    playerLabel=addLabel(c,currentFile==null?"Nie wybrano nagrania":currentFile.getName(),16,WHITE,true);
    playerLabel.setSingleLine(false);
    playerLabel.setMaxLines(2);
    gap(c,14);
    playerMeter=new Meter(this,true);
    c.addView(playerMeter,new LinearLayout.LayoutParams(-1,dp(104)));
    gap(c,18);
    LinearLayout controls=horizontal();
    c.addView(controls,new LinearLayout.LayoutParams(-1,dp(53)));
    final String[] labels={"ODTWÓRZ","PAUZA","STOP","WIĘCEJ"};
    for(int i=0;i<labels.length;i++){
      final int action=i;
      TextView btn=label(labels[i],11,WHITE,true);
      btn.setGravity(Gravity.CENTER);
      btn.setSingleLine(true);
      btn.setPadding(dp(1),0,dp(1),0);
      btn.setBackground(back(CARD2,LINE,10));
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);
      if(i>0)p.leftMargin=dp(5);
      controls.addView(btn,p);
      btn.setOnClickListener(v->{
        if(action==0){
          if(player!=null){player.start();enableVisualizer();}
          else if(currentFile!=null)play(currentFile);
          else showTab(2);
        }else if(action==1){
          if(player!=null && player.isPlaying())player.pause();
        }else if(action==2){
          stopPlayer();
          if(playerMeter!=null)playerMeter.invalidate();
        }else{
          playerMore();
        }
      });
    }
  }
  private void playerMore(){
    if(currentFile==null || !currentFile.exists()){
      new AlertDialog.Builder(this).setTitle("Odtwarzacz")
        .setItems(new String[]{"Wybierz nagranie"},(d,which)->showTab(2)).show();
      return;
    }
    File f=currentFile;
    String favorite=fav(f)?"Usuń z ulubionych":"Dodaj do ulubionych";
    new AlertDialog.Builder(this).setTitle(f.getName())
      .setItems(new String[]{"Moje nagrania",favorite,"Zmień nazwę",
        "Udostępnij","Przenieś do kosza"},(d,which)->{
        switch(which){
          case 0:showTab(2);break;
          case 1:
            prefs.edit().putBoolean("fav_"+f.getAbsolutePath(),!fav(f)).apply();
            break;
          case 2:
            stopPlayer();
            rename(f);
            break;
          case 3:share(f);break;
          case 4:
            confirm("Przenieść do kosza?",f.getName(),()->{
              stopPlayer();
              if(f.renameTo(new File(trash(),f.getName()))){
                currentFile=null;
                showTab(1);
              }else toast("Nie można przenieść pliku.");
            });
            break;
        }
      }).show();
  }
  private void play(File f){
    stopPlayer();
    currentFile=f;
    try{
      player=new MediaPlayer();
      player.setDataSource(f.getAbsolutePath());
      player.prepare();
      enableVisualizer();
      player.setOnCompletionListener(mp->{
        stopPlayer();
        if(playerLabel!=null)playerLabel.setText(f.getName());
      });
      if(playerLabel!=null)playerLabel.setText(f.getName());
      player.start();
    }catch(Exception e){
      stopPlayer();
      toast("Błąd odtwarzania: "+e.getMessage());
    }
  }
  private void enableVisualizer(){
    if(player==null)return;
    if(visualizer!=null){try{visualizer.release();}catch(Exception ignored){}visualizer=null;}
    try{
      visualizer=new Visualizer(player.getAudioSessionId());
      int[] range=Visualizer.getCaptureSizeRange();
      visualizer.setCaptureSize(Math.min(1024,range[1]));
      visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
        @Override public void onWaveFormDataCapture(Visualizer v,byte[] waveform,int samplingRate){
          if(waveform==null || waveform.length==0)return;
          double squares=0d;
          int stride=Math.max(1,waveform.length/256);
          int count=0;
          for(int i=0;i<waveform.length;i+=stride){
            int value=(waveform[i]&0xff)-128;
            squares+=value*value;
            count++;
          }
          double rms=Math.sqrt(squares/Math.max(1,count));
          playbackLevel=(float)Math.min(32767,rms*256.0);
        }
        @Override public void onFftDataCapture(Visualizer v,byte[] fft,int samplingRate){}
      },Math.min(Visualizer.getMaxCaptureRate(),12000),true,false);
      visualizer.setEnabled(true);
    }catch(Exception problem){
      if(visualizer!=null){try{visualizer.release();}catch(Exception ignored){}visualizer=null;}
      playbackLevel=0f;
    }
  }
  private void stopPlayer(){
    playbackLevel=0f;
    if(visualizer!=null){
      try{visualizer.setEnabled(false);}catch(Exception ignored){}
      try{visualizer.release();}catch(Exception ignored){}
      visualizer=null;
    }
    if(player!=null){
      try{player.stop();}catch(Exception ignored){}
      player.release();
      player=null;
    }
  }
  private void settingsPage(){
    h(page,"USTAWIENIA");gap(page,14);
    LinearLayout theme=card(page);title(theme,"WYGLĄD");
    micro(theme,"Motyw: "+themeName());
    gap(theme,12);block(theme,"ZMIEŃ MOTYW",false,this::chooseTheme);
    LinearLayout storage=card(page);title(storage,"NAGRANIA");
    micro(storage,dir().getAbsolutePath());
    gap(storage,13);block(storage,"OTWÓRZ MOJE NAGRANIA",false,()->showTab(2));
    LinearLayout about=card(page);title(about,"O APLIKACJI");
    micro(about,"PureCapture 2.0.2  •  n2s");
    micro(about,"Android: "+Build.VERSION.RELEASE+". Obsługa przechwytywania wewnętrznego od Androida 10.");
  }
  private void diagnosticPage(){
    h(page,"DIAGNOSTYKA NAGRAŃ");gap(page,14);
    LinearLayout c=card(page);title(c,"URZĄDZENIE");
    item(c,"MODEL",Build.MANUFACTURER+" "+Build.MODEL);
    item(c,"ANDROID",Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT);
    item(c,"WERSJA","PureCapture 2.0.2");
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
    LinearLayout notes=card(page);title(notes,"ZGODNOŚĆ SYSTEMU");
    micro(notes,Build.VERSION.SDK_INT>=29?"Przechwytywanie wewnętrzne dostępne po zgodzie systemowej; źródło musi zezwalać na przechwytywanie.":"Android 9: działa mikrofon. Przechwytywanie wewnętrzne wymaga Androida 10 lub nowszego.");
    gap(notes,10);micro(notes,"EMUI 9.1: w Ustawieniach → Bateria → Uruchamianie aplikacji zezwól Grabberowi na działanie w tle.");
  }
  private void item(LinearLayout c,String left,String right){
    micro(c,left);gap(c,4);addLabel(c,right,14,WHITE,true);hairline(c);
  }
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
  @Override public void onBackPressed(){if(drawerOpen)closeDrawer();else if(tab!=0)showTab(0);else super.onBackPressed();}
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
    private final MainActivity owner;
    private final boolean playback;
    private final Paint paint=new Paint(3);
    private float displayed=0f;
    Meter(MainActivity owner,boolean playback){
      super(owner);
      this.owner=owner;
      this.playback=playback;
    }
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);
      float width=getWidth();
      float height=getHeight();
      float mid=height/2f;
      boolean active=playback
        ?owner.player!=null && owner.player.isPlaying()
        :RecordingService.running;
      float raw=playback?owner.playbackLevel:RecordingService.reportedLevel();
      // Noise gate: no fake animation when the source is silent.
      float threshold=playback?350f:90f;
      float value=!active || raw<=threshold?0f:
        Math.min(1f,(raw-threshold)/(playback?13000f:5000f));
      if(value==0f)displayed=0f;
      else displayed=0.32f*displayed+0.68f*value;
      paint.setColor(owner.lightTheme?0xffa1a1ab:0xff656565);
      paint.setStrokeWidth(Math.max(2f,getResources().getDisplayMetrics().density*1.9f));
      paint.setStrokeCap(Paint.Cap.ROUND);
      canvas.drawLine(0,mid,width,mid,paint);
      if(displayed<=0f)return;
      float gap=width/49f;
      float amplitude=height*0.47f*displayed;
      paint.setColor(RED);
      paint.setStrokeWidth(Math.max(2.4f,gap*0.38f));
      float t=System.currentTimeMillis()/120f;
      for(int i=0;i<49;i++){
        float envelope=(float)(0.24+0.76*Math.sin(Math.PI*(i+1)/50f));
        float shape=(float)(0.18+0.82*Math.abs(Math.sin(i*0.53f+t)));
        float half=Math.max(2f,amplitude*envelope*shape);
        float x=gap*(i+0.5f);
        canvas.drawLine(x,mid-half,x,mid+half,paint);
      }
    }
  }
}
