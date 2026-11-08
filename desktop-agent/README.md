# MPX Desktop Agent (`:desktop-agent`) — desktop-sync sub-1

Conector leve no PC (Windows) que escaneia uma pasta de músicas e serve a biblioteca para o
celular, via HTTP na LAN. Módulo Kotlin/JVM **isolado** (irmão do `:app`; o `:app` **não**
depende dele). Servidor **Ktor CIO**; stream com **Range/206** (plugin `PartialContent`);
tudo atrás de **token**; bind **LAN-only** (nunca `0.0.0.0`).

## Rodar (dev, sem empacotar)

```bash
# 1º arg = pasta de músicas (default: ~/Music). --port (default 8080). --host força o IP.
./gradlew :desktop-agent:run --args="C:\\Users\\voce\\Music"
./gradlew :desktop-agent:run --args="D:\\Musicas --port 9090"
```

No arranque o agente imprime o **TOKEN** (aleatório por execução) e a **URL da LAN**
(`http://<ip-site-local>:<porta>`). O IP é o 1º IPv4 **site-local** (`isSiteLocalAddress`,
não-loopback) detectado; com várias interfaces ele loga todas e usa a 1ª (override: `--host`).

## Contrato (DTO servido — fixo para a sub-2)

`RemoteTrackDto { id, title, artist, album, durationMs, trackNumber, year, mime, format,
bitrate, hasArt, relPathHint }`. `id` = **SHA-1 hex do caminho relativo** (estável entre
scans). `format` espelha `Track.format` do app (MP3/FLAC/AAC/OGG/OPUS/WAV). `relPathHint` =
caminho relativo com `/` (a sub-2 deriva a `key` = `Track.key` do app a partir dele).

## Endpoints (todos exigem token → 401 sem/errado)

| Método | Rota | Resposta |
|---|---|---|
| GET | `/ping` | `200 {name, version, trackCount}` |
| GET | `/library` | `200` array de `RemoteTrackDto` |
| GET | `/stream/{id}` | o áudio; `Accept-Ranges: bytes`, `206` com header `Range`; `404` se id desconhecido |
| GET | `/art/{id}` | a capa (embutida ou `folder/cover.(jpg|png)`); `404` se não houver |

Auth: `Authorization: Bearer <token>` **ou** `?token=<token>`.

## Client de teste (curl) — gate manual no PC

Suponha `TOKEN=abc...`, `URL=http://192.168.0.10:8080` e um `ID` tirado de `/library`:

```bash
# handshake / health
curl -s "$URL/ping" -H "Authorization: Bearer $TOKEN"
curl -s "$URL/ping?token=$TOKEN"                      # token na query também vale

# 401 sem token / token errado
curl -s -o /dev/null -w "%{http_code}\n" "$URL/ping"                 # -> 401
curl -s -o /dev/null -w "%{http_code}\n" "$URL/ping?token=errado"    # -> 401

# biblioteca inteira (JSON)
curl -s "$URL/library?token=$TOKEN"

# stream completo (200) e parcial (206 via Range — o que o ExoPlayer manda no seek)
curl -s -o full.mp3  "$URL/stream/$ID?token=$TOKEN"
curl -s -D - -o part.bin -r 0-9  "$URL/stream/$ID?token=$TOKEN"      # -> 206, Content-Range, 10 bytes
curl -s -I "$URL/stream/$ID?token=$TOKEN"                            # -> Accept-Ranges: bytes

# capa
curl -s -o art.jpg "$URL/art/$ID?token=$TOKEN"
```

## Empacotamento standalone (jpackage, app-image, JRE trimado via jlink)

Gera uma **pasta executável** no Windows com um JRE mínimo embutido — o usuário **não instala
Java**. Requer um JDK 17+ com `jpackage` no `PATH` (não é dependência do `build`/`test`).

```bash
./gradlew :desktop-agent:jpackageImage
# saída: desktop-agent/build/jpackage/MPXDesktopAgent/  (contém MPXDesktopAgent.exe + runtime/)
```

A task (ver `build.gradle.kts`) roda `installDist` (monta os jars em `build/install`) e então
`jpackage --type app-image` com `--jlink-options "--strip-debug --no-header-files
--no-man-pages --compress=2"` (JRE trimado). Instalador MSI fica para depois; o MVP só prova
que roda standalone. QR/mDNS (descoberta automática) = sub-3.
