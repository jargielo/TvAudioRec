package pl.n2s.grabberpie;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class RecordingService extends Service {
  public static final String START="pl.n2s.grabberpie.START", STOP="pl.n2s.grabberpie.STOP";
  public static volatile boolean running=false,processing=false;
  public static volatile long startedAt=0;
  public static volatile int level=0;
  public static volatile String lastError="";
  private static volatile RecordingService instance;
  private AudioRecord internalRecord, microphoneRecord;
  private MediaProjection projection;
  private Thread worker;
  private PowerManager.WakeLock wakeLock;
  private AudioManager audioManager;
  private boolean scoStarted=false;
  private volatile boolean capturing=false;
  private File temporary, target;
  private int hz=44100, kbps=128;
  private String format="M4A", source="microphone";
  @Override public IBinder onBind(Intent intent){return null;}
  @Override public int onStartCommand(Intent intent,int flags,int id){
    if(intent==null)return START_NOT_STICKY;
    if(STOP.equals(intent.getAction())){stopAndSave();return START_NOT_STICKY;}
    if(!START.equals(intent.getAction()) || running || processing)return START_NOT_STICKY;
    instance=this;
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      lastError="Brak uprawnienia mikrofonu";stopSelf();return START_NOT_STICKY;
    }
    source=intent.getStringExtra("source");
    if(source==null)source="microphone";
    format=intent.getStringExtra("format");
    if(format==null)format="M4A";
    hz=intent.getIntExtra("hz",44100);
    if(hz!=16000&&hz!=32000&&hz!=44100&&hz!=48000)hz=44100;
    kbps=intent.getIntExtra("rate",128);
    audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
    NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    manager.createNotificationChannel(new NotificationChannel("rec","Grabber - nagrywanie",NotificationManager.IMPORTANCE_LOW));
    PendingIntent open=PendingIntent.getActivity(this,5,new Intent(this,MainActivity.class),
      PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification notification=new Notification.Builder(this,"rec")
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setContentTitle("AudioRelay Grabber").setContentText("Nagrywanie dźwięku")
      .setOngoing(true).setContentIntent(open).build();
    if(Build.VERSION.SDK_INT>=29 && !"microphone".equals(source))
      startForeground(47,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    else startForeground(47,notification);
    try{
      PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
      wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"grabber:record");
      wakeLock.acquire();
      File folder=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
      if(folder==null)folder=new File(getFilesDir(),"Music");
      if(!folder.exists()&&!folder.mkdirs())throw new IOException("Brak folderu nagrań");
      String base="Grabber_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date());
      temporary=new File(folder,base+".partial.wav");
      target=new File(folder,base+"."+format.toLowerCase(Locale.US));
      startCapture(intent);
      startedAt=System.currentTimeMillis();
      level=0;
      lastError="";
      processing=false;
      running=true;
    }catch(Exception e){
      lastError="Błąd uruchomienia: "+e.getMessage();
      releaseCapture();
      stopForeground(true);
      stopSelf();
    }
    return START_NOT_STICKY;
  }
  private AudioRecord createMic(int buffer,boolean bluetooth)throws Exception{
    AudioRecord mic=new AudioRecord(MediaRecorder.AudioSource.MIC,hz,
      AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);
    if(mic.getState()!=AudioRecord.STATE_INITIALIZED){
      mic.release();throw new IOException("Mikrofon urządzenia jest niedostępny");
    }
    if(bluetooth){
      AudioDeviceInfo selected=null;
      for(AudioDeviceInfo device:audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)){
        int type=device.getType();
        if(type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
           (Build.VERSION.SDK_INT>=31 && type==AudioDeviceInfo.TYPE_BLE_HEADSET)){
          selected=device;break;
        }
      }
      if(selected==null){mic.release();throw new IOException("Nie wykryto mikrofonu Bluetooth. Połącz zestaw obsługujący wejście audio.");}
      boolean routed=mic.setPreferredDevice(selected);
      if(!routed){mic.release();throw new IOException("Nie można skierować wejścia na mikrofon Bluetooth");}
      if(selected.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO && Build.VERSION.SDK_INT<=30){
        audioManager.startBluetoothSco();scoStarted=true;
      }
    }
    return mic;
  }
  private void startCapture(Intent intent)throws Exception{
    int size=AudioRecord.getMinBufferSize(hz,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    if(size<=0)throw new IOException("Nieobsługiwana częstotliwość "+hz);
    int buffer=Math.max(8192,size*4);
    boolean wantsInternal=!"microphone".equals(source);
    boolean mix="mix".equals(source)||"btmix".equals(source);
    if(wantsInternal){
      if(Build.VERSION.SDK_INT<29)throw new IOException("Dźwięk wewnętrzny wymaga Androida 10+");
      Intent granted=intent.getParcelableExtra("projectionData");
      if(granted==null)throw new IOException("Brak zgody MediaProjection");
      MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      projection=mgr.getMediaProjection(intent.getIntExtra("projectionCode",0),granted);
      if(projection==null)throw new IOException("Brak MediaProjection");
      projection.registerCallback(new MediaProjection.Callback(){
        @Override public void onStop(){
          new Handler(Looper.getMainLooper()).post(()->{
            if(running)stopAndSave();
          });
        }
      },new Handler(Looper.getMainLooper()));
      AudioPlaybackCaptureConfiguration cfg=new AudioPlaybackCaptureConfiguration.Builder(projection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME).build();
      AudioFormat pcm=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(hz).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
      internalRecord=new AudioRecord.Builder().setAudioPlaybackCaptureConfig(cfg)
        .setAudioFormat(pcm).setBufferSizeInBytes(buffer).build();
      if(internalRecord.getState()!=AudioRecord.STATE_INITIALIZED)
        throw new IOException("Nie można przechwycić dźwięku aplikacji");
      internalRecord.startRecording();
    }
    if(!wantsInternal || mix){
      microphoneRecord=createMic(buffer,"btmix".equals(source));
      microphoneRecord.startRecording();
      if("btmix".equals(source)){
        AudioDeviceInfo current=microphoneRecord.getRoutedDevice();
        if(current==null || (current.getType()!=AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
          !(Build.VERSION.SDK_INT>=31&&current.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET)))
          throw new IOException("Dźwięk nie jest kierowany przez mikrofon Bluetooth");
      }
    }
    capturing=true;
    worker=new Thread(()->writeAudio(mix),"Grabber-Audio");
    worker.start();
  }
  private void writeAudio(boolean mix){
    try(RandomAccessFile out=new RandomAccessFile(temporary,"rw")){
      out.setLength(0);
      out.write(WaveHelper.header(0,hz,1));
      byte[] a=new byte[4096],b=new byte[4096];
      long total=0;
      while(capturing){
        AudioRecord primary=internalRecord!=null?internalRecord:microphoneRecord;
        int n=primary.read(a,0,a.length);
        if(n<=0){
          if(capturing)lastError="Odczyt audio nieudany: "+n;
          break;
        }
        if(mix && microphoneRecord!=null){
          int m=microphoneRecord.read(b,0,n);
          if(m>0){
            int count=Math.min(n,m)&~1;
            for(int i=0;i<count;i+=2){
              int left=(short)(((a[i+1]&255)<<8)|(a[i]&255));
              int right=(short)(((b[i+1]&255)<<8)|(b[i]&255));
              int combined=Math.max(-32768,Math.min(32767,(left+right)/2));
              a[i]=(byte)combined;a[i+1]=(byte)(combined>>>8);
            }
          }
        }
        out.write(a,0,n);
        total+=n;
        level=amplitude(a,n);
      }
      out.seek(0);
      out.write(WaveHelper.header(total,hz,1));
    }catch(Exception e){
      lastError="Błąd zapisu WAV: "+e.getMessage();
    }
  }
  private static int amplitude(byte[] data,int n){
    int peak=0;
    for(int i=0;i+1<n;i+=2){
      int v=(short)(((data[i+1]&255)<<8)|(data[i]&255));
      peak=Math.max(peak,Math.abs(v));
    }
    return peak;
  }
  private synchronized void stopAndSave(){
    if(!running)return;
    running=false;
    processing=true;
    releaseCapture();
    if(temporary==null||!temporary.isFile()||temporary.length()<=44){
      lastError="Brak danych nagrania";
      processing=false;finishService();return;
    }
    if("WAV".equalsIgnoreCase(format)){
      if(!temporary.renameTo(target))lastError="Nie można zapisać WAV";
      processing=false;finishService();return;
    }
    String params=encodingParams();
    String args="-hide_banner -nostdin -y -i "+quote(temporary.getAbsolutePath())+
      " -vn "+params+" "+quote(target.getAbsolutePath());
    final File wav=temporary,encoded=target;
    try{
      FFmpegKit.executeAsync(args,session->{
        if(ReturnCode.isSuccess(session.getReturnCode())&&encoded.exists()&&encoded.length()>0){
          wav.delete();lastError="";
        }else{
          if(encoded.exists())encoded.delete();
          File rescued=new File(wav.getParent(),wav.getName().replace(".partial.wav",".wav"));
          if(!wav.renameTo(rescued))lastError="Błąd kodowania "+format+"; plik roboczy pozostawiono: "+wav.getName();
          else lastError="Błąd kodowania "+format+". Zapisano nagranie jako WAV.";
        }
        processing=false;finishService();
      });
    }catch(Throwable e){
      lastError="Brak kodera "+format+": "+e.getMessage();
      File rescued=new File(wav.getParent(),wav.getName().replace(".partial.wav",".wav"));
      wav.renameTo(rescued);
      processing=false;finishService();
    }
  }
  private String encodingParams(){
    int rate=Math.max(64,Math.min(320,kbps));
    switch(format.toUpperCase(Locale.US)){
      case "MP3":return "-c:a libmp3lame -b:a "+rate+"k -f mp3";
      case "FLAC":return "-c:a flac -compression_level 5 -f flac";
      case "AAC":return "-c:a aac -b:a "+rate+"k -f adts";
      case "3GP":return "-c:a aac -b:a 96k -ar 16000 -f 3gp";
      case "OGG":return "-c:a libopus -b:a "+rate+"k -ar 48000 -f ogg";
      default:return "-c:a aac -b:a "+rate+"k -f ipod";
    }
  }
  private static String quote(String path){return "'"+path.replace("'","'\\''")+"'";}
  private void releaseCapture(){
    capturing=false;level=0;
    if(internalRecord!=null){try{internalRecord.stop();}catch(Exception ignored){}}
    if(microphoneRecord!=null){try{microphoneRecord.stop();}catch(Exception ignored){}}
    if(worker!=null){
      try{worker.join(2500);}catch(InterruptedException e){Thread.currentThread().interrupt();}
      worker=null;
    }
    if(internalRecord!=null){internalRecord.release();internalRecord=null;}
    if(microphoneRecord!=null){microphoneRecord.release();microphoneRecord=null;}
    if(projection!=null){MediaProjection p=projection;projection=null;try{p.stop();}catch(Exception ignored){}}
    if(scoStarted){audioManager.stopBluetoothSco();scoStarted=false;}
  }
  private void finishService(){
    if(wakeLock!=null){if(wakeLock.isHeld())wakeLock.release();wakeLock=null;}
    stopForeground(true);stopSelf();
  }
  @Override public void onDestroy(){
    if(running)stopAndSave();
    super.onDestroy();
  }
  public static int reportedLevel(){return level;}
}
