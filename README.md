# Luma — Companhia de aventura

**Novo mod independente: [Dragão Celestial — ouro e obsidiana](dragon-mod/README.md).** Dragão chinês montável, voo, mordida, raio de energia, bosque, ninhos e incubação. [Baixar o pacote pronto do dragão](https://raw.githubusercontent.com/lucasmac412/Mods/dragao-v0.3.0-experimental/downloads/Dragao-Celestial-Fabric-26.2-v0.3.0-Experimental.zip) · [Instalar passo a passo](docs/INSTALAR-DRAGAO.md).

A versão **0.3.0 do dragão** traz câmera de montaria afastada, aceleração com raios, Genki Dama gigante, raio com aura, patas dianteiras alongadas sem patas traseiras e arena de chefe com 113 blocos. O guardião dorme em um santuário e desperta com barra de vida quando um jogador em Sobrevivência entra na arena. O Bosque Celestial continua extremamente comum para testes. [Instalação e controles](docs/INSTALAR-DRAGAO.md).

Luma é uma companheira humanoide para **Minecraft Java Edition 26.2 com Fabric**. Ela tem uma skin própria em roxo e ciano, acompanha o dono, recebe ordens em português do Brasil e pode conversar pelo Ollama e receber ordens por uma tecla de microfone dentro do jogo com o complemento **Luma Voz**.

**[Baixar Luma 0.4.0 experimental com Ollama e voz](https://raw.githubusercontent.com/lucasmac412/Mods/v0.4.0-experimental/downloads/Luma-Fabric-26.2-Ollama-e-Voz-v0.4.0-Experimental.zip)** — inclui Luma, Luma Voz, Fabric API, reconhecimento de voz offline em português e o guia `COMECE-AQUI.txt`. O Ollama e seu modelo de conversa são instalados separadamente pelo site oficial, conforme o guia.

**Comece pelo [passo a passo de instalação](docs/INSTALAR.md).** Ele explica onde colocar os arquivos mesmo para quem nunca instalou um mod. Se você recebeu um pacote de instalação com os JARs, use esses arquivos. O ZIP do código-fonte é diferente: ele precisa ser compilado para virar um mod.

## O que a Luma faz

- Segue, espera, protege o dono e pode ser chamada de volta.
- Recebe itens da sua mão a até seis blocos de distância, usa uma ferramenta ou arma de cada vez e tem uma mochila de 27 espaços.
- Combate corpo a corpo com espadas e ferramentas; não usa arcos, escudos ou armaduras.
- Minera até 16 blocos por ordem: troncos com machado e pedras simples com picareta adequada. Não extrai minérios nem escava túneis.
- Constrói um abrigo fixo de 5 × 5 com 96 blocos entregues por você, deixando uma entrada aberta.
- No modo autônomo, acompanha, enfrenta inimigos próximos e pode coletar até oito troncos por ativação, se tiver um machado.
- Entende ordens simples pelo chat, inclusive `Luma, me siga`.
- Conecta a conversa ao Ollama local, com modelo configurável e histórico curto durante a sessão.
- Com Luma Voz no cliente, recebe ordens pelo microfone ao segurar uma tecla, usando reconhecimento offline em português, e oferece leitura das respostas pela voz do sistema.
- Mantém o painel de voz no navegador como alternativa para mundos de um jogador.
- Preserva o pequeno modelo próprio experimental como opção separada. As ordens básicas funcionam com a IA desligada.

Ela é um NPC com comportamentos programados. Não joga como uma pessoa em todas as situações, não termina o Minecraft sozinha, não fabrica qualquer receita e não transforma pedidos livres em construções arbitrárias. A autonomia só funciona enquanto o mundo e a região da companheira estão sendo processados pelo jogo, com o dono conectado na mesma dimensão. As tarefas com blocos respeitam a regra `minecraft:mob_griefing`; não há integração genérica com proteção de terrenos de outros mods.

A fala usa síntese do sistema ou navegador, conforme a opção escolhida. O timbre e o idioma dependem das vozes instaladas; não há atriz, dublagem gravada ou clonagem de voz. O complemento usa Vosk com um modelo de português de terceiros para reconhecer áudio localmente. O painel no navegador pode usar reconhecimento online do fornecedor do navegador.

## Conversa com Ollama

Instale o [Ollama](https://ollama.com/download), deixe-o aberto e baixe um modelo no PowerShell/terminal:

```text
ollama pull qwen2.5:3b
```

No jogo, use `/luma ia ollama` ou `/luma ia ligar`, seguido de `/luma falar Como podemos começar nossa aventura?`. O mod acessa o Ollama em `127.0.0.1:11434`, no computador que executa o mundo/servidor. Não exige chave de API ou assinatura. O Ollama e o modelo Qwen são software e pesos de terceiros; o projeto não os treinou.

A cada conversa, a Luma informa um resumo confirmado do próprio estado, vida/dimensão/posição do jogador e até quatro turnos anteriores. Isso não fornece visão da tela nem conhecimento completo do mundo. O histórico é temporário; desligar, trocar o provedor/modelo ou sair da sessão o descarta. A primeira resposta pode demorar até dois minutos enquanto o modelo carrega. `/luma ia status` mostra a configuração, sem testar a conexão.

As respostas do modelo são **somente falas**. Ordens simples continuam imediatas na lógica do mod; uma resposta não executa comandos arbitrários nem acrescenta novas construções. O modelo ainda pode errar ou afirmar algo que não ocorreu.

Para a RTX 4060 com 16 GB de RAM, `qwen2.5:3b` é um ponto de partida, sem garantia de desempenho junto com Minecraft. Se faltar memória ou o jogo ficar lento, experimente `ollama pull qwen2.5:1.5b` e `/luma ia modelo qwen2.5:1.5b`. O Ollama pode aproveitar hardware compatível; o gerador próprio antigo continua usando CPU. Não compre hardware ou serviços para experimentar.

## Microfone com uma tecla

Instale também `luma-voice-0.4.0-experimental.jar` e copie a pasta `config/luma-voice/model` do pacote para a pasta de jogo do seu perfil. Com o mundo aberto e sem menus, **segure V, aguarde “Pode falar agora”, fale uma frase curta e solte V**. O texto reconhecido é enviado como `/luma falar ...`; você não precisa começar a frase com “Luma”. Use **Opções → Controles** para mudar a tecla.

A primeira tentativa prepara o modelo; se você soltar antes de gravar, aguarde e tente de novo. O limite é de 15 segundos por gravação. **K** liga/desliga a leitura das respostas e salva essa preferência. O reconhecimento usa áudio local, sem enviar a gravação ao Ollama. A fala usa a voz padrão do sistema; no Windows/macOS, instale/configure uma voz pt-BR. No Linux, a biblioteca nativa usa Flite em inglês e não garante pronúncia pt-BR. O complemento é cliente: não é um chat de voz com outros jogadores. Consulte [OLLAMA-E-VOZ.md](docs/OLLAMA-E-VOZ.md) para instalação, controles e solução de problemas.

## IA própria preservada

`/luma ia propria` seleciona o experimento original de 277.504 parâmetros, com código Java, pesos próprios e memória autoral incluídos no JAR. O treinamento começou com pesos aleatórios e o refinamento usou somente checkpoints do projeto. Ele não usa Ollama e não aprende durante a partida. É muito limitado: no teste sintético da versão 0.3, foram **11/40 respostas úteis**, pela avaliação Codex, sem revisão humana independente. Esse número **não avalia o modelo do Ollama**. Leia [os resultados e falhas](native-ai/experiments-v2/RESULTADOS.md) e [a origem dos dados](native-ai/data-v2/PROVENANCE.md).

## Compatibilidade

| Componente | Versão necessária |
| --- | --- |
| Edição do jogo | Minecraft **Java Edition 26.2** |
| Carregador | Fabric Loader **0.19.5 ou superior**, compatível com 26.2 |
| Dependência | Fabric API **0.161.0+26.2**, ou versão posterior para 26.2 |
| Java para compilar e executar | **Java 25 ou superior compatível**; o projeto usa Java 25 |
| Mods | Luma e Luma Voz `0.4.0-experimental` |

`26.2` é a versão do **Minecraft**, não o número do Fabric Loader. O mod não é para Bedrock, celular/PE, Forge ou NeoForge. Você precisa ter acesso oficial ao Minecraft Java Edition.

## Primeira conversa

Depois da instalação, crie um mundo de teste, abra o chat com `T` e digite uma linha por vez:

```text
/luma criar
/luma seguir
/luma ia ollama
/luma falar Oi, Luma!
```

Com Luma Voz e o modelo de reconhecimento instalados, feche o chat, segure V e diga “me siga” ou “como podemos começar?”. Solte para enviar. As ordens conhecidas funcionam mesmo com o Ollama desligado. `/luma parar` interrompe a tarefa e cancela uma resposta pendente. O [guia de instalação](docs/INSTALAR.md) explica cada etapa para iniciantes.

## Estado da validação

A cobertura de engenharia, os comandos de verificação e as pendências estão em [TESTES.md](docs/TESTES.md). Testes de HTTP, transcrição de áudio preparado e servidor sem interface não comprovam funcionamento do microfone/voz no computador do jogador. Cliente gráfico, microfone real, reprodução audível e desempenho com Minecraft e Ollama precisam ser conferidos no computador de uso.

## Para desenvolver

Com um JDK 25 instalado, execute `./gradlew build` no Linux/macOS ou `gradlew.bat build` no Windows. O Gradle baixa as dependências, compila e executa os testes automatizados, incluindo o servidor GameTest. O JAR instalável será gerado em `build/libs/luma-companion-0.4.0-experimental.jar`. O arquivo com sufixo `-sources.jar` contém fontes e não deve ser instalado como mod. O modelo e o índice já acompanham o código em `native-ai/mod-resources`; você não precisa treiná-los para compilar o mod.

No ambiente de nuvem Linux x86_64, `bash scripts/setup-cloud.sh` instala o JDK verificado e configura o proxy/TLS do Gradle antes do build. Cada tarefa já é isolada: use o checkout existente, sem criar worktrees, salvo pedido explícito. `bash scripts/test-core.sh` permite diagnosticar apenas texto e HTTP sem baixar o Minecraft; não substitui os GameTests.

Para desenvolver o motor próprio separadamente, execute `./gradlew -p native-ai build`. Os comandos de treino, avaliação e conversa estão no [laboratório](native-ai/README.md). Para compilar Luma Voz, execute `./gradlew -p voice-input build`; o JAR fica em `voice-input/build/libs`. O adaptador legado `RemoteBrain` permanece para referência/testes; a integração atual usa `OllamaBrain`, sem variáveis `LUMA_AI_*`. O arquivo de reconhecimento Vosk é distribuído com avisos/licenças de terceiros; o modelo de conversa do Ollama é baixado pelo próprio usuário.

Licença: [MIT](LICENSE).
