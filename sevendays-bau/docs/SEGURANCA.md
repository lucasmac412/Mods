# Investigação do alerta do Defender

Em 27/10/2026, o usuário informou `Trojan:Script/Ulthar.A!ml` ao baixar a versão 0.2.0. O download foi bloqueado antes de instalar. **Não há confirmação da causa nem classificação oficial como falso positivo.**

## Retirada do pacote anterior

O ZIP 0.2.0 foi removido do estado atual do repositório e a tag `7days-baus-v0.2.0-experimental` foi excluída. O link enviado retornou HTTP 404 após a retirada. Isso não apaga cópias, caches ou o histórico de commits.

Arquivo relatado: `7Days-Crafting-dos-Baus-3.4-v0.2.0-Experimental.zip`.
SHA-256: `3f6253dea37e48624c74577d9f109d8121a99f503b5aa2b1eb2f6f4a9a757a06`.
DLL interna: `29b18825f3199c51ebd421ea3e949ab383fb4abe6eb3937e58835b4a27815b3a`.

## Conferência de integridade

O ZIP relatado contém somente seis arquivos: a DLL gerenciada do mod, manifesto, configuração JSON, XML da interface e dois textos. A DLL foi reconstruída a partir de `588d47f`, com SDK .NET 8.0.426 e a revisão informativa original `e10203b69041668bbb41a08a5acc99d7844200d0`: o resultado é idêntico byte a byte ao publicado. Uma reconstrução sem a revisão informativa diferia apenas nesse atributo de versão; após reproduzir o atributo, os hashes coincidiram.

A revisão do código/decompilação não identificou rotinas de execução de comandos, downloads externos, persistência no Windows ou alteração do Defender. O plugin usa Harmony dentro do jogo, leitura do seu `config.json` e a rede nativa da partida. Isso não constitui uma certificação antivírus.

A DLL candidata 0.3.0 também foi reconstruída de uma cópia limpa dos fontes de `8a40b4a`, reproduzindo a revisão informativa `7eefae71b3264a9f699163aaa367b2a75c20dc48`, e resultou idêntica ao binário usado nos testes nativos.

## Análises descartadas

A [primeira execução](https://github.com/lucasmac412/Mods/actions/runs/40514865376) retornou código zero. Ela **não vale como evidência de ausência de ameaças**: uma [verificação posterior](https://github.com/lucasmac412/Mods/actions/runs/40516365314) constatou que a imagem Windows do GitHub excluía `C:\` e `D:\`, não verificava arquivos compactados e estava com MAPS desativado. O gate de exclusões falhou corretamente.

O fluxo foi corrigido para remover essas exclusões apenas na VM descartável de análise, habilitar varredura de arquivos compactados e MAPS, atualizar assinaturas, verificar a conexão de nuvem e exigir que os alvos não estejam excluídos. A DLL nunca é executada no job de antivírus. As análises incluem ZIP e conteúdo extraído, marca de origem da Internet e conferência de registros de detecção mesmo quando o scanner retorna zero.

## Resultado válido e versão 0.3.0

A [análise final no Windows Server 2022](https://github.com/lucasmac412/Mods/actions/runs/40518155922), com proteção em tempo real, IOAV e monitoramento de comportamento ativos, terminou com sucesso. Ela confirmou o resultado da [varredura personalizada anterior](https://github.com/lucasmac412/Mods/actions/runs/40516898782). O Microsoft Defender informou **“found no threats”** nas quatro varreduras: ZIP relatado, conteúdo extraído dele, ZIP 0.3.0 e conteúdo extraído da nova versão. Não houve registros em `Get-MpThreatDetection` nem em `Get-MpThreat`.

- Plataforma Defender: `4.18.26090.9`; mecanismo: `1.1.26090.9`.
- Assinaturas: `1.461.226.0`, atualizadas em 27/10/2026.
- Nenhuma exclusão de caminho, processo ou extensão. Proteção em tempo real, IOAV e monitoramento de comportamento confirmados como ativos pelo mecanismo.
- Varredura de arquivos compactados habilitada; MAPS avançado habilitado e conexão validada.
- ZIPs marcados com origem da Internet. A DLL foi apenas analisada pelo antivírus, não executada nesse job.
- O teste é uma varredura personalizada na VM; não reproduz integralmente o Chrome, SmartScreen, políticas locais ou o ambiente do usuário.

**Isso não comprova que o alerta original era falso positivo e não garante ausência de novas detecções.** Não foi identificado malware específico para “remover”. A nova versão contém as alterações funcionais solicitadas e o mesmo ZIP analisado é o publicado. O pacote anterior permanece retirado.

ZIP 0.3.0 SHA-256: `2882a59fa0b444a0bea94ecf32fc24a4cd21292f35c7ebe2b5c27c4e7b929184`.
DLL 0.3.0 SHA-256: `fbb536e00f6dabdd52b4e69ddcaf79b534c1688fbe2eb741b109541e0929d614`.

[Evidência do scanner](seguranca-v030/scanner.txt) · [Conexão de nuvem](seguranca-v030/cloud.txt) · [Preferências](seguranca-v030/preferences.json) · [Resultados e hashes](seguranca-v030/results.json) · [Integridade/reconstrução](seguranca-v030/integrity.json)

Se o Defender bloquear a nova versão, **não desative a proteção, não crie exceções e não restaure o arquivo da quarentena**. O próximo diagnóstico precisa do nome/caminho afetado e da versão das assinaturas no Histórico de proteção. Testes funcionais do mod continuam separados da análise antivírus.
