# Luma — Companhia de aventura

Luma é uma companheira humanoide para **Minecraft Java Edition 26.2 com Fabric**. Ela tem uma skin própria em roxo e ciano, acompanha o dono, recebe ordens em português do Brasil e pode responder em voz alta por um painel no navegador.

**[Baixar Luma 0.2.0 experimental com IA própria](https://raw.githubusercontent.com/lucasmac412/Mods/v0.2.0-experimental/downloads/Luma-Fabric-26.2-IA-Propria-Experimental.zip)** — inclui Luma, Fabric API, o modelo próprio e o guia `COMECE-AQUI.txt`. A IA ainda pode produzir texto sem sentido; confira os limites abaixo.

**Comece pelo [passo a passo de instalação](docs/INSTALAR.md).** Ele explica onde colocar os arquivos mesmo para quem nunca instalou um mod. Se você recebeu um pacote de instalação com os JARs, use esses arquivos. O ZIP do código-fonte é diferente: ele precisa ser compilado para virar um mod.

## O que a Luma faz

- Segue, espera, protege o dono e pode ser chamada de volta.
- Recebe itens da sua mão a até seis blocos de distância, usa uma ferramenta ou arma de cada vez e tem uma mochila de 27 espaços.
- Combate corpo a corpo com espadas e ferramentas; não usa arcos, escudos ou armaduras.
- Minera até 16 blocos por ordem: troncos com machado e pedras simples com picareta adequada. Não extrai minérios nem escava túneis.
- Constrói um abrigo fixo de 5 × 5 com 96 blocos entregues por você, deixando uma entrada aberta.
- No modo autônomo, acompanha, enfrenta inimigos próximos e pode coletar até oito troncos por ativação, se tiver um machado.
- Entende ordens simples pelo chat, inclusive `Luma, me siga`.
- Oferece entrada pelo microfone e leitura de respostas em português por um painel local, em mundos de um jogador.
- Inclui um modelo generativo próprio, treinado do zero, com ativação opcional. O texto é gerado por pesos aprendidos, mas ainda tem qualidade muito limitada. As ordens básicas funcionam com o modelo desligado.

Ela é um NPC com comportamentos programados. Não joga como uma pessoa em todas as situações, não termina o Minecraft sozinha, não fabrica qualquer receita e não transforma pedidos livres em construções arbitrárias. A autonomia só funciona enquanto o mundo e a região da companheira estão sendo processados pelo jogo, com o dono conectado na mesma dimensão. As tarefas com blocos respeitam a regra `minecraft:mob_griefing`; não há integração genérica com proteção de terrenos de outros mods.

A voz usa síntese de fala do sistema ou navegador. A qualidade depende das vozes instaladas; não há uma atriz dublando as respostas nem garantia de voz humana. O reconhecimento do microfone geralmente precisa de Chrome ou Edge e pode depender de internet e dos serviços do fornecedor do navegador.

## IA própria experimental

Esta versão inclui código original de tokenização BPE, Transformer causal, treinamento com AdamW e geração de texto em Java. Os pesos começam aleatórios e foram treinados com um microcorpus sintético escrito por Codex para este projeto. Não usamos pesos pré-treinados, Ollama, API paga ou bibliotecas de aprendizado de máquina. Java, Minecraft e Fabric continuam sendo dependências do mod.

Para experimentar, use `/luma ia ligar` e `/luma falar Como podemos começar nossa aventura?`. Para desligar, use `/luma ia desligar`. A geração acontece no computador que executa o mundo/servidor e não envia a conversa a um serviço de IA. Esta escolha vale só para a sessão atual; o modelo começa desligado ao entrar novamente.

**O resultado ainda não tem qualidade comparável aos grandes modelos usados com Ollama.** Nas 34 perguntas registradas, produziu principalmente fragmentos de palavras sem sentido. O modelo só gera falas: os comandos e as capacidades reais do NPC continuam controlando as ações. Não há memória de conversa nem aprendizagem automática enquanto você joga.

O motor atual usa CPU; não utiliza a RTX 4060. Nenhum recurso pago foi contratado. Reconhecimento de microfone e síntese de voz continuam dependendo do navegador/sistema e não são modelos próprios deste projeto. Leia [os resultados completos](native-ai/experiments/RESULTADOS.md), [a origem dos dados](native-ai/docs/PROVENANCE.md) e [o laboratório de treinamento](native-ai/README.md).

## Compatibilidade

| Componente | Versão necessária |
| --- | --- |
| Edição do jogo | Minecraft **Java Edition 26.2** |
| Carregador | Fabric Loader **0.19.5 ou superior**, compatível com 26.2 |
| Dependência | Fabric API **0.161.0+26.2**, ou versão posterior para 26.2 |
| Java para compilar e executar | **Java 25 ou superior compatível**; o projeto usa Java 25 |
| Mod | Luma `0.2.0-experimental` |

`26.2` é a versão do **Minecraft**, não o número do Fabric Loader. O mod não é para Bedrock, celular/PE, Forge ou NeoForge. Você precisa ter acesso oficial ao Minecraft Java Edition.

## Primeira conversa

Em um mundo de teste, abra o chat com `T` e digite:

```text
/luma criar
/luma seguir
/luma falar Oi, Luma!
/luma voz
```

O último comando fornece um link local para o painel de voz. Abra o link completo no mesmo computador, clique em **Ativar conexão e voz** e então autorize o microfone se quiser usá-lo. O texto também funciona sem microfone. Veja os comandos, a entrega de materiais e a IA própria opcional no [guia completo](docs/INSTALAR.md).

## Estado da validação

O JAR foi gerado com `BUILD SUCCESSFUL` usando as dependências oficiais do Minecraft 26.2. Passaram **60 testes JUnit do mod**, **48 do laboratório de IA** e **sete cenários da Luma dentro de um servidor Minecraft real sem interface gráfica**; o teste interno do runner também passou. A integração gerou texto com o checkpoint incluído, com teste de cancelamento e timeout. A avaliação das respostas não demonstrou conversa útil. Aparência em tela, microfone, reprodução audível e desempenho no computador do jogador continuam sem teste. Consulte [TESTES.md](docs/TESTES.md) para a cobertura e as pendências.

## Para desenvolver

Com um JDK 25 instalado, execute `./gradlew build` no Linux/macOS ou `gradlew.bat build` no Windows. O Gradle baixa as dependências, compila e executa os testes automatizados, incluindo o servidor GameTest. O JAR instalável será gerado em `build/libs/luma-companion-0.2.0-experimental.jar`. O arquivo com sufixo `-sources.jar` contém fontes e não deve ser instalado como mod. O modelo já treinado acompanha o código em `native-ai/mod-resources`; você não precisa treiná-lo para compilar o mod.

No ambiente de nuvem Linux x86_64, `bash scripts/setup-cloud.sh` instala o JDK verificado e configura o proxy/TLS do Gradle antes do build. Cada tarefa já é isolada: use o checkout existente, sem criar worktrees, salvo pedido explícito. `bash scripts/test-core.sh` permite diagnosticar apenas texto e HTTP sem baixar o Minecraft; não substitui os GameTests.

Para desenvolver o motor próprio separadamente, execute `./gradlew -p native-ai build`. Os comandos de treino, avaliação e conversa estão no [laboratório](native-ai/README.md). O antigo adaptador HTTP permanece no código para referência e testes, mas não é usado por `/luma ia ligar` nesta versão; variáveis `LUMA_AI_*` não configuram o modelo embutido.

Licença: [MIT](LICENSE).
