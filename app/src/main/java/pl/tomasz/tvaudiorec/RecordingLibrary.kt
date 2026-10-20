package pl.tomasz.tvaudiorec

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * Own index + MediaStore + direct folder, with diagnostic counters.
 * A new recording is available immediately even before MediaStore indexes it.
 */
object RecordingLibrary {
    data class Entry(val uri:Uri,val name:String,val size:Long,val time:Long,val origin:String)
    data class Scan(val items:List<Entry>,val counts:Map<String,Int>,
                    val errors:List<String>,val paths:List<String>,val indexed:Int)
    data class Output(val uri:Uri,val fd:ParcelFileDescriptor,val name:String,
                      val publicFile:Boolean,val fallback:File?)
    private val allowed=setOf("mp3","m4a","mp4","wav","flac","aac","ogg")
    private fun audio(name:String)=name.substringAfterLast('.',"").lowercase() in allowed

    fun create(context:Context,name:String,mime:String):Output {
        val resolver=context.contentResolver
        val uri=try {
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,name)
                    put(MediaStore.MediaColumns.MIME_TYPE,mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH,"Music/TVAudioRecorder/")
                    put(MediaStore.MediaColumns.IS_PENDING,1)
                })
        }catch(e:Exception){
            Log.e("TVAudioRecorder","Public file creation failed",e)
            null
        }
        if(uri!=null)try {
            val descriptor=resolver.openFileDescriptor(uri,"rw")
                ?:error("Public file was not opened")
            context.getSharedPreferences("record_settings",Context.MODE_PRIVATE)
                .edit().putString("last_output_path","Music/TVAudioRecorder/"+name)
                .putString("last_output_uri",uri.toString()).commit()
            return Output(uri,descriptor,name,true,null)
        }catch(e:Exception){
            Log.e("TVAudioRecorder","Public file opening failed",e)
            try{resolver.delete(uri,null,null)}catch(_:Exception){}
        }
        val root=context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?:context.filesDir
        val dir=File(root,"TVAudioRecorder")
        check(dir.exists() || dir.mkdirs()){"Nie można utworzyć folderu nagrań"}
        val file=File(dir,name)
        val descriptor=ParcelFileDescriptor.open(file,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or
            ParcelFileDescriptor.MODE_TRUNCATE)
        context.getSharedPreferences("record_settings",Context.MODE_PRIVATE)
            .edit().putString("last_output_path",file.absolutePath)
            .putString("last_output_uri",Uri.fromFile(file).toString()).commit()
        return Output(Uri.fromFile(file),descriptor,name,false,file)
    }

    fun finalize(context:Context,output:Output,success:Boolean):String {
        val resolver=context.contentResolver
        if(!success){
            if(output.publicFile)try{resolver.delete(output.uri,null,null)}catch(_:Exception){}
            else try{output.fallback?.delete()}catch(_:Exception){}
            return "Nagranie puste lub nieukończone."
        }
        var published=true
        if(output.publicFile)try {
            published=resolver.update(output.uri,
                ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},
                null,null)>0
        }catch(e:Exception){
            published=false
            Log.e("TVAudioRecorder","Publishing failed",e)
        }
        val bytes=try {
            if(output.fallback!=null)output.fallback.length()
            else resolver.openFileDescriptor(output.uri,"r")?.use {
                if(it.statSize<0)0L else it.statSize
            } ?:0L
        }catch(_:Exception){0L}
        // Keep index even if publishing fails: the URI may still be readable.
        RecordingIndex.save(context,output.uri,output.name,bytes,
            System.currentTimeMillis()/1000L)
        val prefs=context.getSharedPreferences("record_settings",Context.MODE_PRIVATE)
        prefs.edit().putString("last_saved_uri",output.uri.toString())
            .putString("last_saved_name",output.name)
            .putLong("last_saved_bytes",bytes)
            .putBoolean("last_saved_published",published)
            .putLong("last_saved_at",System.currentTimeMillis())
            .putLong("library_revision",prefs.getLong("library_revision",0L)+1L)
            .commit()
        return if(published)"Zapisano: "+output.name+" ("+bytes+" B)"
            else "Zapisano: "+output.name+
                " — plik zarejestrowano, ale Android nie potwierdził publikacji"
    }

    fun scan(context:Context,showAll:Boolean):Scan{
        val items=linkedMapOf<String,Entry>()
        val counts=linkedMapOf<String,Int>()
        val errors=mutableListOf<String>()
        val paths=mutableListOf<String>()
        fun insert(item:Entry){
            if(!audio(item.name))return
            val old=items[item.uri.toString()]
            if(old==null || (old.size==0L && item.size>0))
                items[item.uri.toString()]=item
        }
        val own=RecordingIndex.entries(context)
        counts["Rejestr zapisanych plików"]=own.size
        for(item in own)
            insert(Entry(item.uri,item.name,item.size,item.timestamp,"Własny indeks"))

        val resolver=context.contentResolver
        val projection=arrayOf(MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH)

        fun queryAudio(label:String,selection:String?,args:Array<String>?) {
            var count=0
            try {
                val base=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                @Suppress("DEPRECATION")
                val includePending=MediaStore.setIncludePending(base)
                resolver.query(includePending,projection,selection,args,
                    MediaStore.MediaColumns.DATE_ADDED+" DESC")?.use {c->
                    while(c.moveToNext()){
                        val name=c.getString(1) ?:continue
                        if(!audio(name))continue
                        val path=c.getString(4) ?: ""
                        val ownName=name.startsWith("TV_Audio_",true) ||
                            name.startsWith("TEST_TV_",true)
                        if(!showAll && !path.contains("TVAudioRecorder",true) && !ownName)
                            continue
                        insert(Entry(ContentUris.withAppendedId(base,c.getLong(0)),
                            name,c.getLong(2),c.getLong(3),"MediaStore: "+path))
                        count++
                    }
                }
            }catch(e:Exception){
                errors.add(label+": "+e.javaClass.simpleName+": "+
                    (e.message ?: "").take(70))
            }
            counts[label]=count
        }
        // Query each independently because some TV firmware versions leave
        // relative_path empty even when display_name is correct.
        queryAudio("Folder w MediaStore",MediaStore.MediaColumns.RELATIVE_PATH+
            " LIKE ?",arrayOf("%TVAudioRecorder%"))
        queryAudio("Nazwy w MediaStore",MediaStore.MediaColumns.DISPLAY_NAME+
            " LIKE ?",arrayOf("TV_Audio_%"))
        if(showAll)queryAudio("Cała biblioteka audio",null,null)

        // Audio-only MP4 might be indexed under MediaStore.Files instead.
        try{
            val base=MediaStore.Files.getContentUri("external")
            var count=0
            resolver.query(base,projection,
                MediaStore.MediaColumns.RELATIVE_PATH+" LIKE ?",
                arrayOf("%TVAudioRecorder%"),null)?.use{c->
                while(c.moveToNext()) {
                    val name=c.getString(1) ?:continue
                    if(!audio(name))continue
                    insert(Entry(ContentUris.withAppendedId(base,c.getLong(0)),
                        name,c.getLong(2),c.getLong(3),"MediaStore.Files"))
                    count++
                }
            }
            counts["MediaStore.Files"]=count
        }catch(e:Exception){errors.add("MediaStore.Files: "+e.javaClass.simpleName)}
        val public=File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC),"TVAudioRecorder")
        val roots=listOfNotNull(public,
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?:context.filesDir,"TVAudioRecorder"),
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            context.getExternalFilesDir(null))
            .distinctBy{it.absolutePath}
        for(dir in roots){
            var count=0
            try{
                val files=dir.listFiles()
                if(files==null)paths.add(dir.absolutePath+" — brak dostępu lub folderu")
                else{
                    fun walk(filesToRead:Array<File>){
                        for(f in filesToRead){
                            if(f.isDirectory && f.name=="TVAudioRecorder") {
                                walk(f.listFiles() ?:emptyArray())
                            } else if(f.isFile && audio(f.name)){
                                insert(Entry(Uri.fromFile(f),f.name,f.length(),
                                    f.lastModified()/1000L,"Plik z folderu"))
                                count++
                            }
                        }
                    }
                    walk(files)
                    paths.add(dir.absolutePath+" — "+count+" plików audio")
                }
            }catch(e:Exception){
                paths.add(dir.absolutePath+" — "+e.javaClass.simpleName)
            }
            counts["Skan "+dir.name]=count
        }
        return Scan(items.values.sortedByDescending{it.time},
            counts,errors,paths,own.size)
    }

    fun testFile(context:Context):String{
        val output=create(context,"TEST_TV_"+System.currentTimeMillis()+".wav","audio/wav")
        var okay=false
        try{
            val pcm=ByteArray(96000)
            for(i in 0 until 48000){
                val sample=(2400*Math.sin(2*Math.PI*440.0*i/48000.0)).toInt()
                pcm[2*i]=(sample and 255).toByte()
                pcm[2*i+1]=(sample shr 8).toByte()
            }
            val sink=WavSink(output.fd,48000,1)
            try{
                sink.write(pcm,pcm.size)
                sink.finish()
                okay=true
            }finally{sink.close()}
        }finally{
            try{output.fd.close()}catch(_:Exception){}
        }
        return finalize(context,output,okay)
    }
}
