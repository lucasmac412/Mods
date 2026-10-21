# Instalar Luma com Ollama e voz

Também disponível: **[Dragão Celestial 0.2.1 experimental](https://raw.githubusercontent.com/lucasmac412/Mods/dragao-v0.2.1-experimental/downloads/Dragao-Celestial-Fabric-26.2-v0.2.1-Experimental.zip)**, um mod independente, com dois JARs e instruções. Siga o [passo a passo do dragão](../docs/INSTALAR-DRAGAO.md). Não precisa de Luma ou Ollama. Para reconstruir esse pacote após compilar `dragon-mod`, execute `python3 scripts/package-dragon.py`.

A 0.2.1 refaz o visual do dragão: juba volumosa, focinho alongado, corpo mais fino com volumes de musculatura e sem patas. Mantém a ondulação lateral, os golpes e os dois ovos de criação na aba Dragão Celestial. Montado, use o raio com as duas mãos vazias; com item em qualquer mão, o clique direito usa o item normalmente, inclusive ovos do Minecraft.

Nesta versão de testes, o Bosque Celestial continua muito comum: um centro de bosque e ninho por região de 256 × 256 blocos, 36 vezes mais centros por área do que na 0.1.0. Os bosques mantêm raio de 192 blocos e podem se unir. A frequência aumentada é temporária e aparece somente em regiões novas; prefira um mundo novo. Ao atualizar, substitua o JAR antigo por `celestial-dragon-0.2.1-experimental.jar`, sem deixar duas versões em `mods`; o Fabric API não mudou.

**[Baixar o pacote pronto 0.4.0 experimental](https://raw.githubusercontent.com/lucasmac412/Mods/v0.4.0-experimental/downloads/Luma-Fabric-26.2-Ollama-e-Voz-v0.4.0-Experimental.zip)**.

O ZIP inclui os três JARs de Luma, Luma Voz e Fabric API, o modelo offline de reconhecimento de português e o passo a passo. Você não precisa programar ou compilar. O Ollama e seu modelo de conversa são instalados separadamente; o [guia](../docs/OLLAMA-E-VOZ.md) mostra como fazer isso sem assinatura ou chave de API.

1. No Chrome, clique no link acima. Aperte **Ctrl + J** e use **Mostrar na pasta**.
2. Extraia o ZIP e abra **COMECE-AQUI.txt**.
3. Instale Fabric para Minecraft Java Edition 26.2. Copie os **três JARs** para `mods` e a pasta `config/luma-voice` para `config` no diretório de jogo do perfil.
4. Para atualizar, mova JARs antigos dos mesmos mods para fora de `mods`. Mescle a pasta de configuração, preservando as configurações de outros mods.
5. Instale e abra Ollama, execute `ollama pull qwen2.5:3b` e, no mundo, use `/luma criar` e `/luma ia ollama`. Segure V para falar e solte para enviar.

O erro “não foi possível baixar o artefato” refere-se à entrega de arquivos da conversa. Este link baixa pelo GitHub. Na lista de arquivos do repositório, abra o ZIP e clique em **Download raw file**. **Code → Download ZIP** baixa o código-fonte e não substitui este pacote pronto.

`SHA256SUMS.txt` registra os checksums. Leia o [guia completo](../docs/INSTALAR.md), o [guia de Ollama e voz](../docs/OLLAMA-E-VOZ.md) e os [testes](../docs/TESTES.md).

Os pacotes 0.1, 0.2 e 0.3 são preservados para referência. O nome `Luma-Fabric-26.2-IA-Propria-v0.3.0-Experimental.zip` identifica a versão anterior, sem esta integração com Ollama e sem o complemento de tecla de microfone.

Luma Voz usa Vosk e um modelo de português de terceiros, com licenças e origem documentadas no pacote. A fala da Luma depende da voz do sistema. O reconhecimento do complemento ocorre localmente; o painel opcional no Chrome pode depender de serviços online do navegador.

Para reproduzir o pacote após os builds aprovados, use o script de empacotamento documentado no repositório. Não instale arquivos `-sources.jar` nem copie o ZIP inteiro para `mods`.
