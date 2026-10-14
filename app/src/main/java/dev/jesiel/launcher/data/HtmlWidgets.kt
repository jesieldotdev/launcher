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
    /** Na área de trabalho ou só guardado na biblioteca de widgets. */
    val onDesktop: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("html", html)
        .put("interactive", interactive).put("network", network).put("software", software).put("onDesktop", onDesktop)

    companion object {
        fun fromJson(o: JSONObject) = HtmlWidget(
            id = o.getString("id"),
            name = o.optString("name", "Widget HTML"),
            html = o.optString("html"),
            interactive = o.optBoolean("interactive", false),
            network = o.optBoolean("network", false),
            software = o.optBoolean("software", false),
            onDesktop = o.optBoolean("onDesktop", true),
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
        "ntfy" to "Canal do Claude Code no ntfy.sh",
        "horas" to "Só a hora (00–23)",
        "minutos" to "Só os minutos (00–59)",
        "semana" to "Semana do ano",
        "tempBateria" to "Temperatura da bateria (°C)",
        "rede" to "Wi-Fi, Dados móveis ou Sem conexão",
        "volume" to "Volume de mídia (0–100)",
        "ultimoApp" to "Último app aberto",
        "notificacoes" to "Notificações pendentes (precisa de acesso a notificações)",
        "musicaTitulo" to "Música: título",
        "musicaArtista" to "Música: artista",
        "musicaAlbum" to "Música: álbum",
        "musicaApp" to "Música: app que está tocando",
        "musicaTocando" to "\"sim\" tocando, vazio pausado",
        "musicaPosicao" to "Música: posição (1:23)",
        "musicaDuracao" to "Música: duração (3:45)",
        "musicaProgresso" to "Música: progresso (0–100)",
        "musicaCapa" to "Música: capa (use em <img src> ou url())",
        "corPrimaria" to "Material You: cor principal do sistema",
        "corPrimariaFundo" to "Material You: fundo da cor principal",
        "corSecundaria" to "Material You: cor secundária",
        "corTerciaria" to "Material You: cor terciária",
        "corFundo" to "Material You: fundo",
        "corSuperficie" to "Material You: superfície (cartões)",
        "corTexto" to "Material You: texto",
        "corTextoSuave" to "Material You: texto secundário",
        "corContorno" to "Material You: bordas e divisórias",
    )

    /** Nome em inglês de cada variável (mesmo valor), para quem prefere ou para código gerado por outras IAs. */
    val english: Map<String, String> = linkedMapOf(
        "hora" to "time", "data" to "date", "diaSemana" to "weekday", "dia" to "day", "mes" to "month", "ano" to "year",
        "saudacao" to "greeting", "bateria" to "battery", "carregando" to "charging", "alarme" to "alarm",
        "memoria" to "memory", "armazenamento" to "storage", "tema" to "theme", "cor" to "accent", "ntfy" to "ntfy",
        "horas" to "hours", "minutos" to "minutes", "semana" to "week", "tempBateria" to "batteryTemp",
        "rede" to "network", "volume" to "volume", "ultimoApp" to "lastApp", "notificacoes" to "notifications",
        "musicaTitulo" to "musicTitle", "musicaArtista" to "musicArtist", "musicaAlbum" to "musicAlbum",
        "musicaApp" to "musicApp", "musicaTocando" to "musicPlaying", "musicaPosicao" to "musicPosition",
        "musicaDuracao" to "musicDuration", "musicaProgresso" to "musicProgress", "musicaCapa" to "musicCover",
        "corPrimaria" to "colorPrimary", "corPrimariaFundo" to "colorPrimaryContainer", "corSecundaria" to "colorSecondary",
        "corTerciaria" to "colorTertiary", "corFundo" to "colorBackground", "corSuperficie" to "colorSurface",
        "corTexto" to "colorOnSurface", "corTextoSuave" to "colorOnSurfaceVariant", "corContorno" to "colorOutline",
    )
    private val fromEnglish: Map<String, String> = english.entries.associate { (pt, en) -> en to pt }

    /** Copia cada valor também com o nome em inglês. */
    fun withEnglish(values: Map<String, String>): Map<String, String> =
        values + values.mapNotNull { (k, v) -> english[k]?.takeIf { it != k }?.let { it to v } }

    /** Variáveis que dependem do acesso a notificações. */
    fun needsNotificationAccess(used: Set<String>) = used.any { it.startsWith("musica") || it == "notificacoes" }

    private val PLACEHOLDER = Regex("""\{\{\s*([a-zA-Z]+)\s*\}\}""")
    private val TAG = Regex("""<[^>]*>""")

    /**
     * Nomes (em português, os internos) das variáveis usadas no HTML, para calcular só o
     * necessário. Reconhece também os nomes em inglês.
     */
    fun used(html: String): Set<String> {
        fun mentioned(name: String) = html.contains("launcher.$name") || html.contains("--$name")
        val placeholders = PLACEHOLDER.findAll(html).map { it.groupValues[1] }.map { fromEnglish[it] ?: it }.toSet()
        return placeholders + all.map { it.first }.filter { pt -> mentioned(pt) || english[pt]?.let(::mentioned) == true }
    }

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
            <style>:root{$cssVars}html,body{margin:0;padding:0;background:transparent;overflow:hidden;height:100%}
            *{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;-webkit-tap-highlight-color:transparent}</style>
        """.trimIndent()
        val script = if (!interactive) "" else """
            <script>
            window.launcher = ${JSONObject(values as Map<*, *>)};
            window.__launcherUpdate = function(d){
              window.launcher = d;
              document.querySelectorAll('[data-lv]').forEach(function(e){ e.textContent = d[e.getAttribute('data-lv')] || ''; });
              document.querySelectorAll('[data-lsrc]').forEach(function(e){ var v = d[e.getAttribute('data-lsrc')] || ''; if (e.getAttribute('src') !== v) e.setAttribute('src', v); });
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


    /**
     * Painel do Claude Code: lê o canal do ntfy.sh (preenchido pelos hooks em .claude/) e o
     * status do GitHub Actions. Só consulta a rede enquanto a tela inicial está visível.
     */
    val CLAUDE = "Claude Code" to """
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Orbitron:wght@500;700;900&display=swap">
<style>
.k{position:relative;height:100%;box-sizing:border-box;padding:4% 5.5% 3.5%;border-radius:min(9vh,34px);overflow:hidden;
  display:flex;flex-direction:column;color:#e9c27a;font-family:Orbitron,"Eurostile","Courier New",monospace;
  background:radial-gradient(120% 90% at 50% 45%,rgba(40,30,12,.38) 0%,rgba(16,13,7,.46) 60%,rgba(10,8,5,.58) 100%);
  box-shadow:inset 0 0 0 2px #7d6136,inset 0 0 30px rgba(255,170,60,.10),inset 0 0 40px rgba(0,0,0,.35);
  text-shadow:0 1px 3px rgba(0,0,0,.6)}
.k::after{content:"";position:absolute;inset:0;pointer-events:none;
  background:repeating-linear-gradient(180deg,rgba(255,200,120,.035) 0 1px,transparent 1px 4px)}
.row{display:flex;justify-content:space-between;align-items:center;gap:8px;white-space:nowrap}
.hd{font-size:min(4.6vh,2.6vw);letter-spacing:.12em;font-weight:500;text-transform:uppercase;text-decoration:none;color:inherit}
.hd i{display:inline-block;width:.75em;height:.6em;background:#e9c27a;margin-right:.55em;vertical-align:.05em}
.hr{height:1px;flex:none;background:linear-gradient(90deg,#6b5330,#4a3a22);margin:2.2% 0}
.st{align-items:flex-end}
.big{text-shadow:none;font-weight:900;font-size:min(15vh,8.5vw);line-height:1;white-space:nowrap;letter-spacing:.02em;
  background:repeating-linear-gradient(180deg,#ffd98c 0 2px,rgba(255,206,120,.45) 2px 3px);
  -webkit-background-clip:text;background-clip:text;color:transparent;
  filter:drop-shadow(0 0 5px rgba(255,190,90,.55)) drop-shadow(0 0 16px rgba(255,170,60,.25));transform-origin:left bottom}
.big.err{background:repeating-linear-gradient(180deg,#ff8a6a 0 2px,rgba(255,110,80,.45) 2px 3px);-webkit-background-clip:text;background-clip:text;
  filter:drop-shadow(0 0 6px rgba(255,90,60,.6))}
.big.wait{animation:pulse 1.4s ease-in-out infinite}
.big.work{animation:pulse 2.6s ease-in-out infinite}
@keyframes pulse{50%{opacity:.45}}
.when{text-align:right;font-size:min(4.2vh,2.4vw);color:#c9a464;line-height:1.35}
.when b{display:block;font-size:min(5.4vh,3vw);color:#e9c27a;font-weight:700}
.msg{font-size:min(4.6vh,2.6vw);line-height:1.35;margin-top:1.6%;color:#f0d49a;
  display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.log{flex:1;min-height:0;overflow-y:auto;font-size:min(3.9vh,2.2vw);line-height:1.55;color:#a88a55;scrollbar-width:none;
  -webkit-mask:linear-gradient(180deg,#000 80%,transparent);mask:linear-gradient(180deg,#000 80%,transparent);padding-bottom:6%}
.log::-webkit-scrollbar{display:none}
.log div{display:flex;gap:.6em;white-space:nowrap}
.log span:last-child{overflow:hidden;text-overflow:ellipsis}
.log .t{color:#7d6136;flex:none}
.log .l{flex:none;width:5.6em;font-weight:700}
.log .ok .l{color:#e9c27a}.log .bad .l{color:#ff7a5a}.log .wait .l{color:#fff0c0}.log .stop .l{color:#b8955a}.log .work .l{color:#8fd0c0}
.ft{font-size:min(4.2vh,2.4vw);letter-spacing:.1em;color:#c9a464;text-transform:uppercase}
.dot{display:inline-block;width:.8em;height:.8em;border-radius:50%;background:#c9a464;margin-right:.6em;vertical-align:-.05em}
.dot.run{animation:pulse 1s infinite}.dot.bad{background:#ff6a4a;box-shadow:0 0 8px #ff6a4a}.dot.ok{background:#ffd98c;box-shadow:0 0 8px #ffbe5a}
.cells{display:inline-flex;gap:2px;margin-left:.6em;vertical-align:middle}
.cells b{width:min(2.2vh,1.2vw);height:min(3vh,1.7vw);border:1px solid #6b5330}
.cells b.ok{background:#e9c27a}.cells b.bad{background:#ff6a4a}.cells b.stop{background:#7d6136}.cells b.wait{background:#fff0c0}.cells b.work{background:#4f8f80}
</style>
<div class="k">
  <a class="row hd" href="launcher://url?u=https%3A%2F%2Fclaude.ai%2Fcode"><span><i></i>Claude Code</span><span id="repo">cc_01</span></a>
  <div class="hr"></div>
  <div class="row st"><div class="big" id="big">----</div><div class="when"><b id="hm"></b><span id="ago"></span></div></div>
  <div class="msg" id="msg">Conectando…</div>
  <div class="hr"></div>
  <div class="log" id="log"></div>
  <div class="row ft"><span><span class="dot" id="cid"></span><span id="ci">CI —</span></span><span><span id="n">0</span> em 24h<span class="cells" id="cells"></span></span></div>
</div>
<script>
var TOPIC = (window.launcher && launcher.ntfy) || '';      // canal salvo em Personalizar › Claude Code
var SERVER = 'https://ntfy.sh';
var REPO = 'jesieldotdev/launcher';
function onLauncherData(d){ if ((d.ntfy || '') != TOPIC) { TOPIC = d.ntfy || ''; events = []; sig = ''; lastNtfy = 0; } }
var events = [], lastNtfy = 0, lastCi = 0, ntfyFail = false, sig = '';

function kind(e){
  var t = e.tags || [];
  if (t.indexOf('wait') >= 0) return 'wait';
  if (t.indexOf('work') >= 0) return 'work';
  if (t.indexOf('build') >= 0) return t.indexOf('x') >= 0 ? 'bad' : 'ok';
  return 'stop';
}
var LABEL = {wait:'SUA VEZ', work:'RODANDO', bad:'FALHOU', ok:'BUILD OK', stop:'PRONTO'};
function ago(t){
  var s = Math.max(0, Math.floor(Date.now()/1000 - t));
  if (s < 60) return 'agora';
  if (s < 3600) return 'há ' + Math.floor(s/60) + ' min';
  if (s < 86400) return 'há ' + Math.floor(s/3600) + ' h';
  return 'há ' + Math.floor(s/86400) + ' d';
}
function hhmm(t){ var d = new Date(t*1000); return ('0'+d.getHours()).slice(-2) + ':' + ('0'+d.getMinutes()).slice(-2); }
function esc(s){ return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;'); }
function el(id){ return document.getElementById(id); }
function fit(){
  var b = el('big'); b.style.transform = 'none';
  var w = b.parentNode.clientWidth - el('hm').parentNode.offsetWidth - 10, sw = b.scrollWidth;
  if (sw > w && w > 0) b.style.transform = 'scale(' + (w / sw) + ')';
}
function draw(){
  var big = el('big');
  big.className = 'big';
  if (!TOPIC) {
    big.textContent = 'SEM CANAL'; el('msg').textContent = 'Defina o canal em Personalizar › Claude Code';
    el('hm').textContent = ''; el('ago').textContent = '';
  } else if (!events.length) {
    big.textContent = ntfyFail ? 'OFFLINE' : 'OCIOSO';
    el('msg').textContent = ntfyFail ? 'Sem conexão com o ntfy' : 'Nada nas últimas 24 h';
    el('hm').textContent = ''; el('ago').textContent = '';
  } else {
    var e = events[events.length-1], k = kind(e);
    big.textContent = LABEL[k];
    if (k == 'bad') big.className = 'big err';
    if (k == 'wait') big.className = 'big wait';
    if (k == 'work') big.className = 'big work';
    el('msg').textContent = e.message || e.title || '';
    el('hm').textContent = hhmm(e.time); el('ago').textContent = ago(e.time);
    var t = (e.tags || []); var repo = t[t.length-1];
    if (repo && repo != 'stop' && repo != 'wait' && repo != 'build') el('repo').textContent = repo + ' / cc';
  }
  // A lista só é refeita quando chegam eventos novos, para não perder a rolagem.
  var s = events.length + ':' + (events.length ? events[events.length-1].id : '');
  if (s != sig) {
    sig = s;
    el('log').innerHTML = events.slice(0, -1).reverse().map(function(e){
      var k = kind(e);
      return '<div class="' + k + '"><span class="t">' + hhmm(e.time) + '</span><span class="l">' + LABEL[k] + '</span><span>' + esc(e.message || e.title) + '</span></div>';
    }).join('') || '<div><span>Sem histórico</span></div>';
    el('cells').innerHTML = events.slice(-10).map(function(e){ return '<b class="' + kind(e) + '"></b>'; }).join('');
    el('n').textContent = events.length;
  }
  fit();
}
function pollNtfy(){
  if (!TOPIC) return;
  fetch(SERVER + '/' + encodeURIComponent(TOPIC) + '/json?poll=1&since=24h').then(function(r){ return r.text(); }).then(function(txt){
    events = txt.split('\n').filter(Boolean).map(function(l){ try { return JSON.parse(l); } catch(x){ return null; } })
      .filter(function(e){ return e && e.event == 'message'; });
    ntfyFail = false; draw();
  }).catch(function(){ ntfyFail = true; draw(); });
}
function pollCi(){
  fetch('https://api.github.com/repos/' + REPO + '/actions/runs?per_page=1').then(function(r){ return r.json(); }).then(function(j){
    var run = j.workflow_runs && j.workflow_runs[0];
    if (!run) return;
    var dot = el('cid'), txt;
    if (run.status != 'completed') { dot.className = 'dot run'; txt = 'CI rodando'; }
    else if (run.conclusion == 'success') { dot.className = 'dot ok'; txt = 'CI ok'; }
    else { dot.className = 'dot bad'; txt = 'CI ' + (run.conclusion || 'erro'); }
    el('ci').textContent = txt + ' · ' + hhmm(Date.parse(run.updated_at)/1000);
  }).catch(function(){});
}
function tick(){
  var now = Date.now();
  if (now - lastNtfy > 45000) { lastNtfy = now; pollNtfy(); }
  if (now - lastCi > 300000) { lastCi = now; pollCi(); }
  draw();
}
document.addEventListener('visibilitychange', function(){ if (!document.hidden) { lastNtfy = 0; tick(); } });
window.addEventListener('resize', fit);
tick(); setInterval(tick, 15000);
</script>""".trimIndent()

    /** Música tocando agora: capa, faixa, progresso e controles (precisa de acesso a notificações). */
    val MUSIC = "Música" to """
<style>
.m{position:relative;height:100%;box-sizing:border-box;border-radius:24px;overflow:hidden;color:#fff;font-family:sans-serif;
  display:flex;align-items:center;gap:4%;padding:4% 5%;background:rgba(20,20,26,.55);border:1px solid rgba(255,255,255,.18)}
.bg{position:absolute;inset:-20%;background-size:cover;background-position:center;filter:blur(22px) saturate(1.4);opacity:.55;z-index:0}
.m>*:not(.bg):not(.app){position:relative;z-index:1}
.cover{height:78%;aspect-ratio:1;border-radius:14px;background:rgba(255,255,255,.12) center/cover;flex:none;
  box-shadow:0 6px 18px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center;font-size:min(14vh,8vw)}
.info{flex:1;min-width:0;display:flex;flex-direction:column;justify-content:center;height:100%}
.t{font-weight:700;font-size:min(10vh,5.2vw);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.a{opacity:.8;font-size:min(7.5vh,4vw);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px}
.bar{height:4px;border-radius:2px;background:rgba(255,255,255,.25);margin:6% 0 2%}
.bar i{display:block;height:100%;border-radius:2px;background:#fff;width:0;transition:width 1s linear}
.time{display:flex;justify-content:space-between;font-size:min(6vh,3.2vw);opacity:.75}
.ctl{display:flex;justify-content:space-between;align-items:center;margin-top:3%;width:82%}
.ctl a{color:#fff;text-decoration:none;font-size:min(11vh,6vw);line-height:1;padding:2px 6px}
.ctl a.p{font-size:min(14vh,7.5vw)}
.ctl a:active{opacity:.5}
.app{position:absolute;right:5%;top:6%;font-size:min(5.5vh,3vw);opacity:.6;z-index:1}
</style>
<div class="m">
  <div class="bg" id="bg"></div>
  <a class="cover" id="cover" href="launcher://musica">♪</a>
  <div class="info">
    <div class="t" id="t">Nada tocando</div>
    <div class="a" id="a">Abra um app de música</div>
    <div class="bar"><i id="bar"></i></div>
    <div class="time"><span id="pos"></span><span id="dur"></span></div>
    <div class="ctl">
      <a href="launcher://musica/anterior">⏮</a>
      <a class="p" id="play" href="launcher://musica/alternar">▶</a>
      <a href="launcher://musica/proxima">⏭</a>
    </div>
  </div>
  <div class="app" id="app"></div>
</div>
<script>
// Variáveis usadas: launcher.musicaTitulo, launcher.musicaArtista, launcher.musicaApp, launcher.musicaTocando,
// launcher.musicaAlbum, launcher.musicaPosicao, launcher.musicaDuracao, launcher.musicaProgresso, launcher.musicaCapa
var lastCover = null;
function onLauncherData(d){
  var has = !!d.musicaTitulo;
  document.getElementById('t').textContent = has ? d.musicaTitulo : 'Nada tocando';
  document.getElementById('a').textContent = has ? (d.musicaArtista || d.musicaAlbum || '') : 'Abra um app de música';
  document.getElementById('app').textContent = d.musicaApp || '';
  document.getElementById('play').textContent = d.musicaTocando ? '⏸' : '▶';
  document.getElementById('bar').style.width = (d.musicaProgresso || 0) + '%';
  document.getElementById('pos').textContent = d.musicaPosicao || '';
  document.getElementById('dur').textContent = d.musicaDuracao || '';
  if (d.musicaCapa !== lastCover) {
    lastCover = d.musicaCapa;
    var c = document.getElementById('cover');
    c.style.backgroundImage = d.musicaCapa ? 'url(' + d.musicaCapa + ')' : '';
    c.textContent = d.musicaCapa ? '' : '♪';
    document.getElementById('bg').style.backgroundImage = d.musicaCapa ? 'url(' + d.musicaCapa + ')' : '';
  }
}
</script>""".trimIndent()

    /** Relógio de bordo nas cores do Material You (acompanha o papel de parede e o modo claro/escuro). */
    val MATERIAL_CLOCK = "Relógio Material You" to """
<style>
  @import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;500;600;700&display=swap');

  * { box-sizing: border-box; }

  html, body {
    width: 100%;
    height: 100%;
    margin: 0;
    overflow: hidden;
    background: transparent;
  }

  .terminal {
    /* Diminua este valor para deixar mais transparente */
    --fundo: .55;
    /* Cores do Material You (mudam com o papel de parede e com o modo claro/escuro) */
    --cor: var(--corPrimaria);
    --texto: var(--corTexto);
    --suave: var(--corTextoSuave);

    width: 100%;
    height: 100%;
    position: relative;
    overflow: hidden;
    container-type: size;
    color: var(--cor);
    font-family: 'Orbitron', 'Courier New', monospace;
    border: 1px solid color-mix(in srgb, var(--cor) 35%, transparent);
    border-radius: 16px;
    background: transparent;
    box-shadow:
      inset 0 0 35px rgba(0, 0, 0, .25),
      inset 0 1px 0 color-mix(in srgb, var(--cor) 12%, transparent);
    isolation: isolate;
  }

  .terminal::before {
    content: "";
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    opacity: var(--fundo);
    background:
      radial-gradient(
        ellipse at center,
        var(--corPrimariaFundo) 0%,
        var(--corSuperficie) 65%,
        var(--corFundo) 100%
      );
  }

  .terminal::after {
    content: "";
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 2;
    background: repeating-linear-gradient(
      to bottom,
      transparent 0px,
      transparent 3px,
      rgba(0, 0, 0, .18) 3px,
      rgba(0, 0, 0, .18) 4px
    );
    box-shadow: inset 0 0 45px rgba(0, 0, 0, .2);
  }

  .layout {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    padding: 4cqh 4cqw;
  }

  .top, .bottom {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: clamp(10px, 4cqh, 22px);
    font-weight: 500;
    letter-spacing: .08em;
    text-shadow: 0 1px 4px rgba(0, 0, 0, .8);
  }

  .top {
    padding-bottom: 3cqh;
    border-bottom: 1px solid color-mix(in srgb, var(--cor) 25%, transparent);
  }

  .signal {
    display: inline-block;
    width: .55em;
    height: .55em;
    margin-right: .5em;
    background: var(--cor);
    box-shadow: 0 0 10px var(--cor);
  }

  .clock {
    flex: 1;
    min-height: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;
    font-size: min(25cqw, 47cqh);
    line-height: 1;
    font-weight: 600;
    letter-spacing: -.04em;
    padding-right: .04em;
    font-variant-numeric: tabular-nums;
    text-shadow:
      0 2px 5px rgba(0, 0, 0, .5),
      0 0 3px color-mix(in srgb, var(--cor) 60%, white),
      0 0 18px color-mix(in srgb, var(--cor) 45%, transparent),
      3px 0 0 color-mix(in srgb, var(--corTerciaria) 15%, transparent);
  }

  .date {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    padding-bottom: 3cqh;
    font-size: clamp(11px, 4.5cqh, 25px);
    font-weight: 400;
    text-shadow: 0 1px 4px rgba(0, 0, 0, .8);
  }

  .greeting {
    color: var(--texto);
  }

  .bottom {
    padding-top: 3cqh;
    border-top: 1px solid color-mix(in srgb, var(--cor) 25%, transparent);
    color: var(--suave);
    font-size: clamp(9px, 3.2cqh, 18px);
  }

  .battery {
    display: flex;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
  }

  .meter {
    width: 11cqw;
    height: .65em;
    padding: 2px;
    border: 1px solid color-mix(in srgb, var(--suave) 50%, transparent);
  }

  .meter-fill {
    height: 100%;
    width: clamp(0%, calc(var(--bateria, 0) * 1%), 100%);
    background: var(--cor);
    box-shadow: 0 0 8px color-mix(in srgb, var(--cor) 40%, transparent);
  }
</style>

<div class="terminal">
  <div class="layout">
    <div class="top">
      <span><span class="signal"></span>LOCAL TIME</span>
      <span>{{ano}} / CLOCK_01</span>
    </div>

    <div class="clock">{{hora}}</div>

    <div class="date">
      <span class="greeting">{{saudacao}}</span>
      <span>{{data}}</span>
    </div>

    <div class="bottom">
      <span>● SISTEMA ONLINE</span>
      <div class="battery">
        <span>BAT {{bateria}}%</span>
        <div class="meter">
          <div class="meter-fill"></div>
        </div>
      </div>
    </div>
  </div>
</div>""".trimIndent()

    val all: List<Pair<String, String>> = listOf(
        CLAUDE,
        MUSIC,
        MATERIAL_CLOCK,
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
