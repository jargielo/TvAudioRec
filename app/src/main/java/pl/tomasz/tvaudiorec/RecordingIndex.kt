package pl.tomasz.tvaudiorec

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent recording catalog.
 * The file is immediately registered when the service finishes saving, so
 * the list does not depend on the platform media scanner or its index delay.
 */
object RecordingIndex {
    data class Item(val uri: Uri, val name: String, val size: Long, val timestamp: Long)
    private const val STORE="recording_catalog"
    private const val KEY="items"
    private const val MAX=2500

    @Synchronized
    fun save(context: Context, uri: Uri, name: String, size: Long, timestamp: Long) {
        val p=context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val source=try {JSONArray(p.getString(KEY,"[]"))} catch (_:Exception){JSONArray()}
        val output=JSONArray()
        output.put(JSONObject().apply {
            put("uri",uri.toString())
            put("name",name)
            put("size",size)
            put("time",timestamp)
        })
        for (i in 0 until source.length()) {
            val old=source.optJSONObject(i) ?:continue
            if(old.optString("uri") == uri.toString()) continue
            if(output.length()>=MAX)break
            output.put(old)
        }
        // commit() is synchronous: survive app/process shutdown.
        p.edit().putString(KEY,output.toString()).commit()
    }

    @Synchronized
    fun entries(context: Context):List<Item>{
        val p=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
        val arr=try {JSONArray(p.getString(KEY,"[]"))} catch (_:Exception){JSONArray()}
        val result=ArrayList<Item>()
        for(i in 0 until arr.length()){
            val obj=arr.optJSONObject(i) ?:continue
            val uri=obj.optString("uri","")
            if(uri.isBlank())continue
            result.add(Item(Uri.parse(uri),obj.optString("name","Nagranie"),
                obj.optLong("size",0L),obj.optLong("time",0L)))
        }
        return result
    }

    @Synchronized
    fun delete(context:Context,uri:Uri){
        val p=context.getSharedPreferences(STORE,Context.MODE_PRIVATE)
        val arr=try {JSONArray(p.getString(KEY,"[]"))}catch (_:Exception){JSONArray()}
        val clean=JSONArray()
        for(i in 0 until arr.length()){
            val item=arr.optJSONObject(i) ?:continue
            if(item.optString("uri") != uri.toString())clean.put(item)
        }
        p.edit().putString(KEY,clean.toString()).commit()
    }
}
