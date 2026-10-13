package dev.jesiel.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Widget criado pelo usuário em HTML/CSS (e opcionalmente JavaScript).
 *
 * - [interactive] falso ("leve"): sem JavaScript; o Launcher troca as variáveis e redesenha só
 *   quando algum valor muda (no máximo uma vez por minuto para a hora).
 * - [interactive] verdadeiro: JavaScript ligado; os dados chegam por `onLauncherData(dados)`.
 *
 * Em ambos os casos o widget fica pausado quando a tela inicial não está visível.
 */
data class HtmlWidget(
    val id: String,
    val name: String,
    val html: String,
    val interactive: Boolean = false,
    val network: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("html", html)
        .put("interactive", interactive).put("network", network)

    companion object {
        fun fromJson(o: JSONObject) = HtmlWidget(
            id = o.getString("id"),
            name = o.optString("name", "Widget HTML"),
            html = o.optString("html"),
            interactive = o.optBoolean("interactive", false),
            network = o.optBoolean("network", false),
        )

        fun listFromJson(text: String?): List<HtmlWidget> = runCatching {
            val a = JSONArray(text ?: "[]")
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        fun listToJson(list: List<HtmlWidget>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

/** Variáveis disponíveis nos widgets HTML. */
object HtmlVariables {
    val all = listOf(
        "hora" to "Hora (HH:mm)",
        "data" to "Data curta (ter., 13 de out.)",
        "diaSemana" to "Dia da semana",
        "dia" to "Dia do mês",
        "mes" to "Mês por extenso",
        "ano" to "Ano",
        "saudacao" to "Bom dia / Boa tarde / Boa noite",
        "bateria" to "Bateria (0–100)",
        "carregando" to "\"Carregando\" ou vazio",
        "alarme" to "Próximo alarme ou vazio",
        "memoria" to "Memória em uso (%)",
        "armazenamento" to "Armazenamento livre (GB)",
        "tema" to "claro ou escuro",
        "cor" to "Cor de destaque do tema (#rrggbb)",
    )

    private val PLACEHOLDER = Regex("""\{\{\s*([a-zA-Z]+)\s*\}\}""")
    private val TAG = Regex("""<[^>]*>""")

    /** Nomes de variáveis usadas no HTML (para calcular só o necessário). */
    fun used(html: String): Set<String> = PLACEHOLDER.findAll(html).map { it.groupValues[1] }.toSet() +
        all.map { it.first }.filter { html.contains("launcher.$it") || html.contains("--$it") }

    /**
     * Monta o documento final: troca `{{variavel}}` pelo valor, expõe as variáveis também como
     * propriedades CSS (`var(--bateria)`) e, no modo interativo, como o objeto `launcher` em JS.
     * Links `launcher://...` são tratados pelo Launcher.
     */
    fun render(html: String, values: Map<String, String>, interactive: Boolean): String {
        fun escape(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val body = buildString {
            var last = 0
            // Fora das tags o valor entra como texto marcado (para atualizar sem recarregar);
            // dentro das tags (atributos), como valor puro.
            TAG.findAll(html).forEach { tag ->
                append(replaceText(html.substring(last, tag.range.first), values, ::escape, interactive))
                append(PLACEHOLDER.replace(tag.value) { m -> escape(values[m.groupValues[1]].orEmpty()) })
                last = tag.range.last + 1
            }
            append(replaceText(html.substring(last), values, ::escape, interactive))
        }
        val cssVars = values.entries.joinToString(";") { (k, v) -> "--$k:${cssValue(v)}" }
        val head = """
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>:root{$cssVars}html,body{margin:0;padding:0;background:transparent;overflow:hidden;height:100%}</style>
        """.trimIndent()
        val script = if (!interactive) "" else """
            <script>
            window.launcher = ${JSONObject(values as Map<*, *>)};
            window.__launcherUpdate = function(d){
              window.launcher = d;
              document.querySelectorAll('[data-lv]').forEach(function(e){ e.textContent = d[e.getAttribute('data-lv')] || ''; });
              for (var k in d) document.documentElement.style.setProperty('--'+k, d[k]);
              if (typeof window.onLauncherData === 'function') window.onLauncherData(d);
            };
            document.addEventListener('DOMContentLoaded', function(){ if (typeof window.onLauncherData === 'function') window.onLauncherData(window.launcher); });
            </script>
        """.trimIndent()
        return "<!doctype html><html><head>$head$script</head><body>$body</body></html>"
    }

    private fun replaceText(text: String, values: Map<String, String>, escape: (String) -> String, interactive: Boolean) =
        PLACEHOLDER.replace(text) { m ->
            val key = m.groupValues[1]
            val v = escape(values[key].orEmpty())
            if (interactive) "<span data-lv=\"$key\">$v</span>" else v
        }

    /** Números ficam como número (para calc()); textos, entre aspas. */
    private fun cssValue(v: String) = if (v.toDoubleOrNull() != null || v.startsWith("#")) v else "\"" + v.replace("\"", "'") + "\""
}

/** Modelos prontos para começar. */
object HtmlTemplates {
    val all: List<Pair<String, String>> = listOf(
        "Relógio" to """
<div style="height:100%;display:flex;flex-direction:column;justify-content:center;padding:16px;box-sizing:border-box;
  border-radius:24px;background:linear-gradient(135deg,rgba(255,255,255,.18),rgba(255,255,255,.04));
  color:#fff;font-family:sans-serif;border:1px solid rgba(255,255,255,.25)">
  <div style="font-size:64px;font-weight:200;line-height:1">{{hora}}</div>
  <div style="font-size:16px;opacity:.85;margin-top:6px">{{saudacao}} · {{data}}</div>
</div>""".trimIndent(),
        "Bateria" to """
<div style="height:100%;display:flex;align-items:center;gap:18px;padding:16px;box-sizing:border-box;border-radius:24px;
  background:rgba(0,0,0,.35);color:#fff;font-family:sans-serif">
  <svg viewBox="0 0 36 36" style="height:80%;aspect-ratio:1">
    <circle cx="18" cy="18" r="15.9" fill="none" stroke="rgba(255,255,255,.2)" stroke-width="3"/>
    <circle cx="18" cy="18" r="15.9" fill="none" stroke="{{cor}}" stroke-width="3" stroke-linecap="round"
      stroke-dasharray="{{bateria}} 100" transform="rotate(-90 18 18)"/>
    <text x="18" y="21" text-anchor="middle" font-size="9" fill="#fff">{{bateria}}%</text>
  </svg>
  <div><div style="font-size:20px">Bateria</div><div style="opacity:.75">{{carregando}}</div></div>
</div>""".trimIndent(),
        "Dia" to """
<div style="height:100%;padding:16px;box-sizing:border-box;border-radius:24px;background:#fff;color:#1c1c1e;font-family:sans-serif">
  <div style="color:#ff3b30;font-weight:600;text-transform:capitalize">{{diaSemana}}</div>
  <div style="font-size:52px;line-height:1.1">{{dia}}</div>
  <div style="opacity:.6">{{mes}} de {{ano}}</div>
  <div style="margin-top:8px;font-size:13px">⏰ {{alarme}}</div>
</div>""".trimIndent(),
        "Atalhos (interativo)" to """
<style>
  .g{height:100%;display:grid;grid-template-columns:repeat(2,1fr);gap:10px;padding:10px;box-sizing:border-box;font-family:sans-serif}
  a{display:flex;align-items:center;justify-content:center;border-radius:18px;background:rgba(255,255,255,.18);
    color:#fff;text-decoration:none;font-size:15px;transition:transform .15s}
  a:active{transform:scale(.94)}
</style>
<div class="g">
  <a href="launcher://app/com.whatsapp">WhatsApp</a>
  <a href="launcher://app/com.android.chrome">Chrome</a>
  <a href="launcher://painel">Painel</a>
  <a href="launcher://apps">Apps</a>
</div>
<script>function onLauncherData(d){ /* d.hora, d.bateria, ... */ }</script>""".trimIndent(),
    )
}
