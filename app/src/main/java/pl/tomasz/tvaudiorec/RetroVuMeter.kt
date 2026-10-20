package pl.tomasz.tvaudiorec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max

/**
 * Retro stereo-style peak meter for the recording dashboard.
 * Both rows intentionally show the same capture peak: AudioRecord's live
 * capture reports a combined level rather than per-channel peaks.
 */
class RetroVuMeter(context:Context):View(context){
    var level:Int=0
        set(value){field=value.coerceAtLeast(0);invalidate()}
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val cream=Color.rgb(245,220,184)
    private val ember=Color.rgb(202,78,65)
    private val dim=Color.rgb(49,43,38)
    private val columns=35
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        val w=width.toFloat()
        val h=height.toFloat()
        if(w<=0f || h<=0f)return
        p.style=Paint.Style.FILL
        val labelWidth=28f*resources.displayMetrics.density
        val x0=labelWidth
        val usable=(w-x0-5f).coerceAtLeast(50f)
        val gap=4f
        val barWidth=(usable/columns-gap).coerceAtLeast(2f)
        val top1=h*0.12f
        val top2=h*0.56f
        val bh=h*0.24f
        p.color=cream
        p.textSize=max(14f,h*0.17f)
        c.drawText("L",3f,top1+bh*0.88f,p)
        c.drawText("R",3f,top2+bh*0.88f,p)
        // Monitored PCM amplitude is on a 0..32767 scale. Log-like
        // compression keeps quiet voices visible on a compact meter.
        val normalized=(kotlin.math.sqrt(level.toDouble()/32767.0)*
            columns).toInt().coerceIn(0,columns)
        for(i in 0 until columns) {
            val start=x0+i*usable/columns
            p.color=if(i<normalized) {
                if(i>columns*0.87f)cream else ember
            }else dim
            c.drawRoundRect(start,top1,start+barWidth,top1+bh,1.5f,1.5f,p)
            c.drawRoundRect(start,top2,start+barWidth,top2+bh,1.5f,1.5f,p)
        }
    }
}
