# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/);
versionamento conforme [RELEASING.md](RELEASING.md).

## [Unreleased]

## [1.7.1] - 2026-11-28

### Removido
- **Sincronização com o PC (Desktop) da 1.7.0 foi removida.** O recurso não será usado. Saíram:
  a tela **CFG → [7] DESKTOP**, o pareamento (IP/porta + token, QR e link `mpx://pair`), a
  biblioteca remota e o streaming do PC, a sincronização offline das favoritas e o botão
  **REMOVER OFFLINE**, a descoberta automática na rede local e o agente do PC (`:desktop-agent`).
- O app volta a **bloquear tráfego HTTP em claro**, como na 1.6.1: todos os serviços usados
  (letras, GitHub, YouTube) são só HTTPS.

### Alterado
- **Limpeza automática do pareamento**: ao abrir a 1.7.1 pela primeira vez, o app apaga o endereço
  e o token do PC que a 1.7.0 tinha salvo, e o índice das faixas sincronizadas. Nada é feito em
  instalações que nunca usaram o recurso.
- As músicas que a 1.7.0 copiou para `Music/MPX` **não são apagadas**: são suas e continuam na
  biblioteca como faixas locais comuns.
- Faixas do PC que estavam em playlists ou favoritas deixam de aparecer (como um arquivo apagado);
  a cópia offline em `Music/MPX`, se houver, continua tocando normalmente.
- Mensagens de erro do player ficaram genéricas (sem referência ao PC). Os erros continuam
  aparecendo na tela e no log local.

## [1.7.0] - 2026-11-27

### Adicionado
- **Sincronização com o PC** (novo): o app pode ler a biblioteca de música de um computador da rede
  local e tocar as faixas dele. Para isso existe o agente desktop (`:desktop-agent`), que roda no PC
  e serve a biblioteca pela rede local.
- **Conectar ao PC** em **CFG → [7] DESKTOP**: por IP e porta com token, pelo QR de pareamento ou
  por link (`mpx://pair`). Um link pede confirmação antes de conectar.
- **Biblioteca remota**: navegar e tocar as músicas do PC, com streaming.
- **Sincronização offline das favoritas**: copia as favoritas do PC para `Music/MPX`, com nome
  legível. A cópia pode ser cancelada, e o botão **REMOVER OFFLINE** apaga as cópias.

### Segurança
- Conexão com o PC só para **IPv4 privado ou loopback**, digitado ou vindo do QR. Nomes de domínio
  não são aceitos.
- Redirecionamentos HTTP desligados nas conexões com o agente.
- O token do PC fica **fora do backup** do app e é mascarado nos logs.
- O agente compara o token em tempo constante e limita tentativas (**429** em excesso).
- **Tráfego HTTP em claro** é permitido na configuração de rede do app, porque o agente é alcançado
  pelo IP da LAN e um certificado não cobre IPs arbitrários. Os serviços públicos usados pelo app
  (letras, GitHub, YouTube) continuam **só HTTPS**. Esta é uma decisão documentada; HTTPS com
  certificado próprio fica para uma versão futura.

### Corrigido
- Mensagens de erro de conexão com o PC agora dizem o que aconteceu (agente offline, token errado).
- Erros do player aparecem na tela, em vez de passarem em silêncio.

### Alterado
- Processo de desenvolvimento (sem mudança no app): a `main` passa a ser protegida, com mudanças por
  Pull Request e checks obrigatórios (build, testes JVM, lint e guarda anti-regressão do R8).
- Um push na `main` sem subir a versão não publica mais nada: a release existente e seu APK ficam
  intactos. Cada release agora inclui o `mapping.txt` do R8, para desofuscar crashes.
- Testes unitários JVM e lint com baseline passam a ser versionados no repositório.

## [1.6.1] - 2026-11-27

### Corrigido
- **Crash ao baixar** músicas ou playlists (release): o app fechava ao iniciar o yt-dlp, na primeira
  vez que o motor era preparado. Causa: a minificação do build de release removia classes que o
  extrator de arquivos usa internamente. Corrigido.
- Falhas de download agora aparecem como **erro** na tela, em vez de fechar o app.
- Após um erro, o app guarda um **log local** de falhas (nada é enviado para fora do aparelho).

### Adicionado
- Em **CFG → SOBRE**: **LOG ERROS** (ver o log), **COMPARTILHAR** (enviar o log por outro app) e
  **LIMPAR** (apagar o log).

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
