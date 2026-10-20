# Verificações da Luma

Este arquivo separa testes automatizados de comportamento observado no Minecraft. Um teste de texto ou HTTP não comprova que o NPC renderiza, navega ou constrói corretamente dentro do jogo.

## Estado

Validação em 20/10/2026, com **Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Temurin 25.0.4.1, Gradle 9.7.1 e Loom 1.18.4**:

| Verificação | Resultado |
| --- | --- |
| Setup e build | `bash scripts/setup-cloud.sh` verifica Java/Gradle e compila mod, laboratório e complemento de voz; os três builds foram concluídos com `BUILD SUCCESSFUL` |
| Código comum e cliente | Compilados; JAR produzido em `build/libs/luma-companion-0.4.0-experimental.jar` |
| JUnit do mod: português, IA própria, Ollama, adaptador legado e HTTP de voz | **77 executados, 77 aprovados, zero falhas ou pulos** |
| JUnit do laboratório `native-ai` | **66 executados, 66 aprovados, zero falhas ou pulos** |
| JUnit do complemento de voz `voice-input` | **14 executados, 14 aprovados, zero falhas ou pulos** |
| GameTests no servidor real | **Sete cenários Luma aprovados**, mais um controle interno `minecraft:always_pass`; oito no relatório total |
| Artefatos | Skin, renderer, painel de voz, checkpoint original e índice de memória incluídos; mod de testes excluído do JAR instalável. O complemento de voz é um JAR cliente separado |
| Qualidade da IA própria | A opção `/luma ia propria` mantém o modelo da versão 0.3: 11/40 respostas úteis em teste sintético avaliado por Codex; veja [resultados e falhas](../native-ai/experiments-v2/RESULTADOS.md). Essa taxa não avalia Ollama |
| Ollama | Contrato HTTP `/api/chat` verificado com servidor local controlado; modelo Ollama real e qualidade das respostas no PC do jogador não foram testados |
| Reconhecimento português Vosk | Modelo oficial carregou, reconhecedor foi criado e processou 1 segundo PCM16LE silencioso no Linux x64/JDK 25, retornando texto vazio. Não comprova precisão com fala humana |

Os sete GameTests verificaram registro e atributos; entrega e devolução sem duplicação e recusa de intruso; parada e navegação real; extração de 16 entre 17 troncos com loot e desgaste; abrigo de 96 blocos preservando terreno ocupado; serialização de dono, vida, modo, equipamento e materiais; combate com espada contra monstro e recusa de atacar o dono; e o fluxo de comandos criar, entregar, remover e recriar. Registro/atributos/inventário compõem um único cenário.

Não foram executados um cliente gráfico, captura real do microfone, reprodução audível nem login Microsoft. A voz padrão depende do sintetizador do Minecraft/sistema; não há confirmação de voz pt-BR em todos os sistemas. O modelo original foi treinado e executado em CPU; o refinamento usa apenas os nossos próprios pesos. Essa integração foi exercitada com o checkpoint e índice reais, além de geradores controlados para concorrência. `/luma ia ligar` agora escolhe **Ollama local**; `/luma ia propria` mantém `NativeBrain`. O antigo `RemoteBrain` continua apenas para referência/testes e não é o adaptador novo. Ollama e um LLM real não foram instalados neste ambiente de testes; a integração HTTP usa respostas controladas, sem medir qualidade de conversa. O runner pode registrar consulta de chaves públicas Mojang recusada pela rede; isso não impede os testes com jogadores simulados.

Relatórios atuais: `build/test-results/test/TEST-*.xml` e `build/test-results/gametest.xml`. O teste de controle do runner é contado separadamente dos sete cenários que verificam a Luma.

## Compilação e testes automatizados

Com JDK 25 e internet, execute na pasta do projeto:

```sh
./gradlew build
```

No Windows PowerShell:

```powershell
.\gradlew.bat build
```

O comando deve terminar com `BUILD SUCCESSFUL`. O relatório de testes será gerado em `build/reports/tests/test/index.html`. O JAR instalável será `build/libs/luma-companion-0.4.0-experimental.jar`. O complemento cliente é compilado separadamente com `./gradlew -p voice-input build`; seu JAR fica em `voice-input/build/libs/luma-voice-0.4.0-experimental.jar`.

`build` também inicia e encerra o servidor GameTest automaticamente. Para executar apenas esses cenários, use `./gradlew runGameTest`; para JUnit, use `./gradlew test`.

Os testes de `IntentParserTest` cobrem ordens em português, interrupções, negações e ambiguidades. `NativeBrainTest` cobre carga/geração com modelo e memória incluídos, uso da nota como entrada sem substituir a saída do gerador, encaminhamento de conversas com “não/ou/talvez”, preservação de ordens reais, saída somente de conversa, limpeza/limites, cancelamento, timeout, fila, falhas e fechamento. `RemoteBrainTest` preserva testes do adaptador legado com HTTP controlado. `VoiceBridgeTest` verifica autenticação, origem/host, limites, cursor e revogação do painel.

Os 15 testes novos de `OllamaBrainTest` verificam JSON e contexto atual enviados à API nativa, histórico de quatro turnos limitado por sessão, conversas com negações/alternativas, ordens imediatas sem HTTP, modelo ausente/404, respostas inválidas e grandes, timeout, cancelamento de pedido anterior, fechamento, limite global de quatro trocas e texto sempre `CHAT`. Nenhuma saída do LLM é executada como comando. O cenário de comandos no GameTest também seleciona modelo com `:`, troca Ollama/própria, consulta status, desliga e para o NPC sem precisar de um servidor Ollama.

O complemento de voz testa duração/tamanho de captura, silêncio, exclusão de gravações sobrepostas, fechamento de recursos, cancelamento durante preparação/transcrição e texto enviado como argumento literal de `luma falar`. A compilação cobre as APIs cliente reais de Minecraft 26.2. O reconhecimento usa Vosk e modelo português de terceiros, com licenças e verificação em `third-party/voice`; não confundir com o modelo de texto autoral. Não há alegação de gravação real, precisão de transcrição ou reprodução audível aprovada.

Os 66 testes do motor original são executados separadamente com `./gradlew -p native-ai build`, e têm relatórios em `native-ai/build/test-results/test`. Eles verificam gradientes, causalidade, aprendizagem de sequência pequena, tokenização, arquivos, máscaras, clipping, geração, agendas de aprendizado, snapshots, reaproveitamento de nossos checkpoints com AdamW novo, recuperação lexical e ausência de referências reservadas no índice/notas. Não demonstram qualidade comparável a modelos grandes. O modelo não aprende durante o jogo e cada pergunta é independente.

## Roteiro manual no Minecraft

Use um mundo **novo**, em Criativo e com comandos habilitados. Instale apenas Luma e Fabric API na primeira execução, com Minecraft Java Edition 26.2 e Fabric Loader compatível. Tenha uma cópia de segurança antes de testar em outro mundo.

Registre cada linha como **passou**, **falhou** ou **não testado**, com uma observação curta. As linhas abaixo são um roteiro de validação, não resultados já obtidos.

| Verificação | Procedimento e resultado esperado |
| --- | --- |
| Carregamento | Abra o perfil Fabric 26.2; o jogo deve chegar ao menu sem erro de dependência. |
| Aparência | Use `/luma criar`; confira corpo humanoide, skin própria roxa/ciano e ausência de textura faltando. |
| Dono | Confirme que as ordens afetam a companheira de quem as envia. Em multiplayer, teste com dois jogadores e suas próprias companheiras. |
| Seguir e esperar | Alterne `/luma seguir` e `/luma ficar`; caminhe em uma área plana e observe a mudança de comportamento. |
| Recuperação | Use `/luma localizar`; confira dimensão/coordenadas conhecidas. Na mesma dimensão e com a região carregada, use `/luma chamar`; confira a chegada e ausência de duplicação. |
| Conversa local | Use `/luma falar Oi` e `Luma, me siga`; confira resposta em português e execução da ordem clara. |
| Ordens negativas | Envie `Luma, não construa`; a frase não deve iniciar a construção. Use `/luma parar` para cancelar uma tarefa ativa. |
| Inventário | A até seis blocos, entregue itens com `/luma entregar`; use `/luma devolver` e confira as quantidades e a ausência de duplicação. Fora do alcance, a troca deve ser recusada. Confira os 27 espaços e a troca da ferramenta da mão. |
| Ferramentas | Entregue machado para troncos ou picareta adequada para pedra, em local acessível; confira limite de 16 blocos por ordem, itens obtidos e desgaste. |
| Limites de mineração | Tente sem ferramenta, em minérios, abaixo dos pés do dono e ao lado de líquidos; confira a recusa ou espera, sem extração indevida. |
| Construção | Entregue 96 blocos aceitos e use `/luma construir` em área plana 5 × 5, com volume de cinco blocos de altura completamente vazio; confira piso, paredes de três blocos, teto, entrada aberta 1 × 2 e consumo dos 96 materiais. |
| Construção sem recursos | Peça construção sem blocos suficientes; confira que não surgem blocos grátis. |
| Obstáculos | Peça construção em local ocupado; confira que construções existentes não são substituídas indevidamente. |
| Combate | Entregue uma arma e use `/luma proteger` perto de um inimigo, em área de teste controlada; observe seleção de alvo e dano. |
| Autonomia | Use `/luma autonomo` com machado e troncos acessíveis; confira limite de oito troncos por ativação, acompanhamento e combate. Interrompa com `/luma parar`. |
| Regra do mundo | Desative `minecraft:mob_griefing`; confira bloqueio da mineração e construção sem bloquear acompanhamento e combate. Reative a regra ao terminar o teste. |
| Persistência | Entregue itens, salve, saia e reabra o mundo; confira dono, companheira e inventário. |
| Dimensões e distância | Mude de dimensão; confira pausa das tarefas e recusa ao chamar de outra dimensão. Use `/luma localizar`, retorne e carregue a região antes de chamar. |
| Voz por texto | No mundo de um jogador, use `/luma voz`, abra o link e ative o painel; uma mensagem digitada deve chegar ao jogo. |
| Voz de saída | Escolha uma voz pt-BR e envie uma mensagem; confira a leitura sem repetições excessivas. Registre navegador e voz usados. |
| Microfone | Em Chrome/Edge, autorize o microfone pelo botão; fale uma ordem simples e confira a transcrição e a ação. |
| Microfone negado | Negue a permissão; o painel deve permitir continuar por texto e mostrar o problema. |
| Encerrar voz | Use a pausa e `/luma voz parar`; confira interrupção da fala e do acesso ao jogo. |
| Painel privado | Tente usar o painel sem o código válido; ele não deve aceitar comandos. Não publique o código ao registrar o teste. |
| Servidor dedicado | Confira que o mod carrega sem classes gráficas no servidor. O painel local de voz deve permanecer indisponível; texto continua sendo o canal previsto. |
| IA desligada | Com IA desativada, envie ordens e conversa local; não deve ser necessário ter aplicativo de IA ou chave. |
| Ollama | Instale e abra Ollama, baixe `qwen2.5:3b`, use `/luma ia ollama` e converse. Confira respostas, contexto e quatro turnos de histórico. Feche Ollama e confira aviso de falha sem travamento ou execução de ações geradas. |
| IA própria | Use `/luma ia propria` e envie conversa; confira aviso experimental, tentativa de geração e ausência de travamento do jogo. Registre compreensibilidade, pertinência e erros, sem presumir qualidade. |
| Microfone com tecla | Instale Luma Voz e o modelo na pasta `config`; segure V, fale “me siga” e solte. Confira transcrição e ação; teste silêncio, tecla trocada, outro microfone padrão e recusa de permissão. |
| Cancelamento do microfone | Grave e abra menu/chat, perca foco, desconecte ou troque de mundo. Confira fechamento da captura e ausência de ordens/respostas antigas na nova sessão. |
| Voz dentro do jogo | Com Luma Voz, confira leitura apenas das mensagens da Luma e interrupção por nova fala/V. K alterna leitura; registre sistema, voz e idioma efetivamente usados. Não confundir com o painel no navegador. |
| Cancelamento da IA | Envie conversa e use `/luma parar`, desligue a IA ou saia do mundo; confira ausência de respostas atrasadas após mudar a sessão. |
| Remoção | Recupere os itens e use `/luma remover`; confirme que ela deixa o mundo e pode ser criada novamente conforme o limite do mod. |

Problemas de navegação, API do Minecraft, renderização, balanceamento e disponibilidade das vozes só podem ser confirmados em execução real. Nenhum item desta lista deve ser marcado como aprovado apenas porque o código compilou.
