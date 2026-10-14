#!/usr/bin/env bash
# Envia eventos do Claude Code para um canal do ntfy.sh (lido pelo widget "Claude Code" do Launcher).
# Canal: variável CLAUDE_NTFY_TOPIC ou arquivo .claude/ntfy-topic (fora do git). Sem canal, não faz nada.
# Opcionais: CLAUDE_NTFY_TOKEN (token de uma conta ntfy, com cota própria; sem ele a cota é por IP,
# e o IP da nuvem é compartilhado) e CLAUDE_NTFY_SERVER (outro servidor ntfy, padrão https://ntfy.sh).
dir="$(cd "$(dirname "$0")/.." && pwd)"
topic="${CLAUDE_NTFY_TOPIC:-$(cat "$dir/ntfy-topic" 2>/dev/null)}"
topic="$(printf '%s' "$topic" | tr -d '[:space:]')"
[ -z "$topic" ] && exit 0

input="$(cat)"
field() { printf '%s' "$input" | jq -r "$1 // empty" 2>/dev/null; }
event="$(field .hook_event_name)"
repo="$(basename "$(git -C "$dir/.." rev-parse --show-toplevel 2>/dev/null || echo "$dir/..")")"

case "$event" in
  PostToolUse)
    cmd="$(field .tool_input.command)"
    # Só builds/testes do Gradle.
    printf '%s' "$cmd" | grep -qE '(^|[ /;&])gradlew? ' || exit 0
    out="$(printf '%s' "$input" | jq -r '[.tool_response.stdout, .tool_response.stderr, .tool_response.output] | map(select(. != null)) | join("\n")' 2>/dev/null)"
    task="$(printf '%s' "$cmd" | grep -oE 'gradlew? +[^|;&>]*' | head -1 | sed -E 's/gradlew? +//; s/ -[^ ]+//g; s/ +$//')"
    took="$(printf '%s' "$out" | grep -oE 'BUILD (SUCCESSFUL|FAILED) in [0-9a-z ]+' | tail -1 | sed -E 's/BUILD [A-Z]+ in //')"
    if printf '%s' "$out" | grep -q 'BUILD SUCCESSFUL'; then
      title="build ok"; tags="white_check_mark,build"; prio=3; msg="${task:-gradle} · ${took:-concluído}"
    elif printf '%s' "$out" | grep -q 'BUILD FAILED\|FAILURE:'; then
      title="build falhou"; tags="x,build"; prio=4; msg="${task:-gradle} · ${took:-erro}"
    else
      exit 0
    fi ;;
  Notification)
    title="esperando você"; tags="bell,wait"; prio=4
    msg="$(field .message)"
    case "$msg" in
      *"needs your permission to use "*) msg="Precisa de permissão para usar ${msg##*to use }" ;;
      *"needs your permission"*) msg="Precisa da sua permissão" ;;
      *"waiting for your input"*) msg="Esperando sua resposta" ;;
      *"needs your attention"*) msg="Precisa da sua atenção" ;;
    esac
    msg="${msg:-O Claude precisa da sua resposta}" ;;
  Stop)
    title="execução concluída"; tags="checkered_flag,stop"; prio=3
    msg="$(field .last_assistant_message | tr '\n' ' ' | cut -c1-140)"; msg="${msg:-Terminou e aguarda o próximo pedido}" ;;
  *) exit 0 ;;
esac

server="${CLAUDE_NTFY_SERVER:-https://ntfy.sh}"
auth=()
[ -n "$CLAUDE_NTFY_TOKEN" ] && auth=(-H "Authorization: Bearer $CLAUDE_NTFY_TOKEN")
code="$(curl -s -m 8 -o /dev/null -w '%{http_code}' "${auth[@]}" \
  -H "Title: $title" -H "Tags: $tags,$repo" -H "Priority: $prio" \
  -d "$msg" "${server%/}/$topic")"
if [ "$code" != "200" ]; then
  echo "$(date '+%F %T') ntfy respondeu $code ($event)" >> "$dir/ntfy-errors.log"
fi
exit 0
