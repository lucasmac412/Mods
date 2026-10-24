# Revisão de animação — 0.1.1

A 0.1.1 substitui as poses por coreografias com preparação, extensão no tick de dano, continuação e recuperação. Os ombros acompanham a rotação do peito. A troca entre guarda, posturas e movimentos mistura as poses por dois ticks. Atualizações periódicas da rede não reiniciam o relógio de um golpe já em andamento. A primeira pessoa usa a mesma coreografia dos braços.

Passaram **13 testes JUnit**, **9 testes de combate do servidor + o teste vanilla** e o teste de cliente real com **Dragão Celestial 0.3.5** carregado. Os três testes novos verificam todas as posturas e movimentos: valores finitos, continuidade, recuperação, sincronização do jab com o dano e giros em um único sentido. [Relatório](moonfist/animation-v011.json).

[Prévia animada capturada no cliente Minecraft](moonfist/animacoes-v011.gif): o Discípulo executa soco, gancho, chute giratório, pesado, esquiva e especial. São 80 capturas automáticas apresentadas a 10 quadros/s; os intervalos de captura não constituem uma medição de fluidez ou FPS. O modelo continua usando os membros cúbicos do Minecraft. A qualidade artística e o conforto ainda precisam da avaliação do jogador.

As informações e capturas abaixo registram a versão inicial; os cenários de controles, dano, menus e ovos foram repetidos na 0.1.1.

# Validação — Punhos da Lua Azul 0.1.0 experimental

Implementação original para Minecraft Java 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2 e Java 25. O [relatório estruturado](moonfist/validation.json) identifica os cenários e os hashes das capturas do cliente real.

## Resultados

- **10 testes JUnit aprovados:** combos das três posturas, entrada antecipada no final do golpe e cobrança única, expiração de combo, custo de stamina, compromisso dos ataques, oito etapas com registro de alvos, foco/recarga após trocar item, guarda sem renovação artificial do aparo, janela da esquiva, quebra de guarda/contra-ataque e regeneração limitada.
- **9 testes de combate no servidor real aprovados**, mais o teste padrão `minecraft:always_pass`: preparação e impacto único do jab; dano dos 12 golpes leves/pesados; oito danos efetivos do especial; paredes bloqueando socos e ondas; alcance e dano único da onda; aparo frontal com ganho de recursos; esquiva sem imunidade ao ambiente; cancelamento ao trocar item e rejeição de NaN; invocação adulta equipada e proteção de parceiros.
- **Cliente Minecraft real com OpenGL aprovado:** clique do mouse causando dano confirmado pelo servidor, Z trocando postura, C fixando alvo, G mantendo guarda, Alt movendo o jogador de verdade, R iniciando onda/recarga, Caps iniciando o especial/recarga, renderização em primeira/terceira pessoa, menu de pausa, ovo vanilla após retirar as luvas e duelo no Survival com o Discípulo causando dano no jogador.
- O último teste de cliente carregou também **Dragão Celestial 0.3.4**, executando os mesmos cenários. A montaria do dragão não foi exercitada por este teste; as condições de ativação das luvas excluem passageiros e preservam a câmera escolhida por montarias.
- Build de produção concluído; JAR instalável contém os recursos de cliente/servidor e não inclui o módulo de testes. O empacotador valida metadados, testes, CRC e SHA-256 do Fabric API oficial e de cada arquivo do ZIP.

O auxiliar antigo `makeMockServerPlayerInLevel()` do Minecraft 26.2 fixa o modo Criativo, mesmo após pedir Survival. Por isso os casos de dano recebido usam o Discípulo como defensor no servidor; o caso do cliente usa um jogador Survival real e confirma o duelo. Não são contados testes inicialmente falhos como aprovados.

## Capturas do jogo

[Postura e interface](moonfist/01-stance-and-hud.png) · [Rolamento](moonfist/04-directional-dodge.png) · [Onda](moonfist/05-shockwave.png) · [Cortes do Vazio](moonfist/07-eight-void-cuts.png) · [Luvas em primeira pessoa](moonfist/08-first-person-compact-gloves.png) · [Ovos vanilla](moonfist/09-vanilla-eggs-still-work.png) · [Duelo Survival](moonfist/10-survival-duel.png).

São capturas do mod rodando no motor do Minecraft. A arena e o adversário imóvel com vida aumentada são preparados pelo teste; não representam uma estrutura natural acrescentada ao mundo. No último cenário, a IA do adversário é ativada e o jogador recebe dano em Survival. A inspeção visual confirma o carregamento das poses, luvas e efeitos; a avaliação da fluidez e do balanceamento em uma partida humana continua pendente.

## Repetir

Na raiz do repositório, com JDK 25:

```sh
./gradlew --no-daemon -p moonfist-mod build
./gradlew --no-daemon -p moonfist-mod runClientGameTest
python3 scripts/package-moonfist.py
```

Em Linux com Xvfb/Mesa, `bash scripts/test-moonfist-client.sh` cria uma tela virtual e usa OpenAL sem saída física. `MOONFIST_XVFB` permite indicar o caminho do Xvfb. Para repetir a coexistência, passe `-PcompatDragon=/caminho/celestial-dragon-0.3.4-experimental.jar` ao teste de cliente. O teste gráfico salva imagens em `moonfist-mod/build/reports/client/`.

Não houve teste em Windows/RTX 4060, com shaders, com outro sistema de combate nem em duas máquinas conectadas. As verificações não medem FPS no computador do jogador e não garantem compatibilidade com todo mod. O combate usa poses no esqueleto cúbico padrão do Minecraft; não é uma reprodução integral do combate ou da animação de Nioh. Áudio usa eventos do próprio Minecraft.
