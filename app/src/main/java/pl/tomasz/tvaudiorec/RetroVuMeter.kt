package pl.tomasz.tvaudiorec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Modern AMOLED twin-channel recording level monitor. Both visual rows
 * represent the same combined capture amplitude, not independent channels.
 */
class RetroVuMeter(context:Context):View(context) {
    var level:Int=0
        set(value){field=value.coerceIn(0,32767);invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val lit=Color.rgb(244,247,251)
    private val red=Color.rgb(255,35,61)
    private val dim=Color.rgb(38,42,48)
    private val columns=36

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        val widthPx=width.toFloat()
        val heightPx=height.toFloat()
        if(widthPx<=0f || heightPx<=0f)return
        paint.style=Paint.Style.FILL
        paint.typeface=android.graphics.Typeface.create("sans-serif-medium",
            android.graphics.Typeface.NORMAL)
        val labelWidth=24f*resources.displayMetrics.density
        val padding=8f*resources.displayMetrics.density
        val available=(widthPx-labelWidth-padding).coerceAtLeast(50f)
        val gap=3.5f*resources.displayMetrics.density
        val barWidth=(available/columns-gap).coerceAtLeast(2f)
        val barHeight=heightPx*0.22f
        val top1=heightPx*0.17f
        val top2=heightPx*0.59f
        paint.color=Color.rgb(163,172,183)
        paint.textSize=max(13f,heightPx*0.18f)
        canvas.drawText("L",padding/2,top1+barHeight*0.88f,paint)
        canvas.drawText("R",padding/2,top2+barHeight*0.88f,paint)
        val active=(sqrt(level.toDouble()/32767.0)*columns)
            .toInt().coerceIn(0,columns)
        for(i in 0 until columns) {
            val left=labelWidth+i*available/columns
            paint.color=if(i>=active)dim else if(i>columns*0.87f)red else lit
            val radius=(barWidth*0.36f).coerceAtMost(4f)
            canvas.drawRoundRect(left,top1,left+barWidth,top1+barHeight,
                radius,radius,paint)
            canvas.drawRoundRect(left,top2,left+barWidth,top2+barHeight,
                radius,radius,paint)
        }
    }
}
