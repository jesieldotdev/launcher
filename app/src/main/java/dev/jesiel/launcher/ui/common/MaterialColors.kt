package dev.jesiel.launcher.ui.common

import android.content.Context
import android.os.Build

/**
 * Cores do Material You (paleta dinâmica que o Android 12+ tira do papel de parede), nos papéis do
 * Material 3, para os widgets HTML. Em versões antigas, parte da cor de destaque do tema.
 */
object MaterialColors {
    val names = listOf(
        "corPrimaria", "corPrimariaFundo", "corSecundaria", "corTerciaria",
        "corFundo", "corSuperficie", "corTexto", "corTextoSuave", "corContorno",
    )

    fun of(context: Context, dark: Boolean, fallbackAccent: Int): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback(dark, fallbackAccent)
        fun c(id: Int) = hex(context.getColor(id))
        return runCatching {
            if (dark) mapOf(
                "corPrimaria" to c(android.R.color.system_accent1_200),
                "corPrimariaFundo" to c(android.R.color.system_accent1_700),
                "corSecundaria" to c(android.R.color.system_accent2_200),
                "corTerciaria" to c(android.R.color.system_accent3_200),
                "corFundo" to c(android.R.color.system_neutral1_900),
                "corSuperficie" to c(android.R.color.system_neutral1_800),
                "corTexto" to c(android.R.color.system_neutral1_100),
                "corTextoSuave" to c(android.R.color.system_neutral2_200),
                "corContorno" to c(android.R.color.system_neutral2_400),
            ) else mapOf(
                "corPrimaria" to c(android.R.color.system_accent1_600),
                "corPrimariaFundo" to c(android.R.color.system_accent1_100),
                "corSecundaria" to c(android.R.color.system_accent2_600),
                "corTerciaria" to c(android.R.color.system_accent3_600),
                "corFundo" to c(android.R.color.system_neutral1_10),
                "corSuperficie" to c(android.R.color.system_neutral1_50),
                "corTexto" to c(android.R.color.system_neutral1_900),
                "corTextoSuave" to c(android.R.color.system_neutral2_700),
                "corContorno" to c(android.R.color.system_neutral2_500),
            )
        }.getOrElse { fallback(dark, fallbackAccent) }
    }

    private fun fallback(dark: Boolean, accent: Int): Map<String, String> {
        val a = hex(accent)
        return if (dark) mapOf(
            "corPrimaria" to a, "corPrimariaFundo" to mix(accent, 0x000000, 0.65f), "corSecundaria" to a, "corTerciaria" to a,
            "corFundo" to "#121316", "corSuperficie" to "#1E1F23", "corTexto" to "#E4E2E6", "corTextoSuave" to "#C5C6D0", "corContorno" to "#8F9099",
        ) else mapOf(
            "corPrimaria" to a, "corPrimariaFundo" to mix(accent, 0xFFFFFF, 0.8f), "corSecundaria" to a, "corTerciaria" to a,
            "corFundo" to "#FBF8FD", "corSuperficie" to "#F3F0F4", "corTexto" to "#1B1B1F", "corTextoSuave" to "#45464F", "corContorno" to "#757780",
        )
    }

    private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

    private fun mix(a: Int, b: Int, t: Float): String {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
        return "#%02X%02X%02X".format(ch(16), ch(8), ch(0))
    }
}
