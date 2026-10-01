package com.gozcu.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object Renk {
    val ZEMIN = 0xFF0B0F14.toInt()
    val KART = 0xFF131B24.toInt()
    val CIZGI = 0xFF1F2A36.toInt()
    val YAZI = 0xFFE6EDF3.toInt()
    val SOLUK = 0xFF8B98A5.toInt()
    val YESIL = 0xFF3DDC97.toInt()
    val SARI = 0xFFFFB020.toInt()
    val KIRMIZI = 0xFFFF5C5C.toInt()
    val MAVI = 0xFF5CA8FF.toInt()
    val MOR = 0xFFB48CFF.toInt()
}

fun Context.dp(x: Int): Int = (x * resources.displayMetrics.density).toInt()
fun Context.dpf(x: Float): Float = x * resources.displayMetrics.density

fun yuvarlak(renk: Int, yaricap: Float, cizgi: Int = 0, kalinlik: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(renk)
    g.cornerRadius = yaricap
    if (kalinlik > 0) g.setStroke(kalinlik, cizgi)
    return g
}

fun turRengi(c: Cihaz): Int {
    if (c.uyari) return Renk.KIRMIZI
    return when (c.tespit?.tur) {
        Tur.DRONE -> Renk.SARI
        Tur.TAKIPCI -> Renk.MAVI
        Tur.GOZLUK -> Renk.MOR
        null -> Renk.SOLUK
    }
}

fun metin(ctx: Context, yazi: String, sp: Float, renk: Int, kalin: Boolean = false): TextView {
    val t = TextView(ctx)
    t.text = yazi
    t.textSize = sp
    t.setTextColor(renk)
    if (kalin) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

fun mesafe(rssi: Int): String {
    val d = 10.0.pow((-59.0 - rssi) / 25.0)
    return when {
        d < 1.0 -> "<1 m"
        d > 100.0 -> ">100 m"
        else -> "~${d.roundToInt()} m"
    }
}

fun gecen(ms: Long): String = when {
    ms < 5_000 -> "şimdi"
    ms < 60_000 -> "${ms / 1000} sn önce"
    else -> "${ms / 60_000} dk önce"
}

class RadarView(ctx: Context) : View(ctx) {
    var cihazlar: List<Cihaz> = emptyList()

    private val halka = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tarama = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nokta = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kutu = RectF()

    init {
        halka.style = Paint.Style.STROKE
        halka.color = Renk.CIZGI
        halka.strokeWidth = ctx.dpf(1f)
        tarama.style = Paint.Style.FILL
        nokta.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - context.dpf(8f)

        for (k in 1..3) canvas.drawCircle(cx, cy, r * k / 3f, halka)
        canvas.drawLine(cx - r, cy, cx + r, cy, halka)
        canvas.drawLine(cx, cy - r, cx, cy + r, halka)

        val aci = (SystemClock.uptimeMillis() % 4000L) / 4000f * 360f
        kutu.set(cx - r, cy - r, cx + r, cy + r)
        for (k in 0 until 10) {
            tarama.color = Color.argb((10 - k) * 5, 61, 220, 151)
            canvas.drawArc(kutu, aci - (k + 1) * 4f, 4f, true, tarama)
        }

        for (c in cihazlar) {
            val oran = ((-c.rssi - 35) / 60f).coerceIn(0.08f, 0.95f)
            val a = Math.toRadians(((c.anahtar.hashCode() and 0x7fffffff) % 360).toDouble())
            val x = cx + (r * oran * cos(a)).toFloat()
            val y = cy + (r * oran * sin(a)).toFloat()
            val nabiz = if (c.uyari) 1f + 0.3f * sin(SystemClock.uptimeMillis() / 200.0).toFloat() else 1f
            nokta.color = turRengi(c)
            canvas.drawCircle(x, y, context.dpf(6f) * nabiz, nokta)
        }

        nokta.color = Renk.YAZI
        canvas.drawCircle(cx, cy, context.dpf(3f), nokta)

        postInvalidateDelayed(40)
    }
}

class CihazAdaptoru(private val ctx: Context) : BaseAdapter() {
    var liste: List<Cihaz> = emptyList()

    override fun getCount(): Int = liste.size
    override fun getItem(p: Int): Any = liste[p]
    override fun getItemId(p: Int): Long = p.toLong()

    override fun getView(p: Int, eski: View?, kap: ViewGroup?): View {
        val c = liste[p]
        val renk = turRengi(c)
        val simdi = System.currentTimeMillis()

        val kart = LinearLayout(ctx)
        kart.orientation = LinearLayout.VERTICAL
        kart.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        kart.background = if (c.uyari) {
            yuvarlak(0x22FF5C5C, ctx.dpf(14f), Renk.KIRMIZI, ctx.dp(1))
        } else {
            yuvarlak(Renk.KART, ctx.dpf(14f), Renk.CIZGI, ctx.dp(1))
        }

        val ust = LinearLayout(ctx)
        ust.orientation = LinearLayout.HORIZONTAL
        ust.gravity = Gravity.CENTER_VERTICAL

        val isaret = View(ctx)
        isaret.background = yuvarlak(renk, ctx.dpf(5f))
        val isaretPar = LinearLayout.LayoutParams(ctx.dp(10), ctx.dp(10))
        isaretPar.rightMargin = ctx.dp(10)
        ust.addView(isaret, isaretPar)

        val baslik = metin(ctx, c.tespit?.ad ?: "Bilinmeyen", 15f, Renk.YAZI, true)
        ust.addView(baslik, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ust.addView(metin(ctx, "${c.rssi} dBm", 12f, Renk.SOLUK))
        kart.addView(ust)

        val satirlar = ArrayList<Pair<String, Int>>()
        satirlar.add("${c.tespit?.tur?.etiket ?: ""} · ${c.kaynak} · ${mesafe(c.rssi)} · ${gecen(simdi - c.sonGorulme)}" to Renk.SOLUK)

        val kimlik = c.kimlik
        if (kimlik != null) satirlar.add("Kimlik: $kimlik" to Renk.YAZI)

        val la = c.pilotLat
        val lo = c.pilotLon
        if (la != null && lo != null) {
            satirlar.add(String.format(Locale.US, "Pilot: %.5f, %.5f  (dokun, haritada aç)", la, lo) to Renk.SARI)
        }

        if (c.uyari && c.izler.size >= 2) {
            val dk = (c.izler.last().zaman - c.izler.first().zaman) / 60_000
            satirlar.add("Seninle birlikte hareket ediyor: $dk dk, ${c.izler.size} nokta" to Renk.KIRMIZI)
        }

        for ((yazi, yaziRenk) in satirlar) {
            val t = metin(ctx, yazi, 12f, yaziRenk)
            val par = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            par.topMargin = ctx.dp(4)
            kart.addView(t, par)
        }
        return kart
    }
}
