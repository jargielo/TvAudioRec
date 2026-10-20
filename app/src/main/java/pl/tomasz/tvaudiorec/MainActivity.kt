package pl.tomasz.tvaudiorec

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.os.Environment
import java.io.File
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val captureCode=542
    private val importCode=543
    private var allAudio=false
    private val dark=Color.rgb(27,39,52)
    private val surface=Color.rgb(36,51,67)
    private val accent=Color.rgb(255,209,28)
    private val textColor=Color.rgb(245,247,249)
    private val subtle=Color.rgb(171,184,198)
    private val prefs by lazy { getSharedPreferences("record_settings",MODE_PRIVATE) }
    private lateinit var root:LinearLayout
    private lateinit var panel:LinearLayout
    private lateinit var status:TextView
    private var timer:TextView?=null
    private var waveform:WaveformView?=null
    private var clock:TextView?=null
    private var playSeek:SeekBar?=null
    private var player:MediaPlayer?=null
    private var playing:AudioEntry?=null
    private var playButton:Button?=null
    private var loop=false
    private var page="Nagrywaj"
    private var lastRecording=false
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable {
        override fun run() {
            if(::status.isInitialized) {
                status.text=if(RecordingService.isRecording) "Trwa nagrywanie..."
                    else if(RecordingService.lastResult.isNotEmpty()) RecordingService.lastResult
                    else "Gotowy. Pilot: strzałki / OK."
            }
            timer?.text=if(RecordingService.isRecording)
                time((System.currentTimeMillis()-RecordingService.startedAt).coerceAtLeast(0))
                else "00:00"
            if(RecordingService.isRecording) waveform?.liveLevel=RecordingService.currentLevel
            val mp=player
            if(mp!=null) try {
                if(mp.duration>0) {
                    playSeek?.progress=(mp.currentPosition.toLong()*1000L/mp.duration).toInt()
                    waveform?.progress=mp.currentPosition.toFloat()/mp.duration
                    clock?.text=time(mp.currentPosition.toLong())
                }
            }catch (_:Exception){}
            if(lastRecording && !RecordingService.isRecording && page=="Nagrywaj") showPage("Nagrywaj")
            lastRecording=RecordingService.isRecording
            handler.postDelayed(this,500)
        }
    }
    data class AudioEntry(val uri:Uri,val name:String,val bytes:Long,val added:Long)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(dark)
        root=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(20),dp(20),dp(20),dp(20))
        }
        setContentView(root)
        val side=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(7),dp(6),dp(17),dp(3))
        }
        root.addView(side,LinearLayout.LayoutParams(dp(275),-1))
        side.addView(label("TV AUDIO",30,accent))
        side.addView(label("RECORDER  •  v1.1",17,subtle))
        side.addView(space(26))
        for(name in listOf("Nagrywaj","Nagrania","Ulubione","Kosz","Ustawienia")) {
            val title=when(name){
                "Nagrywaj"->"●  NAGRYWANIE"
                "Nagrania"->"≡  MOJE NAGRANIA"
                "Ulubione"->"☆  ULUBIONE"
                "Kosz"->"▣  KOSZ"
                else->"⚙  USTAWIENIA"
            }
            side.addView(btn(title) {showPage(name)})
        }
        side.addView(space(12))
        side.addView(label("Nagrywaj dźwięk z aplikacji bez nagrywania obrazu.",15,subtle))
        side.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        status=label("Gotowy.",15,subtle)
        side.addView(status)
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(surface)
        }
        panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(25),dp(19),dp(25),dp(16))
        }
        scroll.addView(panel)
        root.addView(scroll,LinearLayout.LayoutParams(0,-1,1f))
        showPage("Nagrywaj")
        handler.post(ticker)
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release();player=null
        super.onDestroy()
    }
    private fun dp(v:Int):Int=(resources.displayMetrics.density*v).toInt()
    private fun label(value:String,size:Int=18,color:Int=textColor):TextView=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(color)
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(3),dp(5),dp(3),dp(5))
    }
    private fun space(h:Int):View=View(this).apply {minimumHeight=dp(h)}
    private fun btn(value:String,click:()->Unit):Button=Button(this).apply {
        text=value
        textSize=16f
        isAllCaps=false
        isFocusable=true
        setTextColor(textColor)
        setPadding(dp(13),dp(10),dp(13),dp(10))
        background=rounded(surface)
        setOnFocusChangeListener { view,hasFocus ->
            view.background=rounded(if(hasFocus)Color.rgb(87,103,118) else surface)
            (view as Button).setTextColor(if(hasFocus) accent else textColor)
        }
        setOnClickListener {click()}
    }
    private fun rounded(color:Int):GradientDrawable=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(13).toFloat()
        setStroke(dp(1),Color.rgb(68,84,98))
    }
    private fun line(title:String,sub:String?=null) {
        panel.addView(label(title,27,accent))
        if(sub!=null)panel.addView(label(sub,15,subtle))
        panel.addView(space(12))
    }
    private fun options(title:String,labels:List<Pair<String,String>>,chosen:String,
                        enabled:(String)->Boolean={true},onChange:(String)->Unit) {
        panel.addView(label(title,20))
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        for((display,key) in labels) {
            val b=btn((if(key==chosen)"✓  " else "")+display) {onChange(key)}
            b.isEnabled=enabled(key)
            if(!b.isEnabled)b.alpha=0.35f
            b.setBackgroundColor(if(key==chosen)Color.rgb(58,79,91) else surface)
            row.addView(b,LinearLayout.LayoutParams(0,dp(63),1f).apply {
                setMargins(dp(3),dp(3),dp(3),dp(3))
            })
        }
        panel.addView(row)
        panel.addView(space(10))
    }
    private fun showPage(target:String) {
        page=target
        panel.removeAllViews()
        timer=null;waveform=null;clock=null;playSeek=null;playButton=null
        when(target) {
            "Nagrywaj"->recordPage()
            "Nagrania"->filesPage(false,false)
            "Ulubione"->filesPage(false,true)
            "Kosz"->filesPage(true,false)
            "Ustawienia"->settingsPage()
        }
    }
    private fun recordPage() {
        line("REJESTRATOR DŹWIĘKU","Źródło: "+sourceName()+"    •    "+
            prefs.getString("format","M4A")+"    •    "+rateName()+"    •    "+
            (if(prefs.getInt("channels",2)==2)"Stereo" else "Mono"))
        timer=label("00:00",54,accent).also { it.gravity=Gravity.CENTER;panel.addView(it) }
        waveform=WaveformView(this).also {
            it.peaks=prefs.getString("wave."+(prefs.getString("lastfile","") ?: ""),"")
                .orEmpty().split(",").mapNotNull {value->value.toIntOrNull()}
            panel.addView(it,LinearLayout.LayoutParams(-1,dp(220)))
        }
        panel.addView(space(18))
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        row.addView(btn("▶  ROZPOCZNIJ NAGRYWANIE") {startRecording()},
            LinearLayout.LayoutParams(0,dp(64),1f))
        row.addView(btn("■  STOP I ZAPISZ") {stopRecording()},
            LinearLayout.LayoutParams(0,dp(64),1f))
        panel.addView(row)
        panel.addView(btn("⌂  WRÓĆ DO APLIKACJI TV") {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        })
        panel.addView(label("Aby nagrywać aplikację w tle, wybierz START i zaakceptuj systemową zgodę. "
            +"Niektóre aplikacje blokują przechwytywanie wewnętrznego audio.",16,subtle))
        panel.addView(btn("⚙  USTAWIENIA: ŹRÓDŁO / FORMAT / kHz / MONO-STEREO") {showPage("Ustawienia")})
        panel.addView(btn("≡  OTWÓRZ ZAPISANE NAGRANIA") {showPage("Nagrania")})
    }
    private fun sourceName():String=when(prefs.getString("source","Internal")) {
        "Microphone"->"Mikrofon";"Internal + Mic"->"Wewnętrzny + mikrofon"
        "Internal + Bluetooth"->"Wewnętrzny + Bluetooth";else->"Wewnętrzny"
    }
    private fun rateName():String=(prefs.getInt("rate",48000)/1000.0).toString()+" kHz"
    private fun startRecording() {
        if(RecordingService.isRecording){status.text="Nagrywanie już trwa";return}
        val missing=ArrayList<String>()
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT>=31 && prefs.getString("source","Internal")=="Internal + Bluetooth" &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.BLUETOOTH_CONNECT)
        if(missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(),441)
            status.text="Zezwól na uprawnienia i wybierz START ponownie."
            return
        }
        if(prefs.getString("format","M4A")=="MP3" && !EncoderSupport.mp3Available()) {
            status.text="MP3: ten telewizor nie ma enkodera. Wybierz M4A, WAV lub FLAC."
            return
        }
        if(prefs.getString("source","Internal")=="Microphone") launchService(Activity.RESULT_CANCELED,null)
        else try {
            val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(),captureCode)
        }catch(e:Exception){status.text="Brak obsługi nagrywania: "+e.message}
    }
    @Deprecated("Android TV projection permission")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==importCode) {
            if(resultCode==RESULT_OK && data?.data!=null) importAudio(data.data!!)
            else status.text="Anulowano wybór pliku."
            return
        }
        if(requestCode==captureCode) {
            if(resultCode==RESULT_OK && data!=null)launchService(resultCode,data)
            else status.text="Zgoda na nagrywanie została anulowana."
        }
    }
    private fun launchService(code:Int,data:Intent?) {
        try {
            startForegroundService(Intent(this,RecordingService::class.java).apply {
                action=RecordingService.START
                putExtra("resultCode",code)
                if(data!=null)putExtra("data",data)
            })
            status.text="Rozpoczynanie nagrywania..."
        }catch(e:Exception) {status.text="Błąd uruchamiania: "+e.message}
    }
    private fun stopRecording() {
        startService(Intent(this,RecordingService::class.java).setAction(RecordingService.STOP))
        status.text="Kończenie i zapisywanie nagrania..."
    }
    private fun getEntries():List<AudioEntry> {
        val result=ArrayList<AudioEntry>()
        val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.DATE_ADDED)
        try {
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,
                MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?",
                arrayOf("Music/TVAudioRecorder/%"),MediaStore.Audio.Media.DATE_ADDED+" DESC")?.use {c->
                while(c.moveToNext()) {
                    result.add(AudioEntry(
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,c.getLong(0)),
                        c.getString(1) ?: "Nagranie",
                        c.getLong(2),c.getLong(3)))
                }
            }
        }catch(e:Exception){status.text="Błąd odczytu plików: "+e.message}
        return result
    }
    private fun trashed(name:String)=prefs.getBoolean("trash."+name,false)
    private fun favorite(name:String)=prefs.getBoolean("favorite."+name,false)
    private fun filesPage(trash:Boolean,onlyFavorite:Boolean) {
        val heading=if(trash)"KOSZ" else if(onlyFavorite)"ULUBIONE NAGRANIA" else "MOJE NAGRANIA"
        line(heading,if(trash)"Nagrania można odzyskać lub trwale usunąć"
            else "Sortowanie: "+if(prefs.getBoolean("newest",true))"od najnowszych" else "od najstarszych")
        if(!trash)panel.addView(btn("↕  ZMIEŃ KOLEJNOŚĆ SORTOWANIA") {
            prefs.edit().putBoolean("newest",!prefs.getBoolean("newest",true)).apply()
            showPage(page)
        })
        val entries=getEntries().filter {trashed(it.name)==trash && (!onlyFavorite || favorite(it.name))}
            .let {if(prefs.getBoolean("newest",true))it else it.reversed()}
        if(entries.isEmpty()){panel.addView(label("Brak nagrań w tej sekcji.",20,subtle));return}
        var group=""
        for(entry in entries) {
            val d=SimpleDateFormat("dd.MM.yyyy",Locale("pl")).format(Date(entry.added*1000))
            if(d!=group) {
                group=d
                panel.addView(label(d,16,subtle))
            }
            val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
            val main=btn(entry.name+"    •    "+size(entry.bytes)) {
                if(trash)showTrashActions(entry) else showPlayer(entry)
            }
            row.addView(main,LinearLayout.LayoutParams(0,dp(65),1f))
            if(!trash) {
                row.addView(btn(if(favorite(entry.name))"★" else "☆") {
                    prefs.edit().putBoolean("favorite."+entry.name,!favorite(entry.name)).apply()
                    showPage(page)
                },LinearLayout.LayoutParams(dp(70),dp(65)))
                row.addView(btn("⋮") {showFileActions(entry)},
                    LinearLayout.LayoutParams(dp(70),dp(65)))
            } else row.addView(btn("PRZYWRÓĆ") {
                prefs.edit().putBoolean("trash."+entry.name,false).apply()
                showPage("Kosz")
            },LinearLayout.LayoutParams(dp(155),dp(65)))
            panel.addView(row)
        }
    }
    private fun showFileActions(entry:AudioEntry) {
        AlertDialog.Builder(this).setTitle(entry.name)
            .setItems(arrayOf("Odtwórz","Zmień nazwę","Dodaj / usuń ulubione","Przenieś do kosza")) {_,which->
                when(which){
                    0->showPlayer(entry)
                    1->rename(entry)
                    2->{
                        prefs.edit().putBoolean("favorite."+entry.name,!favorite(entry.name)).apply()
                        showPage(page)
                    }
                    3->{
                        prefs.edit().putBoolean("trash."+entry.name,true).apply()
                        showPage(page)
                    }
                }
            }.show()
    }
    private fun showTrashActions(entry:AudioEntry) {
        AlertDialog.Builder(this).setTitle(entry.name)
            .setItems(arrayOf("Przywróć nagranie","Usuń trwale")) {_,which->
                if(which==0) {
                    prefs.edit().putBoolean("trash."+entry.name,false).apply()
                    showPage("Kosz")
                } else AlertDialog.Builder(this).setTitle("Usunąć trwale?")
                    .setMessage("Usuniętego pliku nie będzie można odzyskać.")
                    .setPositiveButton("USUŃ") {_,_->
                        try {
                            contentResolver.delete(entry.uri,null,null)
                            prefs.edit().remove("trash."+entry.name)
                                .remove("wave."+entry.name)
                                .remove("favorite."+entry.name).apply()
                            showPage("Kosz")
                        } catch(e:Exception){status.text="Nie usunięto: "+e.message}
                    }.setNegativeButton("ANULUJ",null).show()
            }.show()
    }
    private fun rename(entry:AudioEntry) {
        val field=EditText(this).apply {
            setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT
            setText(entry.name.substringBeforeLast("."))
            selectAll()
        }
        AlertDialog.Builder(this).setTitle("Zmień nazwę nagrania")
            .setView(field)
            .setPositiveButton("ZAPISZ") {_,_->
                val newBase=field.text.toString().trim()
                    .replace(Regex("[/\\\\:*?\"<>|]"),"_").take(90)
                if(newBase.isEmpty())return@setPositiveButton
                val newName=newBase+"."+entry.name.substringAfterLast(".")
                try {
                    val values=ContentValues().apply {put(MediaStore.MediaColumns.DISPLAY_NAME,newName)}
                    val updated=contentResolver.update(entry.uri,values,null,null)
                    if(updated>0) {
                        prefs.edit()
                            .putString("wave."+newName,prefs.getString("wave."+entry.name,""))
                            .putBoolean("favorite."+newName,favorite(entry.name))
                            .putBoolean("trash."+newName,trashed(entry.name))
                            .remove("wave."+entry.name).remove("favorite."+entry.name)
                            .remove("trash."+entry.name).apply()
                        showPage(page)
                    }else status.text="Zmiana nazwy nie powiodła się."
                } catch(e:Exception){status.text="Błąd zmiany nazwy: "+e.message}
            }
            .setNegativeButton("ANULUJ",null).show()
    }
    private fun showPlayer(entry:AudioEntry) {
        page="Odtwarzacz"
        panel.removeAllViews()
        line("ODTWARZACZ","Nagranie: "+entry.name)
        panel.addView(label(size(entry.bytes),17,subtle))
        val wave=WaveformView(this)
        waveform=wave
        wave.peaks=prefs.getString("wave."+entry.name,"").orEmpty()
            .split(",").mapNotNull {it.toIntOrNull()}
        panel.addView(wave,LinearLayout.LayoutParams(-1,dp(195)))
        clock=label("00:00",40,accent).also {
            it.gravity=Gravity.CENTER;panel.addView(it)
        }
        val seek=SeekBar(this).apply {max=1000;progress=0}
        playSeek=seek
        panel.addView(seek)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s:SeekBar?,v:Int,fromUser:Boolean) {
                if(fromUser) {
                    try{player?.let {if(it.duration>0)it.seekTo((it.duration.toLong()*v/1000).toInt())}}
                    catch (_:Exception){}
                }
            }
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar?){}
        })
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        row.addView(btn("↶  -10 s") {skip(-10000)},LinearLayout.LayoutParams(0,dp(62),1f))
        playButton=btn("▶  ODTWÓRZ") {togglePlay(entry)}.also {
            row.addView(it,LinearLayout.LayoutParams(0,dp(62),2f))
        }
        row.addView(btn("+10 s  ↷") {skip(10000)},LinearLayout.LayoutParams(0,dp(62),1f))
        panel.addView(row)
        panel.addView(btn(if(loop)"⟲  POWTARZANIE WŁ." else "⟲  POWTARZANIE WYŁ.") {
            loop=!loop;player?.isLooping=loop;showPlayer(entry)
        })
        panel.addView(btn("☆  ULUBIONE / USUŃ / ZMIEŃ NAZWĘ") {showFileActions(entry)})
        panel.addView(btn("←  WRÓĆ DO LISTY NAGRAŃ") {showPage("Nagrania")})
        if(playing?.uri!=entry.uri) {
            player?.release();player=null;playing=null
        }
        if(playing?.uri==entry.uri)playButton?.text=if(player?.isPlaying==true)"❚❚  PAUZA" else "▶  ODTWÓRZ"
    }
    private fun togglePlay(entry:AudioEntry) {
        try {
            if(playing?.uri!=entry.uri || player==null) {
                player?.release()
                player=MediaPlayer().apply {
                    setDataSource(this@MainActivity,entry.uri)
                    prepare()
                    isLooping=loop
                    setOnCompletionListener {playButton?.text="▶  ODTWÓRZ"}
                }
                playing=entry
            }
            val mp=player ?:return
            if(mp.isPlaying)mp.pause() else mp.start()
            playButton?.text=if(mp.isPlaying)"❚❚  PAUZA" else "▶  ODTWÓRZ"
        }catch(e:Exception){status.text="Odtwarzanie nie działa: "+e.message}
    }
    private fun skip(ms:Int) {
        try{player?.let{it.seekTo((it.currentPosition+ms).coerceIn(0,it.duration))}}
        catch (_:Exception){}
    }
    private fun selectionRow(
        heading:String,value:String,choices:List<Pair<String,String>>,
        selected:String,onSelect:(String)->Unit
    ) {
        panel.addView(label(heading,19,subtle))
        panel.addView(btn("▾   "+value+"      (OK — ZMIEŃ)") {
            val index=choices.indexOfFirst {it.second==selected}.coerceAtLeast(0)
            AlertDialog.Builder(this).setTitle(heading)
                .setSingleChoiceItems(choices.map{it.first}.toTypedArray(),index) { dialog,which->
                    val key=choices[which].second
                    if(heading=="FORMAT NAGRANIA" && key=="MP3" && !EncoderSupport.mp3Available()) {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("MP3 niedostępny na tym telewizorze")
                            .setMessage("Brak systemowego enkodera MP3. Dostępne formaty: "
                                +"M4A, MP4 (tylko audio), WAV i FLAC.")
                            .setPositiveButton("OK",null).show()
                    } else {
                        onSelect(key);dialog.dismiss();showPage("Ustawienia")
                    }
                }.setNegativeButton("ANULUJ",null).show()
        })
        panel.addView(space(9))
    }
    private fun settingsPage() {
        line("USTAWIENIA NAGRYWANIA",
            "Każde ustawienie to osobny przycisk. Pilot: strzałki, OK, Wstecz.")
        val source=prefs.getString("source","Internal") ?: "Internal"
        selectionRow("ŹRÓDŁO DŹWIĘKU",sourceName(),listOf(
            "Dźwięk wewnętrzny" to "Internal",
            "Mikrofon" to "Microphone",
            "Wewnętrzny + mikrofon" to "Internal + Mic",
            "Wewnętrzny + mikrofon Bluetooth" to "Internal + Bluetooth"),source) {
            prefs.edit().putString("source",it).apply()
        }
        val format=prefs.getString("format","M4A") ?: "M4A"
        selectionRow("FORMAT NAGRANIA",when(format) {
            "MP4"->"MP4 — tylko audio AAC"
            "MP3"->"MP3 — kompresja stratna"
            "WAV"->"WAV — bez kompresji"
            "FLAC"->"FLAC — bezstratny"
            else->"M4A — AAC"
        },listOf(
            "M4A — AAC" to "M4A",
            "MP4 — AAC (bez obrazu)" to "MP4",
            "MP3 — wymaga enkodera telewizora" to "MP3",
            "WAV — PCM bez kompresji" to "WAV",
            "FLAC — bezstratny" to "FLAC"),format) {
            prefs.edit().putString("format",it).apply()
        }
        val rate=prefs.getInt("rate",48000).toString()
        selectionRow("CZĘSTOTLIWOŚĆ (kHz)",rate+" Hz",
            listOf("8 kHz" to "8000","16 kHz" to "16000",
                "22,05 kHz" to "22050","32 kHz" to "32000",
                "44,1 kHz" to "44100","48 kHz" to "48000"),rate) {
            prefs.edit().putInt("rate",it.toInt()).apply()
        }
        val channels=prefs.getInt("channels",2).toString()
        selectionRow("KANAŁY",if(channels=="2")"Stereo" else "Mono",
            listOf("Mono" to "1","Stereo" to "2"),channels) {
            prefs.edit().putInt("channels",it.toInt()).apply()
        }
        val bitrate=prefs.getInt("bitrate",192000).toString()
        selectionRow("JAKOŚĆ AAC / MP3",(bitrate.toInt()/1000).toString()+" kb/s",
            listOf("96 kb/s" to "96000","128 kb/s" to "128000",
                "192 kb/s" to "192000","256 kb/s" to "256000",
                "320 kb/s" to "320000"),bitrate) {
            prefs.edit().putInt("bitrate",it.toInt()).apply()
        }
        panel.addView(label("MP4 oznacza plik audio w kontenerze MP4 (bez obrazu). "
            +"Źródło wewnętrzne wymaga systemowej zgody na przechwytywanie. "
            +"Niektóre aplikacje blokują nagrywanie.",16,subtle))
        panel.addView(btn("←  WRÓĆ DO NAGRYWANIA") {showPage("Nagrywaj")})
    }
    private fun time(millis:Long):String {
        val s=(millis/1000).coerceAtLeast(0)
        return if(s>=3600)String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60)
            else String.format(Locale.ROOT,"%02d:%02d",s/60,s%60)
    }
    private fun size(bytes:Long):String=String.format(Locale("pl"),"%.2f MB",bytes/1048576.0)
}