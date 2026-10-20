# Instalar a Luma

**[Baixar Luma 0.3.0 experimental com IA própria](https://raw.githubusercontent.com/lucasmac412/Mods/v0.3.0-experimental/downloads/Luma-Fabric-26.2-IA-Propria-v0.3.0-Experimental.zip)**.

O ZIP inclui o mod compilado, o Fabric API compatível e o passo a passo. O modelo próprio e a memória local já estão dentro do JAR. Não é necessário programar, compilar, instalar Ollama ou configurar uma API. A geração melhorou neste teste pequeno, mas ainda erra bastante: 11/40 respostas úteis, pela avaliação Codex, contra zero na versão anterior. É um experimento opcional, desligado por padrão, com [resultados e falhas registrados](../native-ai/experiments-v2/RESULTADOS.md).

1. Clique no link acima. No Chrome, aperte **Ctrl + J** e use **Mostrar na pasta**.
2. Extraia o ZIP e abra **COMECE-AQUI.txt**.
3. Siga o guia para instalar Fabric em Minecraft Java Edition 26.2 e copiar apenas os dois JARs para a pasta `mods`.
4. Se já tem a Luma antiga, mova o JAR antigo para fora de `mods`; mantenha só uma versão.

O erro “não foi possível baixar o artefato” refere-se à entrega de arquivos da conversa. Este link baixa o arquivo pelo GitHub. Se estiver na lista de arquivos do repositório, abra o ZIP e clique em **Download raw file**. **Code → Download ZIP** baixa o código-fonte e não substitui este pacote de instalação.

`SHA256SUMS.txt` registra os checksums dos pacotes. O [guia completo](../docs/INSTALAR.md) e os [testes](../docs/TESTES.md) estão no projeto e no pacote. Voz/microfone continuam usando navegador/sistema, possivelmente com internet.

`Luma-Fabric-26.2.zip` é o arquivo preservado da versão 0.1.0; ele não inclui o modelo próprio. `Luma-Fabric-26.2-IA-Propria-Experimental.zip` preserva a versão 0.2.0; use o link da versão 0.3.0 acima para a atualização.

Para reproduzir o pacote experimental após um build aprovado: `python3 scripts/package-installation.py`. O script verifica o mod, o checkpoint, o Fabric API e o CRC do ZIP antes de finalizar.
