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
    /** Desenho por software: mais compatível em aparelhos onde o WebView fica em branco. */
    val software: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("html", html)
        .put("interactive", interactive).put("network", network).put("software", software)

    companion object {
        fun fromJson(o: JSONObject) = HtmlWidget(
            id = o.getString("id"),
            name = o.optString("name", "Widget HTML"),
            html = o.optString("html"),
            interactive = o.optBoolean("interactive", false),
            network = o.optBoolean("network", false),
            software = o.optBoolean("software", false),
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
    val RETRO = "Retrô futurista" to """
<style>
.r{position:relative;height:100%;overflow:hidden;border-radius:26px;font-family:"Trebuchet MS",Verdana,sans-serif;
  background:linear-gradient(180deg,#0b0221 0%,#240746 38%,#5b0f72 56%,#ff3c8e 63%,#1a0533 63.2%,#12022a 100%);
  box-shadow:inset 0 0 0 1px rgba(255,120,220,.35),inset 0 0 40px rgba(255,60,180,.25)}
.stars{position:absolute;inset:0 0 40% 0;opacity:.9;
  background-image:radial-gradient(1px 1px at 12% 18%,#fff 60%,transparent),radial-gradient(1px 1px at 28% 32%,#cdf 60%,transparent),
  radial-gradient(1.5px 1.5px at 44% 12%,#fff 60%,transparent),radial-gradient(1px 1px at 63% 26%,#fbe 60%,transparent),
  radial-gradient(1px 1px at 78% 10%,#fff 60%,transparent),radial-gradient(1.5px 1.5px at 90% 34%,#cff 60%,transparent),
  radial-gradient(1px 1px at 6% 40%,#fff 60%,transparent),radial-gradient(1px 1px at 54% 40%,#fff 60%,transparent)}
.sun{position:absolute;left:50%;top:63%;width:44vh;height:44vh;transform:translate(-50%,-54%);border-radius:50%;
  background:linear-gradient(180deg,#fff6a0 0%,#ffcf4a 28%,#ff8a3c 55%,#ff2d7a 85%);
  -webkit-mask:linear-gradient(180deg,#000 0 48%,transparent 48% 53%,#000 53% 61%,transparent 61% 67%,#000 67% 74%,transparent 74% 81%,#000 81% 87%,transparent 87% 94%,#000 94%);
  filter:drop-shadow(0 0 18px rgba(255,90,150,.8))}
.floor{position:absolute;left:-50%;right:-50%;bottom:0;height:37%;transform-origin:50% 0;transform:perspective(120px) rotateX(55deg);
  background:repeating-linear-gradient(90deg,rgba(255,80,200,.9) 0 2px,transparent 2px 9%),
             repeating-linear-gradient(0deg,rgba(80,240,255,.85) 0 2px,transparent 2px 22%);
  -webkit-mask:linear-gradient(180deg,transparent,#000 35%)}
.glow{position:absolute;left:0;right:0;top:61%;height:3%;background:linear-gradient(90deg,transparent,#ff7ad9,#fff,#ff7ad9,transparent);filter:blur(2px);opacity:.9}
.t{position:absolute;left:0;right:0;top:6%;text-align:center;font-weight:900;font-style:italic;letter-spacing:.04em;
  font-size:min(30vh,19vw);line-height:1;
  background:linear-gradient(180deg,#ffffff 0%,#b8f6ff 38%,#3a6dff 49%,#fff 51%,#ff9be8 72%,#ff4fb8 100%);
  -webkit-background-clip:text;background-clip:text;color:transparent;
  filter:drop-shadow(0 2px 0 #2b0b4f) drop-shadow(0 0 10px rgba(255,80,200,.65))}
.d{position:absolute;left:50%;top:calc(7% + min(31vh,20vw));transform:translateX(-50%);white-space:nowrap;color:#b9fbff;
  font-size:min(7vh,4.2vw);letter-spacing:.38em;text-transform:uppercase;padding:.25em .9em;border-radius:99px;
  background:rgba(11,2,33,.55);box-shadow:0 0 0 1px rgba(127,246,255,.45),0 0 12px rgba(24,224,255,.35);text-shadow:0 0 8px #18e0ff}
.b{position:absolute;padding:.2em .6em;border-radius:8px;background:rgba(18,2,42,.6);right:5%;bottom:7%;color:#ffd6f4;font-size:min(6vh,3.4vw);letter-spacing:.2em;text-shadow:0 0 6px #ff3ca8}
.l{position:absolute;padding:.2em .6em;border-radius:8px;background:rgba(18,2,42,.6);left:5%;bottom:7%;color:#ffd6f4;font-size:min(6vh,3.4vw);letter-spacing:.2em;text-shadow:0 0 6px #ff3ca8}
</style>
<div class="r">
  <div class="stars"></div><div class="sun"></div><div class="glow"></div><div class="floor"></div>
  <div class="t">{{hora}}</div>
  <div class="d">{{data}}</div>
  <div class="l">{{saudacao}}</div>
  <div class="b">PWR {{bateria}}%</div>
</div>""".trimIndent()

    val CYBERPUNK = "Cyberpunk" to """
<style>
.f{position:relative;height:100%;background:#fcee0a;filter:drop-shadow(0 0 8px rgba(252,238,10,.45));
  clip-path:polygon(0 0,calc(100% - 26px) 0,100% 26px,100% 100%,22px 100%,0 calc(100% - 22px))}
.c{position:absolute;inset:2px;box-sizing:border-box;padding:5% 6.5%;color:#e8f9ff;font-family:"Courier New",monospace;
  display:flex;flex-direction:column;justify-content:space-between;
  background:
    linear-gradient(115deg,rgba(255,42,109,.16),transparent 40%),
    repeating-linear-gradient(0deg,rgba(255,255,255,.035) 0 1px,transparent 1px 4px),
    linear-gradient(160deg,#07070f,#0d0b1e 60%,#160a24);
  clip-path:polygon(0 0,calc(100% - 25px) 0,100% 25px,100% 100%,21px 100%,0 calc(100% - 21px));
  overflow:hidden;box-shadow:inset 0 0 22px rgba(252,238,10,.18)}
.bar{position:absolute;left:0;top:0;width:38%;height:5px;background:#fcee0a;box-shadow:0 0 12px #fcee0a}
.tag{position:absolute;right:30px;top:5px;font-size:min(5vh,2.9vw);color:#07070f;background:#00f0ff;padding:1px 7px;letter-spacing:.15em;font-weight:700}
.hud{display:flex;justify-content:space-between;font-size:min(5.5vh,3.1vw);letter-spacing:.18em;color:#00f0ff;text-transform:uppercase;margin-top:4px}
.hud b{color:#ff2a6d;font-weight:700}
.clock{position:relative;font-weight:700;font-size:min(34vh,20vw);line-height:1;letter-spacing:.02em;color:#f5fbff;
  text-shadow:0 0 12px rgba(0,240,255,.55)}
.clock::before,.clock::after{content:attr(data-t);position:absolute;left:0;top:0}
.clock::before{color:#ff2a6d;transform:translate(-3px,0);clip-path:polygon(0 18%,100% 18%,100% 38%,0 38%);opacity:.9}
.clock::after{color:#00f0ff;transform:translate(3px,0);clip-path:polygon(0 64%,100% 64%,100% 78%,0 78%);opacity:.9}
.date{font-size:min(7vh,4vw);letter-spacing:.32em;text-transform:uppercase;color:#fcee0a;text-shadow:0 0 8px rgba(252,238,10,.6)}
.meter{display:flex;align-items:center;gap:10px;margin-right:26px;font-size:min(5.5vh,3.1vw);letter-spacing:.15em;color:#00f0ff}
.cells{flex:1;height:min(5vh,9px);background:rgba(0,240,255,.12);position:relative;
  -webkit-mask:repeating-linear-gradient(90deg,#000 0 calc(10% - 3px),transparent calc(10% - 3px) 10%)}
.cells i{position:absolute;inset:0;width:calc(var(--bateria) * 1%);background:linear-gradient(90deg,#ff2a6d,#fcee0a);box-shadow:0 0 10px #ff2a6d}
.corner{position:absolute;right:16px;bottom:12px;width:14px;height:14px;border-right:2px solid #ff2a6d;border-bottom:2px solid #ff2a6d}
</style>
<div class="f"><div class="c">
  <div class="bar"></div><div class="tag">NC-77</div>
  <div class="hud"><span>// net <b>online</b></span><span>mem {{memoria}}%</span></div>
  <div><div class="clock" data-t="{{hora}}">{{hora}}</div>
  <div class="date">{{diaSemana}} · {{dia}} {{mes}}</div></div>
  <div class="meter"><span>PWR</span><div class="cells"><i></i></div><span>{{bateria}}%</span></div>
  <div class="corner"></div>
</div></div>""".trimIndent()

    val all: List<Pair<String, String>> = listOf(
        RETRO,
        CYBERPUNK,
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
