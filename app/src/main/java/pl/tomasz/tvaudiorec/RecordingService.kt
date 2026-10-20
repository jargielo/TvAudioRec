package pl.tomasz.tvaudiorec

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class RecordingService : Service() {
    companion object {
        const val START = "pl.n2s.tvaudiorecorder.START"
        const val STOP = "pl.n2s.tvaudiorecorder.STOP"
        @Volatile var isRecording = false
        @Volatile var startedAt = 0L
        @Volatile var lastResult = ""
        @Volatile var currentLevel = 0
    }
    private var recordingThread: Thread? = null
    private var playbackRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var projection: MediaProjection? = null
    private val settings by lazy { getSharedPreferences("record_settings", MODE_PRIVATE) }
    override fun onBind(intent:Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("tv_audio_record", "Nagrywanie dźwięku TV",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when (intent?.action) {
            STOP -> stopCapture()
            START -> if(recordingThread==null && !isRecording) {
                val source=settings.getString("source","Internal") ?: "Internal"
                val required=if(source=="Microphone") ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else if(source=="Internal") ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                val stopIntent=PendingIntent.getService(this,11,
                    Intent(this,RecordingService::class.java).setAction(STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val notification=Notification.Builder(this,"tv_audio_record")
                    .setContentTitle("TV Audio Recorder")
                    .setContentText("Nagrywanie dźwięku: wybierz STOP, aby zapisać")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setOngoing(true)
                    .addAction(android.R.drawable.ic_media_pause,"ZATRZYMAJ",stopIntent).build()
                try {
                    startForeground(1301,notification,required)
                } catch(e:Exception) {
                    lastResult="Nie można uruchomić usługi: "+e.message
                    stopSelf()
                    return START_NOT_STICKY
                }
                @Suppress("DEPRECATION")
                val permission=intent.getParcelableExtra<Intent>("data")
                val code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED)
                isRecording=true
                startedAt=System.currentTimeMillis()
                lastResult=""
                recordingThread=Thread { recordNow(source,code,permission) }.also {it.start()}
            }
        }
        return START_NOT_STICKY
    }
    private fun stopCapture() {
        isRecording=false
        try{playbackRecord?.stop()}catch (_:Exception){}
        try{micRecord?.stop()}catch (_:Exception){}
        if(recordingThread==null)stopSelf()
    }
    private fun makeRecorder(rate:Int,channels:Int,source:String,proj:MediaProjection?):AudioRecord {
        val channelMask=if(channels==1)AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val format=AudioFormat.Builder().setSampleRate(rate)
            .setChannelMask(channelMask)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val buffer=AudioRecord.getMinBufferSize(rate,channelMask,AudioFormat.ENCODING_PCM_16BIT)
        check(buffer>0){"Nieobsługiwana częstotliwość na TV"}
        val builder=AudioRecord.Builder().setAudioFormat(format)
            .setBufferSizeInBytes(max(buffer*3,32768))
        if(source=="Internal") {
            checkNotNull(proj){"Brak zgody na przechwytywanie"}
            val config=AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            builder.setAudioPlaybackCaptureConfig(config)
        } else builder.setAudioSource(MediaRecorder.AudioSource.MIC)
        val rec=builder.build()
        check(rec.state==AudioRecord.STATE_INITIALIZED){"System TV nie uruchomił źródła "+source}
        if(source=="Bluetooth") {
            if(android.os.Build.VERSION.SDK_INT>=31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
                error("Brak zezwolenia na Bluetooth")
            val manager=getSystemService(AUDIO_SERVICE) as AudioManager
            val device=manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
                it.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (android.os.Build.VERSION.SDK_INT>=31 && it.type==AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            if(device==null) {
                rec.release()
                error("Brak połączonego mikrofonu Bluetooth")
            }
            if(!rec.setPreferredDevice(device)) {
                rec.release()
                error("Nie można ustawić mikrofonu Bluetooth")
            }
        }
        return rec
    }
    private fun recordNow(source:String, code:Int, permission:Intent?) {
        var sink:AudioSink?=null
        var created:RecordingLibrary.Output?=null
        var desc:ParcelFileDescriptor?=null
        var wake:PowerManager.WakeLock?=null
        var success=false
        try {
            val fmt=settings.getString("format","M4A") ?: "M4A"
            // Opus operates at 48 kHz (resampling would add needless complexity).
            val rate=if(fmt=="OPUS") 48000 else settings.getInt("rate",48000)
            val channels=settings.getInt("channels",2)
            val bitrate=if(fmt=="OPUS")settings.getInt("opus_bitrate",96000)
                else settings.getInt("bitrate",192000)
            val needSystem=source!="Microphone"
            val needMic=source!="Internal"
            wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,"TvAudioRecorder:Recorder")
            wake.acquire(12L*60L*60L*1000L)
            if(needSystem) {
                check(code==Activity.RESULT_OK && permission!=null){"Nie udzielono zgody na nagrywanie"}
                val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val proj=manager.getMediaProjection(code,permission!!)
                    ?: error("Brak dostępu do MediaProjection")
                projection=proj
                proj.registerCallback(object:MediaProjection.Callback() {
                    override fun onStop() {stopCapture()}
                },Handler(Looper.getMainLooper()))
                playbackRecord=makeRecorder(rate,channels,"Internal",proj)
            }
            if(needMic)
                micRecord=makeRecorder(rate,channels,
                    if(source=="Internal + Bluetooth") "Bluetooth" else "Mic",null)
            playbackRecord?.startRecording()
            micRecord?.startRecording()
            val extension=when(fmt) {
                "WAV"->"wav";"FLAC"->"flac";"MP3"->"mp3";"MP4"->"mp4";"OPUS"->"opus";else->"m4a"
            }
            val mime=when(fmt) {
                "WAV"->"audio/wav";"FLAC"->"audio/flac";"MP3"->"audio/mpeg";"OPUS"->"audio/ogg";else->"audio/mp4"
            }
            val name="TV_Audio_"+SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.ROOT)
                .format(Date())+"."+extension
            created=RecordingLibrary.create(this,name,mime)
            desc=created.fd
            sink=when(fmt) {
                "WAV"->WavSink(desc,rate,channels)
                "FLAC"->FlacSink(desc,rate,channels)
                "MP3"->Mp3LameSink(desc,rate,channels,bitrate)
                "OPUS"->OggOpusSink(desc,channels,bitrate)
                else->CompressedSink(desc,rate,channels,bitrate,false)
            }
            val bytesPerFrame=channels*2
            val input=ByteArray(16384)
            val extra=ByteArray(16384)
            val peaks=ArrayList<Int>()
            var totalBytes=0L
            var sumLevel=0L
            var samplesLevel=0
            while(isRecording) {
                val n=when {
                    playbackRecord!=null ->playbackRecord!!.read(input,0,input.size,AudioRecord.READ_BLOCKING)
                    micRecord!=null ->micRecord!!.read(input,0,input.size,AudioRecord.READ_BLOCKING)
                    else -> error("Brak źródła audio")
                }
                if(n<=0) {
                    if(!isRecording)break
                    error("Odczyt audio przerwany: "+n)
                }
                val aligned=n/bytesPerFrame*bytesPerFrame
                if(playbackRecord!=null && micRecord!=null) {
                    var count=0
                    while(count<aligned && isRecording) {
                        val received=micRecord!!.read(extra,count,aligned-count,AudioRecord.READ_BLOCKING)
                        if(received<=0)break
                        count+=received
                    }
                    for(i in 0 until aligned-1 step 2) {
                        val left=((input[i+1].toInt() shl 8) or (input[i].toInt() and 255)).toShort().toInt()
                        val right=if(i+1<count)
                            ((extra[i+1].toInt() shl 8) or (extra[i].toInt() and 255)).toShort().toInt()
                            else 0
                        val mixed=(left+right).coerceIn(-32768,32767)
                        input[i]=(mixed and 255).toByte()
                        input[i+1]=((mixed ushr 8) and 255).toByte()
                    }
                }
                if(aligned<=0)continue
                sink.write(input,aligned)
                totalBytes+=aligned
                for(i in 0 until aligned-1 step 2) {
                    val value=((input[i+1].toInt() shl 8) or (input[i].toInt() and 255)).toShort().toInt()
                    sumLevel+=abs(value)
                    samplesLevel++
                }
                if(samplesLevel>=rate*channels/7) {
                    val peak=(sumLevel/samplesLevel).toInt().coerceIn(0,32767)
                    currentLevel=peak
                    peaks.add(peak)
                    sumLevel=0;samplesLevel=0
                    if(peaks.size>1500) {
                        val compact=ArrayList<Int>()
                        for(i in peaks.indices step 2)
                            compact.add(max(peaks[i],peaks.getOrElse(i+1){0}))
                        peaks.clear();peaks.addAll(compact)
                    }
                }
            }
            sink.finish()
            success=totalBytes>0
            if(success) {
                settings.edit().putString("wave."+name,peaks.joinToString(","))
                    .putString("lastfile",name).apply()
                lastResult="Zapisano: "+name
            } else lastResult="Nie zapisano pustego nagrania"
        } catch (e:Exception) {
            lastResult="Błąd nagrywania: "+(e.message ?: e.javaClass.simpleName)
            settings.edit().putString("last_recording_result",lastResult).commit()
            android.util.Log.e("TVAudioRecorder",lastResult,e)
        } finally {
            isRecording=false
            currentLevel=0
            try{playbackRecord?.stop()}catch (_:Exception){}
            try{micRecord?.stop()}catch (_:Exception){}
            try{playbackRecord?.release()}catch (_:Exception){}
            try{micRecord?.release()}catch (_:Exception){}
            playbackRecord=null;micRecord=null
            try{sink?.close()}catch (_:Exception){}
            try{desc?.close()}catch (_:Exception){}
            if(created!=null)try{
                lastResult=RecordingLibrary.finalize(this,created!!,success)
                settings.edit().putString("last_recording_result",lastResult).commit()
            }catch(e:Exception) {
                lastResult="Błąd finalizacji pliku: "+e.message
                settings.edit().putString("last_recording_result",lastResult).commit()
                android.util.Log.e("TVAudioRecorder","Finalization failed",e)
            }
            try{projection?.stop()}catch (_:Exception){}
            projection=null
            try{wake?.release()}catch (_:Exception){}
            recordingThread=null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        if(isRecording) stopCapture()
        super.onDestroy()
    }
}