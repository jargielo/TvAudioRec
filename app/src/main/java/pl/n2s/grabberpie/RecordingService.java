package pl.n2s.grabberpie;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RecordingService extends Service {
  public static final String START="pl.n2s.grabberpie.START";
  public static final String STOP="pl.n2s.grabberpie.STOP";
  public static volatile boolean running=false;
  private MediaRecorder mic;
  private File output;
  @Override public IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent i,int f,int id){
    if(i==null)return START_NOT_STICKY;
    if(STOP.equals(i.getAction())){end();stopSelf();return START_NOT_STICKY;}
    if(START.equals(i.getAction())&&!running){
      if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
         stopSelf();return START_NOT_STICKY;
      }
      NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      NotificationChannel channel=new NotificationChannel("recording","Grabber - nagrywanie",NotificationManager.IMPORTANCE_LOW);
      n.createNotificationChannel(channel);
      PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
           PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
      Notification note=new Notification.Builder(this,"recording")
          .setSmallIcon(android.R.drawable.ic_btn_speak_now)
          .setContentTitle("AudioRelay Grabber")
          .setContentText("Trwa nagrywanie z mikrofonu")
          .setOngoing(true).setContentIntent(pi).build();
      startForeground(19,note);
      begin();
    }
    return START_NOT_STICKY;
  }
  private void begin(){
    try{
      File dir=getExternalFilesDir(Environment.DIRECTORY_MUSIC);
      if(dir==null)dir=new File(getFilesDir(),"Music");
      if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Folder");
      output=new File(dir,"Grabber_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+".m4a");
      mic=new MediaRecorder();
      mic.setAudioSource(MediaRecorder.AudioSource.MIC);
      mic.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      mic.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      mic.setAudioChannels(1);
      mic.setAudioSamplingRate(44100);
      mic.setAudioEncodingBitRate(128000);
      mic.setOutputFile(output.getAbsolutePath());
      mic.prepare();mic.start();running=true;
    }catch(Exception ex){
      if(mic!=null){try{mic.reset();}catch(Exception ignored){}mic.release();mic=null;}
      if(output!=null)output.delete();
      running=false;stopForeground(true);stopSelf();
    }
  }
  private void end(){
    if(mic!=null){
      try{if(running)mic.stop();}catch(Exception e){if(output!=null)output.delete();}
      try{mic.reset();}catch(Exception ignored){}
      mic.release();mic=null;
    }
    running=false;stopForeground(true);
  }
  @Override public void onDestroy(){end();super.onDestroy();}
}
