package pl.tomasz.tvaudiorec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max

class WaveformView(context:Context):View(context) {
    var peaks:List<Int> = emptyList()
        set(value) {field=value;invalidate()}
    var progress:Float=0f
        set(value){field=value.coerceIn(0f,1f);invalidate()}
    var liveLevel:Int=0
        set(value){field=value;invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent=Color.rgb(239,34,53)
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat()
        val h=height.toFloat()
        canvas.drawColor(Color.BLACK)
        paint.strokeWidth=1f
        paint.color=Color.rgb(45,45,49)
        for(i in 0..12) {
            val x=i*w/12f
            canvas.drawLine(x,12f,x,h-12f,paint)
        }
        val middle=h/2f
        canvas.drawLine(0f,middle,w,middle,paint)
        val items=if(peaks.isNotEmpty())peaks else if(liveLevel>0)
            List(32){liveLevel} else emptyList()
        if(items.isNotEmpty()) {
            val maxVal=max(400,items.maxOrNull() ?: 400)
            val spacing=w/items.size
            paint.strokeWidth=max(2f,spacing*0.65f)
            paint.strokeCap=Paint.Cap.ROUND
            for(i in items.indices) {
                val value=(items[i].toFloat()/maxVal).coerceIn(0.025f,1f)
                val bh=value*(h*0.42f)
                paint.color=if(i.toFloat()/items.size<=progress) accent else Color.rgb(156,157,163)
                val x=(i+0.5f)*spacing
                canvas.drawLine(x,middle-bh,x,middle+bh,paint)
            }
        } else {
            paint.color=Color.rgb(150,150,153)
            paint.textSize=19f
            canvas.drawText("Przebieg dźwięku pojawi się po zapisaniu nagrania",18f,middle,paint)
        }
        paint.color=accent
        paint.strokeWidth=3f
        val position=progress*w
        canvas.drawLine(position,0f,position,h,paint)
        paint.style=Paint.Style.FILL
        canvas.drawCircle(position.coerceIn(4f,w-4f),h*0.08f,5f,paint)
    }
}