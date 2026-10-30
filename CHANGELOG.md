# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/);
versionamento conforme [RELEASING.md](RELEASING.md).

## [Unreleased]

## [1.04] - 2026-10-30

### Adicionado
- **Modo paisagem** dedicado em todas as telas (Boot, Now Playing, Library, Queue, EQ, Settings),
  com **barra de navegação lateral** à esquerda.
- **Atualização in-app** (SOBRE → CHECK UPDATE): checa a Release mais recente no GitHub, baixa o APK
  e chama o instalador.
- **Assinatura de release** fixa + **GitHub Actions** que compila e publica o APK assinado a cada push
  na `main`.

### Alterado
- **Desduplicação de download por metadados** (título + artista + duração), no lugar do nome do arquivo:
  não duplica a mesma música vinda de outra playlist/posição e não pula por engano homônimas de
  artistas diferentes.

### Corrigido
- Download de playlist **ignora vídeos privados/indisponíveis** e publica o restante, em vez de marcar
  tudo como erro.

## [1.03]

Versão base — primeira no GitHub.

### Adicionado
- **Downloader de playlists do YouTube Music** → MP3 (yt-dlp via youtubedl-android), com **capa**
  embutida (filtrada no estilo retrô do app) e **letra sincronizada** (LRCLIB).
- **Cor personalizada** (CORES → PERSONALIZADA) com sliders R/G/B.
- Dedup de download por nome de arquivo (`--download-archive` + checagem no MediaStore).
- Player base: biblioteca via MediaStore, fila, **equalizador 10 bandas**, temas/fósforos, estética
  CRT (scanlines, glow, ASCII/dither nas capas), letras no Now Playing com quebra de linha.

[Unreleased]: https://github.com/henckemaier/mpx-player/compare/v1.04...HEAD
[1.04]: https://github.com/henckemaier/mpx-player/releases/tag/v1.04
