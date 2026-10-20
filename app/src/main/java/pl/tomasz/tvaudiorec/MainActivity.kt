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
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.widget.ImageView
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
    private val dark=Color.BLACK
    private val surface=Color.rgb(13,14,17)
    private val accent=Color.rgb(255,35,61)
    private val textColor=Color.rgb(248,249,251)
    private val subtle=Color.rgb(166,173,183)
    private val vintageBorder=Color.rgb(49,54,62)
    private val vintageDim=Color.rgb(21,23,27)
    private val uiScale:Float by lazy {
        when(prefs.getString("ui_size","M")) {
            "S"->0.84f
            "L"->1.00f
            else->0.92f
        }
    }
    private val uiFont:Typeface by lazy {
        try {Typeface.createFromAsset(assets,"Manrope.ttf")}
        catch(_:Exception) {Typeface.create("sans-serif",Typeface.NORMAL)}
    }
    private lateinit var rightPanel:LinearLayout
    private var selectedEntry:AudioEntry?=null
    private var multiSelect=false
    private val marked=linkedSetOf<String>()
    private val prefs by lazy { getSharedPreferences("record_settings",MODE_PRIVATE) }
    private lateinit var root:LinearLayout
    private lateinit var panel:LinearLayout
    private lateinit var status:TextView
    private var timer:TextView?=null
    private var retroMeter:RetroVuMeter?=null
    private val navButtons=linkedMapOf<String,Button>()
    private var waveform:WaveformView?=null
    private var clock:TextView?=null
    private var playSeek:SeekBar?=null
    private var player:MediaPlayer?=null
    private var playing:AudioEntry?=null
    private var playButton:Button?=null
    private var loop=false
    private var page="Nagrywaj"
    private var lastRecording=false
    private var lastLibraryRevision=0L
    private var latestScan:RecordingLibrary.Scan?=null
    private val history=ArrayDeque<String>()
    private var exitDialogShowing=false
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable {
        override fun run() {
            if(::status.isInitialized) {
                if(RecordingService.isRecording) status.text="Trwa nagrywanie..."
                else if(lastRecording) status.text=RecordingService.lastResult.ifEmpty {
                    "Nagrywanie zatrzymane."
                }
            }
            timer?.text=if(RecordingService.isRecording)
                time((System.currentTimeMillis()-RecordingService.startedAt).coerceAtLeast(0))
                else "00:00"
            if(RecordingService.isRecording) waveform?.liveLevel=RecordingService.currentLevel
            retroMeter?.level=if(RecordingService.isRecording)RecordingService.currentLevel else 0
            val mp=player
            if(mp!=null) try {
                if(mp.duration>0) {
                    playSeek?.progress=(mp.currentPosition.toLong()*1000L/mp.duration).toInt()
                    waveform?.progress=mp.currentPosition.toFloat()/mp.duration
                    clock?.text=time(mp.currentPosition.toLong())
                }
            }catch (_:Exception){}
            if(lastRecording && !RecordingService.isRecording && page=="Nagrywaj")
                showPage("Nagrywaj",false)
            val changed=prefs.getLong("library_revision",0L)
            if(changed!=lastLibraryRevision){
                lastLibraryRevision=changed
                if(page in listOf("Nagrania","Ulubione","Kosz","Diagnostyka","Nagrywaj","Odtwarzacz"))
                    showPage(page,false)
            }
            lastRecording=RecordingService.isRecording
            handler.postDelayed(this,500)
        }
    }
    data class AudioEntry(val uri:Uri,val name:String,val bytes:Long,val added:Long)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(dark)
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(6),dp(10),dp(6))
            setBackgroundColor(dark)
        }
        setContentView(root)

        // Always visible horizontal header and navigation - no off-screen menu.
        val header=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val logo=ImageView(this).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            contentDescription="AudioRelay Grabber"
            try {
                assets.open("grabber_wordmark.webp").use {
                    setImageBitmap(BitmapFactory.decodeStream(it))
                }
            }catch (_:Exception){setImageResource(android.R.drawable.ic_btn_speak_now)}
        }
        header.addView(logo,LinearLayout.LayoutParams(dp(216),dp(55)))
        val heading=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),0,0,0)
        }
        heading.addView(label("REJESTRATOR DŹWIĘKU",19,textColor))
        heading.addView(label("AMOLED BLACK  •  v1.9  •  Manrope",10,subtle))
        header.addView(heading,LinearLayout.LayoutParams(0,dp(57),1f))
        header.addView(btn("Aa  S / M / L") {showSizeSelector()},
            LinearLayout.LayoutParams(dp(112),dp(38)))
        root.addView(header,LinearLayout.LayoutParams(-1,dp(59)))

        val tabs=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val menu=listOf(
            "Nagrywaj" to "●  NAGRYWAJ",
            "Nagrania" to "≡  MOJE NAGRANIA",
            "Odtwarzacz" to "▶  ODTWARZACZ",
            "Ustawienia" to "⚙  USTAWIENIA",
            "Diagnostyka" to "◇  DIAGNOSTYKA",
            "Kosz" to "▢  KOSZ"
        )
        for((target,title) in menu) {
            val button=btn(title) {
                if(target=="Odtwarzacz") {
                    val entry=selectedEntry
                    if(entry!=null)showPage("Odtwarzacz")
                    else showPage("Nagrania")
                } else showPage(target)
            }
            button.tag="nav:"+target
            navButtons[target]=button
            tabs.addView(button,LinearLayout.LayoutParams(0,dp(41),
                if(target=="Nagrania")1.25f else if(target=="Diagnostyka")1.12f else 1.0f).apply {
                setMargins(dp(2),0,dp(2),0)
            })
        }
        root.addView(tabs,LinearLayout.LayoutParams(-1,dp(45)))

        val workspace=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(0,dp(6),0,0)
        }
        root.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        val leftScroll=ScrollView(this).apply {
            isFillViewport=false
            background=rounded(surface,vintageBorder)
            isVerticalScrollBarEnabled=false
        }
        panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(7),dp(5),dp(12),dp(4))
        }
        leftScroll.addView(panel)
        workspace.addView(leftScroll,LinearLayout.LayoutParams(0,-1,1.03f).apply{
            setMargins(dp(3),0,dp(5),0)
        })

        val rightScroll=ScrollView(this).apply {
            isFillViewport=false
            background=rounded(surface,vintageBorder)
            isVerticalScrollBarEnabled=false
        }
        rightPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(9),dp(5),dp(7),dp(4))
        }
        rightScroll.addView(rightPanel)
        workspace.addView(rightScroll,LinearLayout.LayoutParams(0,-1,0.97f).apply{
            setMargins(dp(5),0,dp(3),0)
        })

        status=label("Gotowy do nagrywania",11,subtle)
        root.addView(status,LinearLayout.LayoutParams(-1,dp(20)))
        page="Nagrywaj"
        showPage(savedInstanceState?.getString("page") ?: "Nagrywaj",false)
        lastLibraryRevision=prefs.getLong("library_revision",0L)
        handler.post(ticker)
    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putString("page",page)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release();player=null
        super.onDestroy()
    }
    private fun dp(v:Int):Int=(resources.displayMetrics.density*v*uiScale).toInt().coerceAtLeast(1)
    private fun label(value:String,size:Int=18,color:Int=textColor):TextView=TextView(this).apply {
        text=value;textSize=size.toFloat()*uiScale;setTextColor(color)
        typeface=uiFont
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(2),dp(2),dp(2),dp(2))
    }
    private fun space(h:Int):View=View(this).apply {minimumHeight=dp(h)}
    private fun btn(value:String,click:()->Unit):Button=Button(this).apply {
        text=value
        textSize=12.5f*uiScale
        typeface=uiFont
        isAllCaps=false
        isFocusable=true
        setSingleLine(true)
        ellipsize=android.text.TextUtils.TruncateAt.END
        setTextColor(textColor)
        setPadding(dp(6),dp(3),dp(6),dp(3))
        background=rounded(surface,vintageBorder)
        setOnFocusChangeListener { view,hasFocus ->
            val nav=(view.tag as? String)?.removePrefix("nav:")
            val active=nav!=null && nav==page
            view.background=rounded(
                if(hasFocus)Color.rgb(96,17,37)
                else if(active)Color.rgb(44,13,23)
                else surface,
                if(hasFocus || active)accent else vintageBorder)
            (view as Button).setTextColor(if(hasFocus)Color.WHITE else textColor)
        }
        setOnClickListener {click()}
    }
    private fun rounded(color:Int,stroke:Int=vintageBorder):GradientDrawable=
        GradientDrawable().apply {
            setColor(color);cornerRadius=dp(12).toFloat()
            setStroke(dp(1),stroke)
        }
    private fun line(title:String,sub:String?=null) {
        panel.addView(label(title,23,textColor).apply{typeface=Typeface.create(uiFont,Typeface.BOLD)})
        if(sub!=null)panel.addView(label(sub,12,subtle))
        panel.addView(space(5))
    }
    private fun options(title:String,labels:List<Pair<String,String>>,chosen:String,
                        enabled:(String)->Boolean={true},onChange:(String)->Unit) {
        panel.addView(label(title,20))
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        for((display,key) in labels) {
            val b=btn((if(key==chosen)"✓  " else "")+display) {onChange(key)}
            b.isEnabled=enabled(key)
            if(!b.isEnabled)b.alpha=0.35f
            b.background=rounded(if(key==chosen)Color.rgb(52,15,28) else surface, vintageBorder)
            row.addView(b,LinearLayout.LayoutParams(0,dp(63),1f).apply {
                setMargins(dp(3),dp(3),dp(3),dp(3))
            })
        }
        panel.addView(row)
        panel.addView(space(10))
    }
    private fun showPage(target:String, saveHistory:Boolean=true) {
        if(saveHistory && target!=page && page!="Odtwarzacz")
            history.addLast(page)
        if(target!=page && page=="Odtwarzacz")
            player?.pause()
        page=target
        panel.removeAllViews()
        timer=null;retroMeter=null;waveform=null;clock=null;playSeek=null;playButton=null
        for((target,button) in navButtons){
            val active=target==page
            button.background=rounded(if(active)Color.rgb(52,15,28) else surface,
                if(active)accent else vintageBorder)
        }
        when(target) {
            "Nagrywaj"->recordPage()
            "Nagrania"->filesPage(false,false)
            "Odtwarzacz"->playerOverview()
            "Ulubione"->filesPage(false,true)
            "Kosz"->filesPage(true,false)
            "Ustawienia"->settingsPage()
            "Diagnostyka"->diagnosticsPage()
        }
        if(target in listOf("Nagrania","Ulubione","Kosz","Nagrywaj","Odtwarzacz") && selectedEntry==null) {
            val items=RecordingLibrary.scan(this,false).items
            if(items.isNotEmpty()) {
                val first=items.first()
                selectedEntry=AudioEntry(first.uri,first.name,first.size,first.time)
            }
        }
        renderRight(selectedEntry)
    }
    private fun previousScreen() {
        if(page=="Odtwarzacz")player?.pause()
        if(history.isNotEmpty()) {
            val previous=history.removeLast()
            if(previous=="Odtwarzacz") {
                val audio=playing
                if(audio!=null)showPlayer(audio,false)
                else showPage("Nagrania",false)
            } else showPage(previous,false)
        } else if(page!="Nagrywaj") {
            showPage("Nagrywaj",false)
        } else confirmExit()
    }

    @Deprecated("Back navigation for Android TV remote")
    override fun onBackPressed() {
        previousScreen()
    }

    private fun confirmExit() {
        if(exitDialogShowing)return
        exitDialogShowing=true
        val recording=RecordingService.isRecording
        val dialog=AlertDialog.Builder(this)
            .setTitle("Zamknąć AudioRelay Grabber?")
            .setMessage(if(recording)
                "Nagrywanie trwa. Po zamknięciu okna będzie działało w tle. " +
                    "Możesz również zatrzymać i zapisać nagranie."
                else "Czy na pewno chcesz zamknąć aplikację?")
            .setNegativeButton("ANULUJ",null)
            .setPositiveButton(if(recording)"WYJDŹ, NAGRYWAJ W TLE" else "ZAMKNIJ") { _,_ ->
                finish()
            }
            .apply {
                if(recording)setNeutralButton("STOP, ZAPISZ I WYJDŹ") { _,_->
                    stopRecording()
                    finish()
                }
            }
            .create()
        dialog.setOnDismissListener {exitDialogShowing=false}
        dialog.show()
    }

    private fun compactChoice(
        row:LinearLayout,title:String,value:String,selected:String,
        options:List<Pair<String,String>>,onSelect:(String)->Unit
    ) {
        val block=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(3),dp(2),dp(3),dp(2))
        }
        block.addView(label(title,11,subtle),
            LinearLayout.LayoutParams(-1,dp(20)))
        block.addView(btn("▾  "+value) {
            val idx=options.indexOfFirst{it.second==selected}.coerceAtLeast(0)
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(options.map{it.first}.toTypedArray(),idx) {dialog,which->
                    val next=options[which].second
                    if(title=="Format" && next=="MP3" && !Mp3Native.available())
                        status.text="MP3 niedostępny na TV"
                    else if(title=="Format" && next=="OPUS" && !OpusNative.available())
                        status.text="Opus niedostępny w tej instalacji"
                    else {
                        onSelect(next)
                        showPage(page,false)
                    }
                    dialog.dismiss()
                }.setNegativeButton("ANULUJ",null).show()
        },LinearLayout.LayoutParams(-1,dp(35)))
        row.addView(block,LinearLayout.LayoutParams(0,dp(61),1f))
    }

    private fun recordPage() {
        line("REJESTRATOR DŹWIĘKU","Źródło, format, kHz i kanały — bez przewijania")
        val choices=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        val source=prefs.getString("source","Internal") ?: "Internal"
        compactChoice(choices,"Źródło",sourceName(),source,listOf(
            "Dźwięk wewnętrzny" to "Internal",
            "Mikrofon TV" to "Microphone",
            "Wewnętrzny + mikrofon" to "Internal + Mic",
            "Wewnętrzny + Bluetooth" to "Internal + Bluetooth"
        )) {prefs.edit().putString("source",it).commit()}
        val fmt=prefs.getString("format","M4A") ?: "M4A"
        compactChoice(choices,"Format",fmt,fmt,listOf(
            "M4A — AAC" to "M4A",
            "OPUS — mały plik" to "OPUS",
            "MP3 — LAME" to "MP3",
            "MP4 — tylko audio" to "MP4",
            "FLAC — bezstratnie" to "FLAC",
            "WAV — PCM" to "WAV"
        )) {prefs.edit().putString("format",it).commit()}
        val rate=prefs.getInt("rate",48000)
        compactChoice(choices,"Częstotliwość",
            if(fmt=="OPUS")"48 kHz" else if(rate==44100)"44,1 kHz" else (rate/1000).toString()+" kHz",
            rate.toString(),listOf(
                "16 kHz" to "16000","32 kHz" to "32000",
                "44,1 kHz" to "44100","48 kHz" to "48000"
            )) {prefs.edit().putInt("rate",it.toInt()).commit()}
        val channels=prefs.getInt("channels",2)
        compactChoice(choices,"Kanały",if(channels==2)"Stereo" else "Mono",
            channels.toString(),listOf("Mono" to "1","Stereo" to "2")){
            prefs.edit().putInt("channels",it.toInt()).commit()
        }
        panel.addView(choices)
        panel.addView(space(5))
        panel.addView(btn("⚙  USTAWIENIA I JAKOŚĆ NAGRANIA") {
            showPage("Ustawienia")
        },LinearLayout.LayoutParams(-1,dp(39)))
        panel.addView(space(8))
        val clockFrame=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            setPadding(dp(8),dp(5),dp(8),dp(5))
        }
        timer=label("00:00",53,accent).also {
            it.gravity=Gravity.CENTER
            it.typeface=Typeface.create(uiFont,Typeface.BOLD)
            it.background=rounded(Color.rgb(18,20,24),vintageBorder)
            clockFrame.addView(it,LinearLayout.LayoutParams(dp(276),dp(75)))
        }
        panel.addView(clockFrame,LinearLayout.LayoutParams(-1,dp(86)))
        panel.addView(label(if(RecordingService.isRecording)"●  TRWA NAGRYWANIE"
            else "GOTOWY DO NAGRYWANIA",11,subtle).apply {
                gravity=Gravity.CENTER
        },LinearLayout.LayoutParams(-1,dp(19)))
        panel.addView(space(7))
        retroMeter=RetroVuMeter(this).also {
            panel.addView(it,LinearLayout.LayoutParams(-1,dp(83)))
        }
        panel.addView(space(8))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        actions.addView(btn("●  ROZPOCZNIJ NAGRYWANIE"){startRecording()},
            LinearLayout.LayoutParams(0,dp(51),1.15f).apply {
                setMargins(0,0,dp(6),0)
            })
        actions.addView(btn("■  STOP I ZAPISZ"){stopRecording()},
            LinearLayout.LayoutParams(0,dp(51),0.9f))
        panel.addView(actions)
        panel.addView(space(6))
        panel.addView(btn("⌂  Wróć do telewizji — nagrywaj w tle") {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        },LinearLayout.LayoutParams(-1,dp(35)))
    }
    private fun playerOverview() {
        line("ODTWARZACZ","Wybierz plik z listy po prawej stronie")
        panel.addView(btn("☷  Otwórz pełną bibliotekę nagrań"){
            showPage("Nagrania")
        },LinearLayout.LayoutParams(-1,dp(42)))
        panel.addView(label("Sterowanie odtwarzaniem i przebieg dźwięku są po prawej.",14,subtle))
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
        if(prefs.getString("format","M4A")=="MP3" && !Mp3Native.available()) {
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
    private fun hasAudioPermission():Boolean {
        val permission=if(Build.VERSION.SDK_INT>=33)"android.permission.READ_MEDIA_AUDIO"
            else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED
    }
    override fun onRequestPermissionsResult(
        requestCode:Int,permissions:Array<out String>,grantResults:IntArray
    ) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==733){
            status.text=if(hasAudioPermission())
                "Przyznano dostęp do starszych nagrań. Lista została odświeżona."
                else "Brak dostępu do cudzych nagrań; własne pliki nadal są widoczne."
            if(page in listOf("Nagrania","Ulubione","Kosz"))
                showPage(page,false)
        }
    }
    private fun requestAudioPermission() {
        val permission=if(Build.VERSION.SDK_INT>=33)"android.permission.READ_MEDIA_AUDIO"
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if(!hasAudioPermission())requestPermissions(arrayOf(permission),733)
    }
    private fun getEntries():List<AudioEntry> {
        val scanned=RecordingLibrary.scan(this,allAudio)
        latestScan=scanned
        return scanned.items.map{
            AudioEntry(it.uri,it.name,it.size,it.time)
        }
    }
    private fun importAudio(uri:Uri) {
        val sourceName=try {
            contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,null,null)?.use {c->
                    if(c.moveToFirst())c.getString(0)else null
                }
        }catch(_:Exception){null}
        val clean=(sourceName ?: "Import_"+System.currentTimeMillis()+".m4a")
            .replace(Regex("[/\\\\:*?\"<>|]"),"_").take(100)
        val extension=clean.substringAfterLast(".","m4a").lowercase()
        if(extension !in setOf("mp3","m4a","mp4","wav","flac","aac","ogg","opus")) {
            status.text="Nieobsługiwany format: "+clean
            return
        }
        val mime=when(extension) {
            "mp3"->"audio/mpeg";"m4a","mp4"->"audio/mp4"
            "wav"->"audio/wav";"flac"->"audio/flac";"ogg","opus"->"audio/ogg"
            else->"audio/aac"
        }
        var saved:Uri?=null
        try {
            val values=ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,clean)
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Music/TVAudioRecorder/")
                put(MediaStore.MediaColumns.MIME_TYPE,mime)
                put(MediaStore.MediaColumns.IS_PENDING,1)
            }
            saved=contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values)
                ?:error("Nie można zapisać pliku")
            val output=saved ?: error("Nie udało się utworzyć pliku")
            contentResolver.openInputStream(uri).use {input->
                checkNotNull(input){"Nie można odczytać nagrania"}
                contentResolver.openOutputStream(output,"w").use {dest->
                    checkNotNull(dest){"Pamięć TV niedostępna"}
                    input.copyTo(dest)
                }
            }
            contentResolver.update(output,ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING,0)
            },null,null)
            var importedSize=0L
            try {
                contentResolver.query(output,arrayOf(MediaStore.MediaColumns.SIZE),
                    null,null,null)?.use { c->
                    if(c.moveToFirst())importedSize=c.getLong(0)
                }
            }catch (_:Exception){}
            RecordingIndex.save(this,output,clean,importedSize,
                System.currentTimeMillis()/1000L)
            allAudio=false
            showPage("Nagrania")
            status.text="Zaimportowano: "+clean
        }catch(e:Exception) {
            if(saved!=null)try{contentResolver.delete(saved,null,null)}catch(_:Exception){}
            status.text="Import nieudany: "+e.message
        }
    }
    private fun pickImport() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type="audio/*"
            },importCode)
        }catch(e:Exception){
            status.text="TV nie ma przeglądarki plików: "+e.message
        }
    }
    private fun trashed(name:String)=prefs.getBoolean("trash."+name,false)
    private fun favorite(name:String)=prefs.getBoolean("favorite."+name,false)
    private fun filesPage(trash:Boolean,onlyFavorite:Boolean) {
        val heading=if(trash)"KOSZ" else if(onlyFavorite)"ULUBIONE" else "MOJE NAGRANIA"
        line(heading,"Pliki nagrane na tym telewizorze")
        if(!trash && !hasAudioPermission() &&
            !prefs.getBoolean("asked_audio_permission",false)){
            prefs.edit().putBoolean("asked_audio_permission",true).apply()
            panel.post {requestAudioPermission()}
        }

        val entries=getEntries().filter {
            trashed(it.name)==trash && (!onlyFavorite || favorite(it.name))
        }.let{if(prefs.getBoolean("newest",true))it else it.reversed()}

        panel.addView(label("Znaleziono: "+entries.size+" nagrań",17,textColor))
        val controls=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        controls.addView(btn(if(multiSelect)"ANULUJ ZAZNACZANIE" else "☑  ZAZNACZ WIELE"){
            multiSelect=!multiSelect
            marked.clear()
            showPage(page,false)
        },LinearLayout.LayoutParams(0,dp(39),1f))
        if(multiSelect) {
            controls.addView(btn("WSZYSTKIE") {
                marked.clear()
                marked.addAll(entries.map{it.uri.toString()})
                showPage(page,false)
            },LinearLayout.LayoutParams(0,dp(39),0.7f))
        }
        panel.addView(controls)
        if(multiSelect) {
            panel.addView(btn("☒  "+if(trash)"USUŃ TRWALE" else "DO KOSZA"+
                "  ("+marked.size+")") {confirmBatchDelete(entries,trash)},
                LinearLayout.LayoutParams(-1,dp(39)))
            panel.addView(label("Zaznaczono: "+marked.size+" z "+entries.size,12,subtle))
        }
        if(entries.isEmpty()){
            panel.addView(label("Brak nagrań. Użyj TESTU ZAPISU poniżej, "+
                "aby sprawdzić, czy telewizor zapisuje i wyświetla pliki.",17,subtle))
        }else{
            var group=""
            for(entry in entries.take(250)){
                val date=SimpleDateFormat("dd.MM.yyyy",Locale("pl"))
                    .format(Date(entry.added*1000))
                if(date!=group) {
                    group=date
                    panel.addView(label(date,16,subtle))
                }
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                if(multiSelect)row.addView(btn(
                    if(marked.contains(entry.uri.toString()))"☑" else "☐") {
                    if(!marked.add(entry.uri.toString()))marked.remove(entry.uri.toString())
                    showPage(page,false)
                },LinearLayout.LayoutParams(dp(38),dp(42)))
                row.addView(btn(entry.name+"  •  "+size(entry.bytes)) {
                    if(multiSelect) {
                        if(!marked.add(entry.uri.toString()))marked.remove(entry.uri.toString())
                        showPage(page,false)
                    }else if(trash)showTrashActions(entry) else showPlayer(entry)
                },LinearLayout.LayoutParams(0,dp(42),1f))
                if(!trash && !multiSelect){
                    row.addView(btn(if(favorite(entry.name))"★" else "☆") {
                        prefs.edit().putBoolean("favorite."+entry.name,
                            !favorite(entry.name)).apply()
                        showPage(page,false)
                    },LinearLayout.LayoutParams(dp(38),dp(42)))
                    row.addView(btn("⋮"){showFileActions(entry)},
                        LinearLayout.LayoutParams(dp(62),dp(64)))
                } else if(trash && !multiSelect) {
                    row.addView(btn("PRZYWRÓĆ"){
                        prefs.edit().putBoolean("trash."+entry.name,false).apply()
                        showPage("Kosz",false)
                    },LinearLayout.LayoutParams(dp(77),dp(42)))
                }
                panel.addView(row)
            }
            if(entries.size>250)panel.addView(label("Pokazano 250 najnowszych plików.",16,subtle))
        }
        panel.addView(space(10))
        panel.addView(btn("↻  ODŚWIEŻ LISTĘ NAGRAŃ"){showPage(page,false)})
        if(!trash){
            panel.addView(btn("✓  TEST ZAPISU I LISTY (1 SEKUNDA)") {
                try {
                    val result=RecordingLibrary.testFile(this)
                    showPage("Nagrania",false)
                    status.text=result
                }catch(e:Exception){
                    status.text="Test zapisu nieudany: "+e.message
                    showPage("Diagnostyka")
                }
            })
            panel.addView(btn(if(allAudio)
                "♫  POKAŻ TYLKO NAGRANIA Z REJESTRATORA"
                else "♫  POKAŻ CAŁĄ BIBLIOTEKĘ AUDIO TV"){
                allAudio=!allAudio
                if(allAudio && !hasAudioPermission())requestAudioPermission()
                showPage(page,false)
            })
            panel.addView(btn("＋  IMPORTUJ NAGRANIE Z INNEJ APLIKACJI"){pickImport()})
            panel.addView(btn("⌕  DIAGNOSTYKA PLIKÓW"){showPage("Diagnostyka")})
            if(!hasAudioPermission())
                panel.addView(btn("🔓  PRZYZNAJ DOSTĘP DO STARSZYCH NAGRAŃ"){
                    requestAudioPermission()
                })
            panel.addView(btn("↕  ZMIEŃ SORTOWANIE"){
                prefs.edit().putBoolean("newest",
                    !prefs.getBoolean("newest",true)).apply()
                showPage(page,false)
            })
        }
    }

    private fun diagnosticsPage() {
        line("DIAGNOSTYKA NAGRAŃ","Sprawdzenie tego, co rzeczywiście widzi telewizor")
        val scan=RecordingLibrary.scan(this,true)
        val lastUri=prefs.getString("last_saved_uri","brak")
        val lastName=prefs.getString("last_saved_name","brak")
        val path=prefs.getString("last_output_path","brak")
        panel.addView(label("Widziane pliki: "+scan.items.size,23,accent))
        panel.addView(label("Własny katalog zapisów: "+scan.indexed,20))
        panel.addView(label("Identyfikator tej instalacji: "+packageName,15,subtle))
        panel.addView(label("Dostęp do cudzych nagrań: "+
            if(hasAudioPermission())"PRZYZNANY" else "BRAK",17))
        panel.addView(label("Ostatnie nagranie: "+lastName,16))
        panel.addView(label("Ostatnia ścieżka: "+path,15,subtle))
        panel.addView(label("Ostatni adres URI: "+lastUri,14,subtle))
        panel.addView(label("Ostatni wynik: "+
            (prefs.getString("last_recording_result","brak") ?: "brak"),16))
        panel.addView(label("Ostatni plik udostępniony w MediaStore: "+
            if(prefs.getBoolean("last_saved_published",false))"TAK" else "NIE / BRAK",16))
        panel.addView(label("---- ŹRÓDŁA PLIKÓW ----",20,accent))
        for((key,value) in scan.counts)
            panel.addView(label(key+": "+value,15))
        panel.addView(label("---- SPRAWDZANE FOLDERY ----",20,accent))
        for(p in scan.paths)
            panel.addView(label(p,14,subtle))
        if(scan.errors.isNotEmpty()) {
            panel.addView(label("---- BŁĘDY ODCZYTU ----",20,accent))
            for(e in scan.errors)panel.addView(label(e,15))
        }
        panel.addView(btn("✓  WYKONAJ TEST PLIKU WAV") {
            try {
                val result=RecordingLibrary.testFile(this)
                showPage("Diagnostyka",false)
                status.text=result
            }catch(e:Exception) {
                status.text="Test nieudany: "+e.javaClass.simpleName+" "+e.message
            }
        })
        panel.addView(btn("↻  PONÓW DIAGNOSTYKĘ"){showPage("Diagnostyka",false)})
        panel.addView(btn("≡  PRZEJDŹ DO MOICH NAGRAŃ"){showPage("Nagrania")})
        panel.addView(label("Nagrania wykonane przez inne wersje aplikacji "+
            "mogą znajdować się w odrębnym, prywatnym katalogu Android/data. "+
            "Wtedy użyj funkcji importu w Moich nagraniach.",14,subtle))
    }
    private fun confirmBatchDelete(entries:List<AudioEntry>,permanent:Boolean) {
        val chosen=entries.filter{marked.contains(it.uri.toString())}
        if(chosen.isEmpty()){
            status.text="Nie zaznaczono żadnych plików."
            return
        }
        AlertDialog.Builder(this)
            .setTitle(if(permanent)"Usunąć na stałe "+chosen.size+" nagrań?"
                else "Przenieść do kosza "+chosen.size+" nagrań?")
            .setMessage(if(permanent)
                "Tych plików nie będzie można odzyskać."
                else "Pliki zostaną ukryte na liście, a później można je przywrócić z Kosza.")
            .setNegativeButton("ANULUJ",null)
            .setPositiveButton(if(permanent)"USUŃ TRWALE" else "DO KOSZA") {_,_->
                var successes=0
                var failures=0
                val editor=prefs.edit()
                for(entry in chosen) {
                    try {
                        if(permanent){
                            val removed=if(entry.uri.scheme=="file") {
                                val path=entry.uri.path ?:error("Brak ścieżki")
                                File(path).delete()
                            }else contentResolver.delete(entry.uri,null,null)>0
                            check(removed){"Android odmówił usunięcia pliku"}
                            RecordingIndex.delete(this,entry.uri)
                            editor.remove("trash."+entry.name)
                                .remove("favorite."+entry.name)
                                .remove("wave."+entry.name)
                        } else editor.putBoolean("trash."+entry.name,true)
                        successes++
                    }catch(e:Exception) {
                        failures++
                        android.util.Log.e("AudioRelayRecorder","Bulk remove failed",e)
                    }
                }
                editor.apply()
                marked.clear()
                multiSelect=false
                showPage(page,false)
                status.text=if(failures==0)"Przetworzono "+successes+" plików."
                    else "Przetworzono "+successes+"; błędy: "+failures+
                        ". Sprawdź uprawnienia do plików."
            }.show()
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
                            if(entry.uri.scheme=="file") {
                                val path=entry.uri.path ?: error("Brak ścieżki pliku")
                                check(File(path).delete()) {"Nie udało się usunąć nagrania"}
                            }else check(contentResolver.delete(entry.uri,null,null)>0) {
                                "Nie udało się usunąć nagrania"
                            }
                            RecordingIndex.delete(this,entry.uri)
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
                        RecordingIndex.delete(this,entry.uri)
                        RecordingIndex.save(this,entry.uri,newName,entry.bytes,entry.added)
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
    private fun showSizeSelector() {
        val choices=arrayOf("S — 84% (większa niż poprzednia S)",
            "M — 92% (polecana)", "L — 100% (mniejsza niż poprzednia L)")
        val values=arrayOf("S","M","L")
        val current=values.indexOf(prefs.getString("ui_size","M")).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Wielkość menu Retro — Manrope")
            .setSingleChoiceItems(choices,current) {dialog,index->
                prefs.edit().putString("ui_size",values[index]).commit()
                dialog.dismiss()
                recreate()
            }.setNegativeButton("ANULUJ",null).show()
    }

    private fun quickField(
        container:LinearLayout,title:String,current:String,choices:List<Pair<String,String>>,
        selected:String,onChange:(String)->Unit
    ) {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        row.addView(label(title,12,subtle),LinearLayout.LayoutParams(0,dp(35),1.0f))
        row.addView(btn("▾  "+current) {
            val idx=choices.indexOfFirst{it.second==selected}.coerceAtLeast(0)
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(choices.map{it.first}.toTypedArray(),idx) {dialog,which->
                    val value=choices[which].second
                    if(title=="Format" && value=="MP3" && !Mp3Native.available()) {
                        status.text="Brak biblioteki LAME MP3"
                    }else onChange(value)
                    dialog.dismiss()
                    showPage(page,false)
                }.setNegativeButton("ANULUJ",null).show()
        },LinearLayout.LayoutParams(0,dp(35),1.2f))
        container.addView(row)
    }

    private fun quickSettings() {
        rightPanel.addView(space(8))
        rightPanel.addView(label("SZYBKIE USTAWIENIA NAGRYWANIA",15,textColor))
        val src=prefs.getString("source","Internal") ?: "Internal"
        quickField(rightPanel,"Źródło",sourceName(),listOf(
            "Wewnętrzny" to "Internal","Mikrofon" to "Microphone",
            "Wewnętrzny + Mic" to "Internal + Mic",
            "Wewnętrzny + Bluetooth" to "Internal + Bluetooth"
        ),src){prefs.edit().putString("source",it).commit()}
        val format=prefs.getString("format","M4A") ?: "M4A"
        quickField(rightPanel,"Format",format,listOf(
            "OPUS — oszczędny" to "OPUS","MP3" to "MP3","M4A" to "M4A",
            "MP4 (tylko audio)" to "MP4","FLAC" to "FLAC","WAV" to "WAV"
        ),format){prefs.edit().putString("format",it).commit()}
        if(format=="OPUS"){
            val quality=prefs.getInt("opus_bitrate",96000).toString()
            quickField(rightPanel,"Opus kb/s",(quality.toInt()/1000).toString()+" kb/s",
                listOf("48 kb/s — mniejszy plik" to "48000",
                    "64 kb/s — bardzo oszczędny" to "64000",
                    "96 kb/s — polecany" to "96000",
                    "128 kb/s — wyższa jakość" to "128000"),quality){
                prefs.edit().putInt("opus_bitrate",it.toInt()).commit()
            }
        }
        val rate=prefs.getInt("rate",48000).toString()
        quickField(rightPanel,"kHz",when(rate){
            "44100"->"44,1 kHz"
            else->(rate.toInt()/1000).toString()+" kHz"
        },listOf(
            "8 kHz" to "8000","16 kHz" to "16000","22,05 kHz" to "22050",
            "32 kHz" to "32000","44,1 kHz" to "44100","48 kHz" to "48000"
        ),rate){prefs.edit().putInt("rate",it.toInt()).commit()}
        val count=prefs.getInt("channels",2).toString()
        quickField(rightPanel,"Kanały",if(count=="1")"Mono" else "Stereo",
            listOf("Mono" to "1","Stereo" to "2"),count){
            prefs.edit().putInt("channels",it.toInt()).commit()
        }
        rightPanel.addView(btn(if(RecordingService.isRecording)
            "■  STOP — ZAPISZ NAGRANIE"
            else "●  REC — ROZPOCZNIJ NAGRYWANIE") {
            if(RecordingService.isRecording)stopRecording() else startRecording()
        },LinearLayout.LayoutParams(-1,dp(43)))
        rightPanel.addView(label(
            if(RecordingService.isRecording)"TRWA NAGRYWANIE"
            else "NAGRYWANIE WEWNĘTRZNE / MIKROFON",10,accent))
    }

    private fun renderRight(entry:AudioEntry?) {
        if(!::rightPanel.isInitialized)return
        rightPanel.removeAllViews()
        val dashboard=page=="Nagrywaj" || page=="Odtwarzacz"
        val eligible=if(dashboard)getEntries().filter{!trashed(it.name)} else emptyList()
        var item=entry
        if(item==null && eligible.isNotEmpty()){
            val first=eligible.first()
            selectedEntry=first
            item=first
        }

        if(dashboard){
            val heading=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
            }
            heading.addView(label("MOJE NAGRANIA",19,textColor),
                LinearLayout.LayoutParams(0,dp(32),1f))
            heading.addView(btn(eligible.size.toString()+" PLIKÓW  ›"){
                showPage("Nagrania")
            },LinearLayout.LayoutParams(dp(118),dp(32)))
            rightPanel.addView(heading)
            if(eligible.isEmpty()){
                rightPanel.addView(label("Brak nagrań — rozpocznij pierwsze nagranie.",12,subtle),
                    LinearLayout.LayoutParams(-1,dp(43)))
            } else {
                for(record in eligible.take(5)){
                    val row=LinearLayout(this).apply{
                        orientation=LinearLayout.HORIZONTAL
                        gravity=Gravity.CENTER_VERTICAL
                    }
                    val current=selectedEntry?.uri==record.uri
                    val short=if(record.name.length>34)record.name.take(31)+"…"
                        else record.name
                    val pick=btn("♫  "+short){
                        selectedEntry=record
                        renderRight(record)
                    }
                    if(current)pick.background=rounded(Color.rgb(52,15,28),accent)
                    row.addView(pick,LinearLayout.LayoutParams(0,dp(37),1f))
                    row.addView(btn("▶"){togglePlay(record)},
                        LinearLayout.LayoutParams(dp(43),dp(37)))
                    rightPanel.addView(row)
                }
            }
            rightPanel.addView(space(8))
        }

        rightPanel.addView(label("ODTWARZACZ",18,textColor).apply{
            typeface=Typeface.create(uiFont,Typeface.BOLD)
        },
            LinearLayout.LayoutParams(-1,dp(29)))
        if(item==null){
            rightPanel.addView(label("Wybierz nagranie z listy.",12,subtle))
            val graphic=WaveformView(this).apply{progress=0f}
            rightPanel.addView(graphic,LinearLayout.LayoutParams(-1,dp(63)))
        } else {
            val selected=item
            selectedEntry=selected
            if(playing?.uri!=null && playing?.uri!=selected.uri){
                player?.release();player=null;playing=null
            }
            rightPanel.addView(label(selected.name,13,textColor).apply{
                maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
            },LinearLayout.LayoutParams(-1,dp(25)))

            waveform=WaveformView(this).apply{
                peaks=prefs.getString("wave."+selected.name,"").orEmpty()
                    .split(",").mapNotNull{it.toIntOrNull()}
            }
            rightPanel.addView(waveform,LinearLayout.LayoutParams(-1,dp(66)))
            val seekRow=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
            }
            clock=label("00:00",11,subtle)
            seekRow.addView(clock,LinearLayout.LayoutParams(dp(47),dp(29)))
            playSeek=SeekBar(this).apply{max=1000}
            seekRow.addView(playSeek,LinearLayout.LayoutParams(0,dp(29),1f))
            seekRow.addView(label("♫",12,subtle),LinearLayout.LayoutParams(dp(22),dp(29)))
            rightPanel.addView(seekRow)
            playSeek?.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek:SeekBar?,value:Int,fromUser:Boolean){
                    if(fromUser)try{
                        player?.let{if(it.duration>0)
                            it.seekTo((it.duration.toLong()*value/1000).toInt())}
                    }catch (_:Exception){}
                }
                override fun onStartTrackingTouch(seek:SeekBar?){}
                override fun onStopTrackingTouch(seek:SeekBar?){}
            })
            val controls=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                gravity=Gravity.CENTER_VERTICAL
            }
            controls.addView(btn("◀◀  10 s"){skip(-10000)},
                LinearLayout.LayoutParams(0,dp(37),1f))
            playButton=btn(if(player?.isPlaying==true)"❚❚  PAUZA" else "▶  ODTWÓRZ"){
                togglePlay(selected)
            }
            controls.addView(playButton,LinearLayout.LayoutParams(0,dp(37),1.4f))
            controls.addView(btn("10 s  ▶▶"){skip(10000)},
                LinearLayout.LayoutParams(0,dp(37),1f))
            rightPanel.addView(controls)
            if(!dashboard)rightPanel.addView(btn("☆  ULUBIONE / WIĘCEJ") {
                showFileActions(selected)
            },LinearLayout.LayoutParams(-1,dp(35)))
        }
        if(!dashboard) {
            rightPanel.addView(space(8))
            rightPanel.addView(btn("⚙  Ustawienia nagrywania") {
                showPage("Ustawienia")
            },LinearLayout.LayoutParams(-1,dp(37)))
        }
    }

    private fun showPlayer(entry:AudioEntry,saveHistory:Boolean=true){
        renderRight(entry)
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
                    if(heading=="FORMAT NAGRANIA" && key=="MP3" && !Mp3Native.available()) {
                        dialog.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle("MP3 niedostępny na tym telewizorze")
                            .setMessage("Brak biblioteki LAME MP3 w aplikacji. Dostępne formaty: "
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
            "OPUS"->"Opus — oszczędny"
            else->"M4A — AAC"
        },listOf(
            "M4A — AAC" to "M4A",
            "MP4 — AAC (bez obrazu)" to "MP4",
            "MP3 — LAME (działa bez kodeka TV)" to "MP3",
            "Opus — Ogg, mały rozmiar, dobra jakość" to "OPUS",
            "WAV — PCM bez kompresji" to "WAV",
            "FLAC — bezstratny" to "FLAC"),format) {
            prefs.edit().putString("format",it).apply()
        }
        val rate=prefs.getInt("rate",48000).toString()
        if(format=="OPUS")panel.addView(label(
            "Opus nagrywa z częstotliwością 48 kHz (zgodność z kodekiem).",14,subtle))
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
        panel.addView(btn("←  WSTECZ") {previousScreen()})
    }
    private fun time(millis:Long):String {
        val s=(millis/1000).coerceAtLeast(0)
        return if(s>=3600)String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60)
            else String.format(Locale.ROOT,"%02d:%02d",s/60,s%60)
    }
    private fun size(bytes:Long):String=String.format(Locale("pl"),"%.2f MB",bytes/1048576.0)
}