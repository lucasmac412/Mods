# Verificações da Luma

Este arquivo separa testes automatizados de comportamento observado no Minecraft. Um teste de texto ou HTTP não comprova que o NPC renderiza, navega ou constrói corretamente dentro do jogo.

## Estado

Validação em 20/10/2026, com **Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Temurin 25.0.4.1, Gradle 9.7.1 e Loom 1.18.4**:

| Verificação | Resultado |
| --- | --- |
| Setup e build | `bash scripts/setup-cloud.sh` atualizado e executado com sucesso; builds do mod e do laboratório concluídos com `BUILD SUCCESSFUL` |
| Código comum e cliente | Compilados; JAR produzido em `build/libs/luma-companion-0.2.0-experimental.jar` |
| JUnit do mod: português, IA própria, adaptador legado e HTTP de voz | **60 executados, 60 aprovados, zero falhas ou pulos** |
| JUnit do laboratório `native-ai` | **48 executados, 48 aprovados, zero falhas ou pulos** |
| GameTests no servidor real | **Sete cenários Luma aprovados**, mais um controle interno `minecraft:always_pass`; oito no relatório total |
| Artefatos | Skin, renderer, painel de voz e checkpoint original incluídos; mod de testes excluído do JAR instalável |
| Qualidade da IA própria | Três treinos comparados por validação; 24 respostas de teste e dez adicionais registradas. **Ainda não produz conversa útil**; veja [os resultados](../native-ai/experiments/RESULTADOS.md) |

Os sete GameTests verificaram registro e atributos; entrega e devolução sem duplicação e recusa de intruso; parada e navegação real; extração de 16 entre 17 troncos com loot e desgaste; abrigo de 96 blocos preservando terreno ocupado; serialização de dono, vida, modo, equipamento e materiais; combate com espada contra monstro e recusa de atacar o dono; e o fluxo de comandos criar, entregar, remover e recriar. Registro/atributos/inventário compõem um único cenário.

Não foram executados um cliente gráfico, captura real do microfone, reprodução audível nem login Microsoft. O modelo original foi treinado e executado em CPU; a integração foi exercitada com o checkpoint real incluído, além de geradores controlados para os testes de concorrência. O adaptador HTTP antigo permanece apenas para referência/testes; `/luma ia ligar` usa `NativeBrain`, sem endpoint externo. Não instalamos Ollama. O runner registrou uma consulta de chaves públicas Mojang recusada pela rede; isso não impediu os testes, que usam jogadores simulados. A instalação para jogar usa a conta e a rede do computador do jogador.

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

O comando deve terminar com `BUILD SUCCESSFUL`. O relatório de testes será gerado em `build/reports/tests/test/index.html`. O JAR instalável será `build/libs/luma-companion-0.2.0-experimental.jar`.

`build` também inicia e encerra o servidor GameTest automaticamente. Para executar apenas esses cenários, use `./gradlew runGameTest`; para JUnit, use `./gradlew test`.

Os testes de `IntentParserTest` cobrem ordens em português, interrupções, negações e ambiguidades. `NativeBrainTest` cobre carga/geração com o modelo incluído, parser, saída somente de conversa, limpeza/limites, cancelamento, timeout, fila, falhas e fechamento. `RemoteBrainTest` preserva testes do adaptador legado com HTTP controlado. `VoiceBridgeTest` verifica autenticação, origem/host, limites, cursor e revogação do painel.

Os 48 testes do motor original são executados separadamente com `./gradlew -p native-ai build`, e têm relatórios em `native-ai/build/test-results/test`. Eles verificam gradientes, causalidade, aprendizagem de sequência pequena, tokenização, arquivos, máscaras, clipping, geração e integridade do pipeline. Não demonstram qualidade comparável a modelos grandes. O modelo não aprende durante o jogo e cada pergunta é independente.

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
| IA própria | Use `/luma ia ligar` e envie conversa; confira aviso experimental, tentativa de geração e ausência de travamento do jogo. Registre texto incorreto como falha de qualidade, sem esperar português compreensível deste checkpoint. |
| Cancelamento da IA | Envie conversa e use `/luma parar`, desligue a IA ou saia do mundo; confira ausência de respostas atrasadas após mudar a sessão. |
| Remoção | Recupere os itens e use `/luma remover`; confirme que ela deixa o mundo e pode ser criada novamente conforme o limite do mod. |

Problemas de navegação, API do Minecraft, renderização, balanceamento e disponibilidade das vozes só podem ser confirmados em execução real. Nenhum item desta lista deve ser marcado como aprovado apenas porque o código compilou.
