package pl.n2s.grabberpie;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

public final class MainActivity extends Activity {
  private static final int RED=0xffff244d, WHITE=0xffeeeeee, GREY=0xffaaaaaa;
  private TextView status;
  private LinearLayout files;
  private Button start, stop;
  private MediaPlayer player;
  private File playing;
  private final Handler handler=new Handler();
  private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  private TextView text(LinearLayout parent,String value,int sp,int color){
    TextView t=new TextView(this); t.setText(value);t.setTextSize(sp);t.setTextColor(color);
    parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
  }
  private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
  private Button button(LinearLayout p,String s,int background){
    Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(15);
    b.setAllCaps(false);
    GradientDrawable d=new GradientDrawable();d.setColor(background);d.setCornerRadius(dp(12));
    d.setStroke(dp(1),background==RED?RED:0xff666666);b.setBackground(d);
    p.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));return b;
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.BLACK);
    LinearLayout main=new LinearLayout(this);main.setOrientation(1);main.setPadding(dp(22),dp(22),dp(22),dp(24));
    scroll.addView(main);setContentView(scroll);
    Ring icon=new Ring(this);
    LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(95),dp(95));ip.gravity=Gravity.CENTER;ip.bottomMargin=dp(16);main.addView(icon,ip);
    TextView brand=text(main,"AUDIORELAY",16,GREY);brand.setGravity(Gravity.CENTER);
    TextView title=text(main,"GRABBER",32,WHITE);title.setGravity(Gravity.CENTER);title.setLetterSpacing(.15f);
    TextView version=text(main,"ANDROID 9 PIE  •  1.9.2",12,RED);version.setGravity(Gravity.CENTER);
    space(main,24);
    status=text(main,"Gotowy do nagrywania",19,WHITE);
    space(main,10);
    TextView about=text(main,"W Androidzie 9 możliwe jest nagrywanie mikrofonu. Dźwięk wewnętrzny innych aplikacji wymaga Androida 10 lub rozwiązań systemowych.",14,GREY);
    about.setLineSpacing(dp(3),1f);
    space(main,22);
    start=button(main,"●   NAGRYWAJ Z MIKROFONU",RED);start.setOnClickListener(v->record());
    space(main,11);
    stop=button(main,"■   ZATRZYMAJ",0xff222222);stop.setOnClickListener(v->{
       startService(new Intent(this,RecordingService.class).setAction(RecordingService.STOP));
       handler.postDelayed(this::refresh,450);
    });
    space(main,26);
    text(main,"NAGRANIA  •  DOTKNIJ, BY ODTWORZYĆ",16,WHITE);
    text(main,"Przytrzymaj pozycję, aby usunąć plik.",12,GREY);
    space(main,12);
    files=new LinearLayout(this);files.setOrientation(1);main.addView(files);
    space(main,22);
    TextView directory=text(main,"M4A / AAC · 44,1 kHz · 128 kb/s\n"+folder().getAbsolutePath(),12,GREY);
    directory.setTextIsSelectable(true);
    refresh();
  }
  private File folder(){
    File d=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
    if(d==null)d=new File(getFilesDir(),"Music");
    if(!d.exists())d.mkdirs();return d;
  }
  private void record(){
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},22);return;
    }
    startForegroundService(new Intent(this,RecordingService.class).setAction(RecordingService.START));
    status.setText("Uruchamianie nagrywania…");
    handler.postDelayed(this::refresh,550);
  }
  @Override public void onRequestPermissionsResult(int code,String[] p,int[] grants){
    super.onRequestPermissionsResult(code,p,grants);
    if(code==22){
      if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)record();
      else new AlertDialog.Builder(this).setTitle("Mikrofon niedostępny")
             .setMessage("Włącz uprawnienie Mikrofon w ustawieniach aplikacji.")
             .setPositiveButton("OK",null).show();
    }
  }
  private void refresh(){
    boolean running=RecordingService.running;
    status.setText(running?"●   TRWA NAGRYWANIE Z MIKROFONU":"Gotowy do nagrywania");
    status.setTextColor(running?RED:WHITE);
    start.setEnabled(!running);start.setAlpha(running?.4f:1f);
    stop.setEnabled(running);stop.setAlpha(running?1f:.4f);
    files.removeAllViews();
    File[] arr=folder().listFiles(f->f.isFile()&&f.getName().endsWith(".m4a"));
    if(arr==null||arr.length==0){text(files,"Brak zapisanych nagrań",14,GREY);return;}
    Arrays.sort(arr,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
    for(File f:arr){
      TextView row=text(files,"▶   "+f.getName()+"\n      "+String.format(Locale.US,"%.1f MB",f.length()/1048576.0),14,WHITE);
      row.setPadding(dp(12),dp(14),dp(12),dp(14));
      GradientDrawable bg=new GradientDrawable();bg.setColor(0xff171717);bg.setCornerRadius(dp(10));
      bg.setStroke(dp(1),0xff3b3b3b);row.setBackground(bg);
      LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)row.getLayoutParams();lp.bottomMargin=dp(9);row.setLayoutParams(lp);
      row.setOnClickListener(v->play(f));
      row.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Usunąć nagranie?")
         .setMessage(f.getName()).setNegativeButton("Anuluj",null)
         .setPositiveButton("Usuń",(d,w)->{if(f.equals(playing))stopPlayer();f.delete();refresh();}).show();return true;});
    }
  }
  private void stopPlayer(){
    if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}
    playing=null;
  }
  private void play(File f){
    if(f.equals(playing)){stopPlayer();status.setText("Odtwarzanie zatrzymane");return;}
    stopPlayer();
    try{
      player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.prepare();
      player.setOnCompletionListener(mp->{stopPlayer();status.setText("Odtwarzanie zakończone");});
      player.start();playing=f;status.setText("Odtwarzanie: "+f.getName());
    }catch(Exception e){stopPlayer();status.setText("Błąd odtwarzania: "+e.getClass().getSimpleName());}
  }
  @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
  @Override protected void onDestroy(){stopPlayer();super.onDestroy();}
  private static final class Ring extends View{
    private final Paint p=new Paint(3);
    Ring(Context c){super(c);}
    @Override protected void onDraw(Canvas c){
      float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.76f;
      p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);
      c.drawCircle(x,y,r,p);p.setColor(RED);p.setStyle(Paint.Style.FILL);c.drawCircle(x-r,y,10,p);
    }
  }
}
