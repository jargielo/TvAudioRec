package pl.n2s.grabberpie;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class RecordingService extends Service {
  public static final String START="pl.n2s.grabberpie.START", STOP="pl.n2s.grabberpie.STOP";
  public static volatile boolean running=false;
  public static volatile long startedAt=0;
  public static volatile int level=0;
  public static volatile String lastError="";
  private MediaRecorder recorder;
  private AudioRecord audio;
  private Thread worker;
  private PowerManager.WakeLock lock;
  private File file;
  private volatile boolean wavActive;
  private long dataLength;
  @Override public IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_NOT_STICKY;
    if(STOP.equals(intent.getAction())){finish();stopSelf();return START_NOT_STICKY;}
    if(!START.equals(intent.getAction())||running)return START_NOT_STICKY;
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){lastError="Brak zgody na mikrofon";stopSelf();return START_NOT_STICKY;}
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("rec","Grabber · Nagrywanie",NotificationManager.IMPORTANCE_LOW));
    Intent open=new Intent(this,MainActivity.class);
    PendingIntent pi=PendingIntent.getActivity(this,5,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    startForeground(47,new Notification.Builder(this,"rec").setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setContentTitle("AudioRelay Grabber").setContentText("Nagrywanie z mikrofonu").setOngoing(true).setContentIntent(pi).build());
    try{
      PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
      lock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"grabberpie:record");
      lock.acquire();
      String format=intent.getStringExtra("format");
      int rate=Math.max(64000,intent.getIntExtra("rate",128)*1000);
      int hz=intent.getIntExtra("hz",44100);
      if(hz!=16000&&hz!=32000&&hz!=44100&&hz!=48000)hz=44100;
      File dir=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
      if(dir==null)dir=new File(getFilesDir(),"Music");
      if(!dir.exists()&&!dir.mkdirs())throw new IOException("Brak folderu Music");
      file=new File(dir,"Grabber_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+
        ("WAV".equals(format)?".wav":".m4a"));
      if("WAV".equals(format)) startWav(hz);
      else startM4a(hz,rate);
      startedAt=System.currentTimeMillis();running=true;lastError="";
    }catch(Exception ex){
      lastError=ex.getClass().getSimpleName()+": "+ex.getMessage();
      finish();stopSelf();
    }
    return START_NOT_STICKY;
  }
  private void startM4a(int hz,int bitrate)throws Exception{
    recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
    recorder.setAudioChannels(1);recorder.setAudioSamplingRate(hz);
    recorder.setAudioEncodingBitRate(bitrate);recorder.setOutputFile(file.getAbsolutePath());
    recorder.prepare();recorder.start();
  }
  private void startWav(int sampleRate)throws Exception{
    final int sr=sampleRate;
    int min=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    if(min<0)throw new IOException("Nieobsługiwana częstotliwość: "+sr);
    audio=new AudioRecord(MediaRecorder.AudioSource.MIC,sr,AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,Math.max(min*4,8192));
    if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Nie można otworzyć mikrofonu.");
    audio.startRecording();wavActive=true;
    worker=new Thread(()->{
      try(RandomAccessFile out=new RandomAccessFile(file,"rw")){
        out.write(WaveHelper.header(0,sr,1));
        byte[] b=new byte[8192];long total=0;
        while(wavActive){
          int n=audio.read(b,0,b.length);
          if(n>0){out.write(b,0,n);total+=n;level=amplitude(b,n);}
          else if(n<0){lastError="Nie udało się odczytać mikrofonu ("+n+")";break;}
        }
        out.seek(0);out.write(WaveHelper.header(total,sr,1));
      }catch(Exception e){lastError="Błąd WAV: "+e.getMessage();}
    },"Grabber-Wav");worker.start();
  }
  private static int amplitude(byte[] b,int length){
    int peak=0;for(int i=0;i+1<length;i+=2){int v=(short)(((b[i+1]&0xff)<<8)|(b[i]&0xff));peak=Math.max(peak,Math.abs(v));}return peak;
  }
  private void finish(){
    running=false;level=0;
    if(recorder!=null){
      try{recorder.stop();}catch(Exception e){if(file!=null)file.delete();lastError="Zbyt krótkie nagranie";}
      try{recorder.reset();}catch(Exception ignored){}recorder.release();recorder=null;
    }
    wavActive=false;
    if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();audio=null;}
    if(worker!=null){try{worker.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}worker=null;}
    if(lock!=null){if(lock.isHeld())lock.release();lock=null;}
    stopForeground(true);
  }
  @Override public void onDestroy(){finish();super.onDestroy();}
  public static int reportedLevel(){return level;}
}
