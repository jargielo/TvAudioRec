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
    private val surface=Color.rgb(13,13,15)
    private val accent=Color.rgb(241,35,53)
    private val textColor=Color.rgb(246,246,247)
    private val subtle=Color.rgb(170,172,177)
    private val prefs by lazy { getSharedPreferences("record_settings",MODE_PRIVATE) }
    private val manrope:Typeface by lazy {
        try {Typeface.createFromAsset(assets,"fonts/manrope.ttf")}
        catch (_:Exception) {Typeface.create("sans-serif",Typeface.NORMAL)}
    }
    private fun uiScale():Float=when(prefs.getString("ui_size","M")) {
        "S"->0.73f
        "L"->0.97f
        else->0.84f
    }
    private fun fontScale():Float=when(prefs.getString("ui_size","M")) {
        "S"->0.82f
        "L"->1.13f
        else->1.0f
    }
    private lateinit var root:LinearLayout
    private val navigationButtons=linkedMapOf<String,Button>()
    private var rightPreview:LinearLayout?=null
    private var selectedEntry:AudioEntry?=null
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
                if(page in listOf("Nagrania","Ulubione","Kosz","Diagnostyka"))
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
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(10),dp(10),dp(10),dp(10))
        }
        setContentView(root)
        val side=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(4),dp(4),dp(10),dp(4))
        }
        root.addView(side,LinearLayout.LayoutParams(dp(200),-1))
        val logo=ImageView(this).apply {
            scaleType=ImageView.ScaleType.FIT_CENTER
            adjustViewBounds=true
            try {
                assets.open("brand_logo.jpg").use { stream->
                    setImageBitmap(BitmapFactory.decodeStream(stream))
                }
            }catch (_:Exception) { contentDescription="AudioRelay Recorder" }
        }
        side.addView(logo,LinearLayout.LayoutParams(-1,dp(94)))
        side.addView(space(8))
        for(name in listOf("Nagrywaj","Nagrania","Ulubione","Kosz","Ustawienia","Diagnostyka")) {
            val title=when(name){
                "Nagrywaj"->"●  NAGRYWANIE"
                "Nagrania"->"≡  MOJE NAGRANIA"
                "Ulubione"->"☆  ULUBIONE"
                "Kosz"->"▣  KOSZ"
                "Ustawienia"->"⚙  USTAWIENIA"
                else->"⌕  DIAGNOSTYKA PLIKÓW"
            }
            val button=btn(title) {showPage(name)}
            navigationButtons[name]=button
            side.addView(button)
        }
        side.addView(space(10))
        side.addView(btn("Aa  Manrope  •  "+(prefs.getString("ui_size","M") ?: "M")) {
            sizeDialog()
        })
        side.addView(View(this),LinearLayout.LayoutParams(1,0,1f))
        side.addView(label("AudioRelay Recorder  •  v1.6",12,subtle))
        status=label("Gotowy.",13,subtle)
        side.addView(status)
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(surface)
        }
        panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(11),dp(12),dp(10))
        }
        scroll.addView(panel)
        root.addView(scroll,LinearLayout.LayoutParams(0,-1,1f))
        showPage(savedInstanceState?.getString("page") ?: "Nagrywaj",false)
        lastLibraryRevision=prefs.getLong("library_revision",0L)
        handler.post(ticker)
    }
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        player?.release();player=null
        super.onDestroy()
    }
    private fun dp(v:Int):Int=(resources.displayMetrics.density*v*uiScale()).toInt().coerceAtLeast(1)
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("page",page)
        super.onSaveInstanceState(outState)
    }
    private fun sizeDialog() {
        val options=arrayOf("S — kompaktowy","M — standardowy","L — większy")
        val values=arrayOf("S","M","L")
        val index=values.indexOf(prefs.getString("ui_size","M")).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Wielkość interfejsu — Manrope")
            .setSingleChoiceItems(options,index) { dialog,which->
                prefs.edit().putString("ui_size",values[which]).commit()
                dialog.dismiss()
                recreate()
            }.setNegativeButton("ANULUJ",null).show()
    }
    private fun label(value:String,size:Int=18,color:Int=textColor):TextView=TextView(this).apply {
        text=value;textSize=size.toFloat()*fontScale();setTextColor(color)
        typeface=manrope
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(3),dp(5),dp(3),dp(5))
    }
    private fun space(h:Int):View=View(this).apply {minimumHeight=dp(h)}
    private fun btn(value:String,click:()->Unit):Button=Button(this).apply {
        text=value
        textSize=14f*fontScale()
        typeface=manrope
        minimumHeight=0
        minHeight=0
        isAllCaps=false
        isFocusable=true
        setTextColor(textColor)
        setPadding(dp(7),dp(6),dp(7),dp(6))
        background=rounded(surface)
        setOnFocusChangeListener { view,hasFocus ->
            view.background=rounded(if(hasFocus)Color.rgb(67,7,14) else surface,hasFocus)
            (view as Button).setTextColor(if(hasFocus) textColor else textColor)
        }
        setOnClickListener {click()}
    }
    private fun rounded(color:Int,focused:Boolean=false):GradientDrawable=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(10).toFloat()
        setStroke(dp(if(focused)2 else 1),
            if(focused) accent else Color.rgb(37,37,41))
    }
    private fun line(title:String,sub:String?=null) {
        panel.addView(label(title,25,textColor))
        if(sub!=null)panel.addView(label(sub,15,subtle))
        panel.addView(space(6))
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
    private fun showPage(target:String, saveHistory:Boolean=true) {
        if(saveHistory && target!=page && page!="Odtwarzacz")
            history.addLast(page)
        if(target!=page && page=="Odtwarzacz")
            player?.pause()
        page=target
        panel.removeAllViews()
        rightPreview=null
        timer=null;waveform=null;clock=null;playSeek=null;playButton=null
        navigationButtons.forEach { (name,b)->
            b.background=rounded(if(name==target)Color.rgb(65,3,12) else surface,
                name==target)
            b.setTextColor(if(name==target)accent else textColor)
        }
        when(target) {
            "Nagrywaj"->recordPage()
            "Nagrania"->dashboardPage()
            "Ulubione"->filesPage(false,true)
            "Kosz"->filesPage(true,false)
            "Ustawienia"->settingsPage()
            "Diagnostyka"->diagnosticsPage()
        }
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
            .setTitle("Zamknąć TV Audio Recorder?")
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

    private fun recordPage() {
        line("REJESTRATOR DŹWIĘKU","Źródło: "+sourceName()+"    •    "+
            prefs.getString("format","M4A")+"    •    "+rateName()+"    •    "+
            (if(prefs.getInt("channels",2)==2)"Stereo" else "Mono"))
        panel.addView(btn("⚙  WYBIERZ ŹRÓDŁO, FORMAT, kHz, MONO / STEREO") {
            showPage("Ustawienia")
        })
        panel.addView(btn("≡  MOJE NAGRANIA — LISTA I IMPORT") {showPage("Nagrania")})
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
        panel.addView(btn("⌕  SPRAWDŹ GDZIE ZAPISANO PLIKI") {showPage("Diagnostyka")})
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
        if(extension !in setOf("mp3","m4a","mp4","wav","flac","aac","ogg")) {
            status.text="Nieobsługiwany format: "+clean
            return
        }
        val mime=when(extension) {
            "mp3"->"audio/mpeg";"m4a","mp4"->"audio/mp4"
            "wav"->"audio/wav";"flac"->"audio/flac";"ogg"->"audio/ogg"
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

    /** A compact media dashboard: D-pad navigates list, playback and settings. */
    private fun dashboardPage() {
        val container=panel
        val grid=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        container.addView(grid,LinearLayout.LayoutParams(-1,
            (resources.displayMetrics.heightPixels-dp(38)).coerceAtLeast(dp(300))))
        val leftScroll=ScrollView(this).apply {isFillViewport=true}
        val left=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(2),0,dp(7),0)
        }
        leftScroll.addView(left)
        grid.addView(leftScroll,LinearLayout.LayoutParams(0,-1,1.4f))
        val separator=View(this).apply {setBackgroundColor(Color.rgb(37,37,39))}
        grid.addView(separator,LinearLayout.LayoutParams(dp(1),-1))
        val rightScroll=ScrollView(this).apply {isFillViewport=true}
        val right=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(7),0,0,0)
        }
        rightScroll.addView(right)
        grid.addView(rightScroll,LinearLayout.LayoutParams(0,-1,1.05f))
        rightPreview=right

        val head=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        head.addView(label("MOJE NAGRANIA",23,textColor),
            LinearLayout.LayoutParams(0,dp(50),1f))
        head.addView(btn("↻") {showPage("Nagrania",false)},
            LinearLayout.LayoutParams(dp(48),dp(45)))
        head.addView(btn("Aa") {sizeDialog()},
            LinearLayout.LayoutParams(dp(54),dp(45)))
        left.addView(head)

        val filterRow=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        filterRow.addView(btn(if(allAudio)"Tylko nagrania" else "Wszystkie audio") {
            allAudio=!allAudio;showPage("Nagrania",false)
        },LinearLayout.LayoutParams(0,dp(41),1f))
        filterRow.addView(btn("ZAZNACZ / USUŃ WIELE") {multiDeletePage()},
            LinearLayout.LayoutParams(0,dp(41),1.35f))
        left.addView(filterRow)

        val recordings=getEntries().filter {!trashed(it.name)}
        left.addView(label("Biblioteka: "+recordings.size+" plików",13,subtle))
        if(recordings.isEmpty()) {
            left.addView(label("Nie znaleziono nagrań.",18,subtle))
            left.addView(btn("TEST ZAPISU") {
                try {
                    status.text=RecordingLibrary.testFile(this)
                    showPage("Nagrania",false)
                }catch(e:Exception){status.text="Test nieudany: "+e.message}
            })
            left.addView(btn("Diagnostyka") {showPage("Diagnostyka")})
        }
        val list=if(prefs.getBoolean("newest",true))recordings else recordings.reversed()
        for(entry in list.take(250)) {
            val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
            val display=entry.name.take(55)
            val main=btn(display+"  •  "+size(entry.bytes)) {
                showDashboardPreview(entry)
            }
            if(entry.uri==selectedEntry?.uri)main.background=rounded(Color.rgb(48,6,11),true)
            row.addView(main,LinearLayout.LayoutParams(0,dp(49),1f))
            row.addView(btn("▶") {
                showDashboardPreview(entry)
                togglePlay(entry)
            },LinearLayout.LayoutParams(dp(48),dp(49)))
            row.addView(btn("⋮") {showFileActions(entry)},
                LinearLayout.LayoutParams(dp(45),dp(49)))
            left.addView(row)
        }
        left.addView(btn("↕  Sortowanie: "+if(prefs.getBoolean("newest",true))
            "najnowsze" else "najstarsze") {
            prefs.edit().putBoolean("newest",!prefs.getBoolean("newest",true)).apply()
            showPage("Nagrania",false)
        })
        left.addView(btn("＋  Importuj plik") {pickImport()})
        left.addView(btn("⌕  Diagnostyka") {showPage("Diagnostyka")})
        val chosen=recordings.firstOrNull {it.uri==selectedEntry?.uri}
            ?:recordings.firstOrNull()
        showDashboardPreview(chosen)
    }

    private fun quickSelect(
        parent:LinearLayout, title:String, value:String,
        values:List<Pair<String,String>>,selected:String,
        onChange:(String)->Unit
    ) {
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        row.addView(label(title,13,subtle),LinearLayout.LayoutParams(0,dp(40),1f))
        row.addView(btn(value+" ▾") {
            AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(values.map {it.first}.toTypedArray(),
                    values.indexOfFirst{it.second==selected}.coerceAtLeast(0)) {
                    dialog,index->
                    val chosen=values[index].second
                    if(chosen=="MP3" && !Mp3Native.available()) {
                        status.text="Brak biblioteki LAME."
                    } else onChange(chosen)
                    dialog.dismiss()
                    showPage("Nagrania",false)
                }.setNegativeButton("ANULUJ",null).show()
        },LinearLayout.LayoutParams(0,dp(40),1.35f))
        parent.addView(row)
    }

    private fun showDashboardPreview(entry:AudioEntry?) {
        val host=rightPreview ?:return
        selectedEntry=entry
        host.removeAllViews()
        host.addView(label(entry?.name ?: "ODTWARZACZ",17,textColor))
        host.addView(label(if(entry==null)"Wybierz plik z listy"
            else "Audio  •  "+size(entry.bytes),12,subtle))
        val wave=WaveformView(this)
        wave.peaks=entry?.let {
            prefs.getString("wave."+it.name,"").orEmpty()
                .split(",").mapNotNull {n->n.toIntOrNull()}
        } ?:emptyList()
        waveform=wave
        host.addView(wave,LinearLayout.LayoutParams(-1,dp(125)))
        clock=label("00:00",16,accent).also {host.addView(it)}
        val seek=SeekBar(this).apply {max=1000;progress=0;isFocusable=true}
        playSeek=seek
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(v:SeekBar?){}
            override fun onStopTrackingTouch(v:SeekBar?){}
            override fun onProgressChanged(v:SeekBar?,p:Int,fromUser:Boolean){
                if(fromUser)try{player?.let{if(it.duration>0)
                    it.seekTo((it.duration.toLong()*p/1000L).toInt())}}
                catch(_:Exception){}
            }
        })
        host.addView(seek,LinearLayout.LayoutParams(-1,dp(34)))
        val controls=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        controls.addView(btn("−10 s"){skip(-10000)},LinearLayout.LayoutParams(0,dp(46),1f))
        playButton=btn("▶  ODTWÓRZ") {if(entry!=null)togglePlay(entry)}.also {
            it.isEnabled=entry!=null
            controls.addView(it,LinearLayout.LayoutParams(0,dp(46),1.9f))
        }
        controls.addView(btn("+10 s"){skip(10000)},LinearLayout.LayoutParams(0,dp(46),1f))
        host.addView(controls)
        host.addView(btn("☆  Ulubione       ⋯  Opcje") {
            if(entry!=null)showFileActions(entry)
        })
        host.addView(space(8))
        host.addView(label("SZYBKIE USTAWIENIA",16,textColor))
        val source=prefs.getString("source","Internal") ?: "Internal"
        quickSelect(host,"Źródło",sourceName(),listOf(
            "Wewnętrzny" to "Internal","Mikrofon" to "Microphone",
            "Wewnętrzny + Mic" to "Internal + Mic",
            "Wewnętrzny + BT" to "Internal + Bluetooth"
        ),source){prefs.edit().putString("source",it).apply()}
        val fmt=prefs.getString("format","M4A") ?: "M4A"
        quickSelect(host,"Format",fmt,listOf(
            "OPUS — najlepszy rozmiar" to "OPUS",
            "M4A — AAC" to "M4A",
            "MP3 — LAME" to "MP3",
            "WAV — PCM" to "WAV",
            "FLAC — bezstratny" to "FLAC",
            "MP4 — AAC" to "MP4"
        ),fmt){
            prefs.edit().putString("format",it)
                .apply()
        }
        val rate=prefs.getInt("rate",48000).toString()
        quickSelect(host,"Częstotliwość",rate+" Hz",listOf(
            "16 kHz" to "16000","32 kHz" to "32000",
            "44.1 kHz" to "44100","48 kHz" to "48000"
        ),rate){prefs.edit().putInt("rate",it.toInt()).apply()}
        val ch=prefs.getInt("channels",2).toString()
        quickSelect(host,"Kanały",if(ch=="2")"Stereo" else "Mono",
            listOf("Mono" to "1","Stereo" to "2"),ch){
            prefs.edit().putInt("channels",it.toInt()).apply()
        }
        val action=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        action.addView(btn("●  REC") {startRecording()},
            LinearLayout.LayoutParams(0,dp(47),1f))
        action.addView(btn("■  STOP") {stopRecording()},
            LinearLayout.LayoutParams(0,dp(47),1f))
        host.addView(action)
        if(entry!=null && playing?.uri==entry.uri)
            playButton?.text=if(player?.isPlaying==true)"Ⅱ  PAUZA" else "▶  ODTWÓRZ"
    }

    private fun multiDeletePage(trash:Boolean=false) {
        val entries=getEntries().filter{trashed(it.name)==trash}
        if(page!="Wybór nagrań")history.addLast(page)
        page="Wybór nagrań"
        panel.removeAllViews()
        val selected=linkedSetOf<String>()
        panel.addView(label(if(trash)"ZAZNACZ DO TRWAŁEGO USUNIĘCIA"
            else "ZAZNACZ NAGRANIA DO KOSZA",23,textColor))
        val summary=label("Zaznaczono: 0",17,subtle)
        panel.addView(summary)
        val selectAll=btn("☐  Zaznacz wszystkie") {}
        panel.addView(selectAll)
        val selectionButtons=mutableListOf<Pair<AudioEntry,Button>>()
        fun update() {
            summary.text="Zaznaczono: "+selected.size+" z "+entries.size
            for((item,button) in selectionButtons) {
                button.text=(if(selected.contains(item.uri.toString()))"☑  " else "☐  ")+item.name
                button.background=rounded(if(selected.contains(item.uri.toString()))
                    Color.rgb(67,7,16) else surface,selected.contains(item.uri.toString()))
            }
            selectAll.text=if(selected.size==entries.size)
                "☑  Odznacz wszystkie" else "☐  Zaznacz wszystkie"
        }
        selectAll.setOnClickListener {
            if(selected.size==entries.size) selected.clear()
            else for(e in entries)selected.add(e.uri.toString())
            update()
        }
        val row=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        row.addView(btn(if(trash)"USUŃ TRWALE ZAZNACZONE" else "PRZENIEŚ ZAZNACZONE DO KOSZA") {
            if(selected.isEmpty()) {
                status.text="Najpierw zaznacz nagrania."
            } else {
                AlertDialog.Builder(this).setTitle(if(trash)"Usunąć trwale?" else "Przenieść do kosza?")
                    .setMessage(if(trash)
                        "Usuniesz bezpowrotnie "+selected.size+" plików. Nie będzie można ich odzyskać."
                        else "Przenieść "+selected.size+" nagrań? Będzie można je przywrócić.")
                    .setNegativeButton("ANULUJ",null)
                    .setPositiveButton(if(trash)"USUŃ TRWALE" else "PRZENIEŚ") {_,_->
                        val edit=prefs.edit()
                        var done=0
                        var failed=0
                        for(e in entries)if(selected.contains(e.uri.toString())) {
                            if(trash){
                                try {
                                    val removed=if(e.uri.scheme=="file")
                                        File(e.uri.path!!).delete()
                                    else contentResolver.delete(e.uri,null,null)>0
                                    if(removed) {
                                        RecordingIndex.delete(this,e.uri)
                                        edit.remove("trash."+e.name)
                                            .remove("favorite."+e.name)
                                            .remove("wave."+e.name)
                                        done++
                                    } else failed++
                                }catch(_:Exception){failed++}
                            }else{
                                edit.putBoolean("trash."+e.name,true)
                                done++
                            }
                        }
                        edit.commit()
                        showPage(if(trash)"Kosz" else "Nagrania",false)
                        status.text="Zmieniono "+done+" plików."+
                            if(failed>0)" Nie udało się usunąć: "+failed else ""
                    }.show()
            }
        },LinearLayout.LayoutParams(0,dp(49),1f))
        row.addView(btn("←  ANULUJ") {showPage(if(trash)"Kosz" else "Nagrania",false)},
            LinearLayout.LayoutParams(0,dp(49),1f))
        panel.addView(row)
        val list=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply {addView(list)}
        panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        for(e in entries) {
            val button=btn(e.name+"    •    "+size(e.bytes)) {
                val key=e.uri.toString()
                if(!selected.add(key))selected.remove(key)
                update()
            }
            list.addView(button,LinearLayout.LayoutParams(-1,dp(47)))
            selectionButtons.add(e to button)
        }
        update()
    }

    private fun filesPage(trash:Boolean,onlyFavorite:Boolean) {
        val heading=if(trash)"KOSZ" else if(onlyFavorite)"ULUBIONE" else "MOJE NAGRANIA"
        line(heading,"Pliki nagrane na tym telewizorze")
        if(trash)panel.addView(btn("☑  ZAZNACZ / USUŃ WIELE NAGRAŃ TRWALE") {
            multiDeletePage(true)
        })
        if(!trash && !hasAudioPermission() &&
            !prefs.getBoolean("asked_audio_permission",false)){
            prefs.edit().putBoolean("asked_audio_permission",true).apply()
            panel.post {requestAudioPermission()}
        }

        val entries=getEntries().filter {
            trashed(it.name)==trash && (!onlyFavorite || favorite(it.name))
        }.let{if(prefs.getBoolean("newest",true))it else it.reversed()}

        // Results are ABOVE the management buttons, visible without scrolling.
        panel.addView(label("Znaleziono: "+entries.size+" nagrań",23,accent))
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
                row.addView(btn(entry.name+"   •   "+size(entry.bytes)) {
                    if(trash)showTrashActions(entry) else showPlayer(entry)
                },LinearLayout.LayoutParams(0,dp(64),1f))
                if(!trash){
                    row.addView(btn(if(favorite(entry.name))"★" else "☆") {
                        prefs.edit().putBoolean("favorite."+entry.name,
                            !favorite(entry.name)).apply()
                        showPage(page,false)
                    },LinearLayout.LayoutParams(dp(62),dp(64)))
                    row.addView(btn("⋮"){showFileActions(entry)},
                        LinearLayout.LayoutParams(dp(62),dp(64)))
                } else {
                    row.addView(btn("PRZYWRÓĆ"){
                        prefs.edit().putBoolean("trash."+entry.name,false).apply()
                        showPage("Kosz",false)
                    },LinearLayout.LayoutParams(dp(145),dp(64)))
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
    private fun showPlayer(entry:AudioEntry,saveHistory:Boolean=true) {
        if(saveHistory && page!="Odtwarzacz")history.addLast(page)
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
        panel.addView(btn("←  WSTECZ DO LISTY") {previousScreen()})
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
            "OPUS"->"Opus / Ogg — wysoka jakość, mały rozmiar"
            "MP4"->"MP4 — tylko audio AAC"
            "MP3"->"MP3 — kompresja stratna"
            "WAV"->"WAV — bez kompresji"
            "FLAC"->"FLAC — bezstratny"
            else->"M4A — AAC"
        },listOf(
            "Opus / Ogg — oszczędny (zalecany)" to "OPUS",
            "M4A — AAC" to "M4A",
            "MP4 — AAC (bez obrazu)" to "MP4",
            "MP3 — LAME (działa bez kodeka TV)" to "MP3",
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
        val opusRate=prefs.getInt("opus_bitrate",96000).toString()
        selectionRow("JAKOŚĆ OPUS (48 kHz)",(opusRate.toInt()/1000).toString()+" kb/s",
            listOf("48 kb/s — mowa" to "48000","64 kb/s — standard" to "64000",
                "96 kb/s — muzyka" to "96000","128 kb/s — lepsza muzyka" to "128000"),
            opusRate) {
            prefs.edit().putInt("opus_bitrate",it.toInt()).apply()
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