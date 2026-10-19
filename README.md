# Luma — Companhia de aventura

Luma é uma companheira humanoide para **Minecraft Java Edition 26.2 com Fabric**. Ela tem uma skin própria em roxo e ciano, acompanha o dono, recebe ordens em português do Brasil e pode responder em voz alta por um painel no navegador.

**[Baixar o pacote pronto para instalar](https://github.com/lucasmac412/Mods/releases/download/v0.1.0/Luma-Fabric-26.2.zip)** — inclui Luma, Fabric API e o guia `COMECE-AQUI.txt`. O pacote é uma versão inicial; confira os testes e os limites abaixo.

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
- Pode conversar por um modelo de IA opcional, como Ollama. As ordens básicas funcionam sem esse modelo.

Ela é um NPC com comportamentos programados. Não joga como uma pessoa em todas as situações, não termina o Minecraft sozinha, não fabrica qualquer receita e não transforma pedidos livres em construções arbitrárias. A autonomia só funciona enquanto o mundo e a região da companheira estão sendo processados pelo jogo, com o dono conectado na mesma dimensão. As tarefas com blocos respeitam a regra `minecraft:mob_griefing`; não há integração genérica com proteção de terrenos de outros mods.

A voz usa síntese de fala do sistema ou navegador. A qualidade depende das vozes instaladas; não há uma atriz dublando as respostas nem garantia de voz humana. O reconhecimento do microfone geralmente precisa de Chrome ou Edge e pode depender de internet e dos serviços do fornecedor do navegador.

## Compatibilidade

| Componente | Versão necessária |
| --- | --- |
| Edição do jogo | Minecraft **Java Edition 26.2** |
| Carregador | Fabric Loader **0.19.5 ou superior**, compatível com 26.2 |
| Dependência | Fabric API **0.161.0+26.2**, ou versão posterior para 26.2 |
| Java para compilar e executar | **Java 25 ou superior compatível**; o projeto usa Java 25 |
| Mod | Luma `0.1.0` |

`26.2` é a versão do **Minecraft**, não o número do Fabric Loader. O mod não é para Bedrock, celular/PE, Forge ou NeoForge. Você precisa ter acesso oficial ao Minecraft Java Edition.

## Primeira conversa

Em um mundo de teste, abra o chat com `T` e digite:

```text
/luma criar
/luma seguir
/luma falar Oi, Luma!
/luma voz
```

O último comando fornece um link local para o painel de voz. Abra o link completo no mesmo computador, clique em **Ativar conexão e voz** e então autorize o microfone se quiser usá-lo. O texto também funciona sem microfone. Veja os comandos, a entrega de materiais e a IA opcional no [guia completo](docs/INSTALAR.md).

## Estado da validação

O JAR foi gerado com `BUILD SUCCESSFUL` usando as dependências oficiais do Minecraft 26.2. Passaram **50 testes JUnit** e **sete cenários da Luma dentro de um servidor Minecraft real sem interface gráfica**; o teste interno do runner também passou. Foram exercitados comandos, inventário, navegação, mineração, construção, combate e serialização. A aparência em tela, o microfone, as vozes do navegador e um modelo real de conversa ainda precisam de teste no computador do jogador. Consulte [TESTES.md](docs/TESTES.md) para a cobertura e as pendências.

## Para desenvolver

Com um JDK 25 instalado, execute `./gradlew build` no Linux/macOS ou `gradlew.bat build` no Windows. O Gradle baixa as dependências, compila e executa os testes automatizados, incluindo o servidor GameTest. O JAR instalável será gerado em `build/libs/luma-companion-0.1.0.jar`. O arquivo com sufixo `-sources.jar` contém fontes e não deve ser instalado como mod.

No ambiente de nuvem Linux x86_64, `bash scripts/setup-cloud.sh` instala o JDK verificado e configura o proxy/TLS do Gradle antes do build. Cada tarefa já é isolada: use o checkout existente, sem criar worktrees, salvo pedido explícito. `bash scripts/test-core.sh` permite diagnosticar apenas texto e HTTP sem baixar o Minecraft; não substitui os GameTests.

Não coloque chaves de serviços de IA no código. As opções `LUMA_AI_URL`, `LUMA_AI_MODEL` e `LUMA_AI_KEY` são variáveis de ambiente opcionais; veja o guia antes de configurá-las.

Licença: [MIT](LICENSE).
