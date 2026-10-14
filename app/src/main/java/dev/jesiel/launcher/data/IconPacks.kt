package dev.jesiel.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Um pacote de ícones instalado (no formato usado por Nova, ADW, Microsoft Launcher e outros). */
data class IconPack(val packageName: String, val label: String, val icon: ImageBitmap?)

/**
 * Pacotes de ícones: encontra os instalados e troca o ícone de cada app pelo do pacote.
 *
 * O pacote declara, no `appfilter.xml`, qual imagem usa para cada app
 * (`<item component="ComponentInfo{pacote/Atividade}" drawable="nome"/>`). Para os apps que ele
 * não cobre, pode trazer um fundo, uma máscara e uma moldura (`iconback`, `iconmask`, `iconupon`)
 * e uma escala, para o ícone original combinar com os outros.
 */
object IconPacks {
    /** Ações com que os pacotes de ícones se anunciam aos launchers. */
    val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "org.adw.launcher.icons.ACTION_PICK_ICON",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
        "com.fede.launcher.THEME_ICONPACK",
        "com.dlto.atom.launcher.THEME",
    )

    fun installed(context: Context): List<IconPack> {
        val pm = context.packageManager
        val packages = LinkedHashMap<String, IconPack>()
        ACTIONS.forEach { action ->
            val found = runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            found.forEach { info ->
                val pkg = info.activityInfo.packageName
                if (pkg !in packages && pkg != context.packageName) {
                    val app = info.activityInfo.applicationInfo
                    packages[pkg] = IconPack(
                        packageName = pkg,
                        label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(pkg),
                        icon = runCatching { pm.getApplicationIcon(app).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                    )
                }
            }
        }
        return packages.values.sortedBy { it.label.lowercase() }
    }

    /** Abre a Play Store numa busca por pacotes de ícones. */
    fun storeIntent(): Intent =
        Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://search?q=icon%20pack&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Ícones de um pacote já lido. Use [load]; nulo se o pacote não existe ou não tem appfilter. */
class IconPackLoader private constructor(
    private val res: Resources,
    private val packageName: String,
    private val byComponent: Map<String, String>,
    private val byPackage: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    private val scale: Float,
) {
    private val density = res.displayMetrics.densityDpi

    /** Quantos apps o pacote cobre (para mostrar nos ajustes). */
    val size: Int get() = byComponent.size

    /**
     * Ícone do pacote para [component]; para apps sem ícone no pacote, o original no estilo do
     * pacote (se [styleUnthemed]) ou nulo (fica o do sistema).
     */
    fun iconFor(component: ComponentName, original: () -> Drawable, sizePx: Int, styleUnthemed: Boolean): Bitmap? {
        val name = byComponent[component.flattenToString()] ?: byPackage[component.packageName]
        if (name != null) drawable(name)?.let { return it.toBitmap(sizePx, sizePx) }
        if (!styleUnthemed || (backs.isEmpty() && mask == null && upon == null)) return null
        return runCatching { styled(original(), component, sizePx) }.getOrNull()
    }

    /** Fundo do pacote, ícone reduzido e recortado pela máscara, moldura por cima. */
    private fun styled(original: Drawable, component: ComponentName, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val inner = (sizePx * scale).toInt().coerceAtLeast(1)
        val offset = (sizePx - inner) / 2f
        canvas.drawBitmap(original.toBitmap(inner, inner), offset, offset, null)
        // A máscara marca o que sai do ícone (as partes opacas são recortadas).
        mask?.let(::drawable)?.let { m ->
            canvas.drawBitmap(m.toBitmap(sizePx, sizePx), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        // Com vários fundos, cada app recebe sempre o mesmo.
        backs.takeIf { it.isNotEmpty() }?.get(Math.floorMod(component.packageName.hashCode(), backs.size))?.let(::drawable)?.let { b ->
            canvas.drawBitmap(b.toBitmap(sizePx, sizePx), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER) })
        }
        upon?.let(::drawable)?.let { u -> canvas.drawBitmap(u.toBitmap(sizePx, sizePx), 0f, 0f, null) }
        return out
    }

    private fun drawable(name: String): Drawable? = runCatching {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
            ?: return null
        res.getDrawableForDensity(id, density, null)
    }.getOrNull()

    companion object {
        private val COMPONENT = Regex("""ComponentInfo\{([^}]+)\}""")

        fun load(context: Context, packageName: String): IconPackLoader? = runCatching {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val f = parse(openAppFilter(res, packageName) ?: return null)
            IconPackLoader(res, packageName, f.byComponent, f.byPackage, f.backs, f.mask, f.upon, f.scale)
        }.getOrNull()

        /** O que o appfilter.xml declara. */
        class AppFilter(
            val byComponent: Map<String, String>,
            val byPackage: Map<String, String>,
            val backs: List<String>,
            val mask: String?,
            val upon: String?,
            val scale: Float,
        )

        fun parse(parser: XmlPullParser): AppFilter {
            val byComponent = HashMap<String, String>()
            val byPackage = HashMap<String, String>()
            val backs = ArrayList<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    fun attr(n: String): String? = parser.getAttributeValue(null, n)
                    // Imagens de fundo/máscara/moldura vêm em img1, img2…
                    fun images(): List<String> = (0 until parser.attributeCount).filter { parser.getAttributeName(it).startsWith("img") }.map { parser.getAttributeValue(it) }
                    when (parser.name) {
                        "item" -> {
                            val drawable = attr("drawable")
                            val component = attr("component")?.let { COMPONENT.find(it)?.groupValues?.get(1) }
                                ?.let(ComponentName::unflattenFromString)
                            if (!drawable.isNullOrBlank() && component != null) {
                                byComponent.putIfAbsent(component.flattenToString(), drawable)
                                // Se a atividade mudar numa atualização do app, o pacote ainda casa.
                                byPackage.putIfAbsent(component.packageName, drawable)
                            }
                        }
                        "iconback" -> backs += images()
                        "iconmask" -> mask = images().firstOrNull()
                        "iconupon" -> upon = images().firstOrNull()
                        "scale" -> scale = attr("factor")?.toFloatOrNull()?.coerceIn(0.2f, 1.5f) ?: 1f
                    }
                }
                event = parser.next()
            }
            return AppFilter(byComponent, byPackage, backs, mask, upon, scale)
        }

        /** O appfilter fica em res/xml ou em assets, conforme o pacote. */
        private fun openAppFilter(res: Resources, packageName: String): XmlPullParser? {
            val id = res.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) return res.getXml(id)
            val stream = runCatching { res.assets.open("appfilter.xml") }.getOrNull() ?: return null
            return XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "utf-8") }
        }
    }
}
