# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/);
versionamento conforme [RELEASING.md](RELEASING.md).

## [Unreleased]

## [1.6.0] - 2026-10-31

### Adicionado
- **Buscar letras faltantes** (CFG → IMPORTAR): baixa a letra sincronizada das músicas que estão
  sem, **sem rebaixar o áudio**, com barra de progresso e cancelamento. Recupera letras que não
  vieram no download.
- **NetEase** como segunda fonte de letra, usada quando o LRCLIB não tem a música.
- **Painel de letras puxável** no Now Playing (retrato): uma alça puxa o CMD.LOG/LYRICS para cima,
  cobrindo o espectro e o volume; o volume vira uma barra vertical na direita (toque na alça alterna).

### Alterado
- **Now Playing em paisagem** reorganizado: espectro na coluna esquerda (abaixo do nome) e o console
  de letras/log ocupando mais espaço à direita.
- A **versão exibida** (tela de boot, topo do CFG e SOBRE) agora acompanha automaticamente a versão
  do app.
- Tela de boot: "NET SYNC [SKIP]" passou a "INTERNET CONNECTION [NOPE]".

### Corrigido
- No Now Playing, com o painel de letras **expandido** a letra ocupava só a metade de baixo; agora
  preenche a altura disponível, com a linha atual no rodapé.

## [1.5.0] - 2026-10-30

### Adicionado
- **Playlists** (nova aba na biblioteca, antes de ÁLBUNS): criar, editar e excluir; foto opcional
  filtrada no estilo do app; playlist **Favoritos** fixa (não renomeia nem exclui).
- **Toque longo numa faixa** (FAIXAS) → **adicionar a playlist**, **editar** (nome/foto/letra, só
  dentro do MPX) ou **excluir** o arquivo do aparelho (com confirmação dupla).
- **Botão de marcador** no Now Playing: toque favorita; toque longo escolhe a playlist.

### Corrigido
- Persistência de playlists/edições mais robusta (escrita atômica e tolerante a arquivo corrompido),
  orientação de fotos (EXIF) e letra editada que recarrega na hora.

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

[Unreleased]: https://github.com/henckemaier/mpx-player/compare/v1.6.0...HEAD
[1.6.0]: https://github.com/henckemaier/mpx-player/releases/tag/v1.6.0
[1.5.0]: https://github.com/henckemaier/mpx-player/releases/tag/v1.5.0
[1.04]: https://github.com/henckemaier/mpx-player/releases/tag/v1.04
