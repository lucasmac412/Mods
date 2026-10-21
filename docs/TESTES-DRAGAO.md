# Validação do Dragão Celestial 0.3.1 experimental

Ambiente de desenvolvimento Linux x86_64, JDK 25, Minecraft Java 26.2, Fabric Loader 0.19.5 e Fabric API 0.161.0+26.2. O mod é independente da Luma e do Ollama.

A versão **0.3.1** corrige apoios, modelos de blocos e áudio, mantendo o modelo com juba volumosa e focinho alongado, patas dianteiras alongadas com garras curvas, sem patas traseiras e escamas maiores e pontudas nas costas e no corpo. Mantém a ondulação lateral, os dois ovos de criação e a aba do Criativo. O raio continua exigindo as duas mãos vazias, para permitir o uso normal dos itens enquanto montado.

A frequência de testes da 0.1.1 foi preservada: um centro de bosque e ninho por região de **256 × 256 blocos**, com raio de 192 blocos. São 36 vezes mais centros por área do que na 0.1.0; bosques vizinhos podem se unir. Essa geração só aparece em regiões novas do Overworld. Prefira um mundo novo para testar.

## Resultados desta versão

Em **21 de outubro de 2026**, passaram **4 testes JUnit**, **29 GameTests de servidor** (28 cenários do mod e um controle interno do Fabric) e os **cinco cenários de cliente** descritos abaixo.

No teste com entradas reais, a Genki Dama acionada por R removeu **460 blocos da superfície** da plataforma. Em duas janelas de 16 ticks, W percorreu cerca de **10,08 blocos** e W + Shift cerca de **25,93 blocos**, mantendo o jogador montado. Isso confirma a aceleração nesse cenário; não é uma medição de desempenho no computador do jogador.

## Servidor e configuração

O comando `./gradlew --no-daemon -p dragon-mod build` compila as partes de servidor e cliente e executa JUnit e GameTests. Os cenários do servidor verificam:

- Quebrar cada seção do ovo em Sobrevivência solta um único item; perder o suporte ou sofrer uma explosão também não duplica ovos. Quebrar no Criativo não solta item.
- Incubação conserva dono e progresso ao salvar, gera um único dragão domado e espera quando o nascimento está bloqueado. Quebrar a incubadora ocupada devolve seu ovo.
- Os pilares do santuário são contínuos do piso até o topo. O reparo de uma arena antiga preenche os espaços vazios, conserva um bloco colocado pelo jogador e pode ser repetido sem danos.
- O ninho gigante contém um ovo completo e um guardião selvagem, sem duplicação ao reprocessar a mesma estrutura.
- Os dois ovos de criação produzem as versões selvagem e domada com o dono correto, não funcionam em Sobrevivência e esperam uma área desobstruída. Dispensers apenas devolvem esses ovos como itens.
- A aba do Criativo contém todos os 17 itens registrados, sem duplicatas, e permite encontrá-los na busca.
- O bioma é registrado no Overworld, cobre mais de 98% da grade de amostragem na frequência de testes e preserva a seleção do Nether.
- Dono, animais domados, dragões do mesmo dono e membros da equipe do dono ficam protegidos da mordida e do raio.
- Paredes interrompem o raio; o ataque não destrói blocos, não passa de 35 blocos e causa um único acerto por alvo em cada disparo.
- A mordida não causa dano durante a preparação e acerta no quinto tick. O raio carrega por dez ticks antes de causar dano e não atinge novos alvos durante a recuperação. Um golpe não começa durante a animação do outro.
- Os intervalos de mordida e raio são respeitados. A morte durante a preparação cancela o ataque pendente. Uma vaca consegue reagir e causar dano mesmo sem atributo de ataque vanilla.
- A Genki Dama carrega por 40 ticks, atinge 20 blocos de diâmetro e viaja a 0,30 bloco por tick. O impacto destrói exatamente a faixa de 20 × 24 × 6 no cenário preparado, preservando as bordas e um bloco de bedrock. Recarga e exclusão mútua com outros golpes são verificadas.
- O guardião dorme imóvel com a IA suspensa, conserva o sono ao salvar e pode acordar.
- Pacotes de aceleração, Genki Dama e desmontagem de outro jogador são rejeitados.
- Dono, ninho, vida e intervalos sobrevivem ao salvamento. Somente o dono montado consegue enviar controles válidos.
- Alterar vida máxima atualiza dragões existentes sem curá-los e vale para novos dragões; diminuir o máximo limita a vida atual.
- Configurações inválidas, NaN e infinitos são rejeitados. Falhas de gravação não aplicam valores que não foram salvos.

Relatórios reproduzíveis: `dragon-mod/build/test-results/test/TEST-*.xml` e `dragon-mod/build/test-results/gametest.xml`.

## Cliente gráfico e geração natural

`./gradlew --no-daemon -p dragon-mod runClientGameTest` exercita os seguintes cenários em um cliente real do Minecraft, com OpenGL por software e uma tela virtual na nuvem:

1. **Modelo, configuração e montaria:** renderiza os dragões selvagem e domado; abre o botão do Esc; rejeita NaN na tela e no servidor; salva vida 240, mordida 14 e raio 35, confirmando os valores no servidor. Monta com o dono, usa as entradas reais W/Espaço/Ctrl, aciona o raio pelo botão direito e confere câmera automática e Shift sem desmontar, usa R para a Genki Dama, exige uma cratera real na plataforma, compara a distância de voo com W e W + Shift e desmonta com dois toques em Ctrl. A configuração original é restaurada no final.
2. **Geração natural e persistência:** cria um mundo **Normal, semente 1**, usando o gerador real de terreno, sem montar um ninho por comando. Verifica bosque, fundação do ninho com 113 blocos de largura, ovo de três partes, árvores, folhas, solo, afloramentos de rocha, minério e localização da estrutura. Confirma um guardião selvagem adormecido; fecha e reabre o mundo e exige o mesmo UUID e o estado de sono, sem duplicação. Entra em Sobrevivência para conferir o despertar e a barra de chefe. Uma verificação de conexão das rochas ao solo detecta o erro visual de rochas flutuantes encontrado e corrigido durante o desenvolvimento.
3. **Criativo e ovos do Minecraft:** abre o inventário e o atalho da aba Dragão Celestial. Reproduz um dragão interceptando a mira de um ovo e exige que o clique crie a criatura no bloco alcançável atrás dele. Usa cliques reais para criar criaturas com ovos normais em pé, durante a montaria com as mãos principal e secundária e após desmontar. Confere que os ovos não acionam o raio e que ele continua disponível com as duas mãos vazias. O cenário isola o combate e usa dificuldade que permite monstros, para distinguir falha do item de uma criatura morta pelo dragão ou removida pelo modo Pacífico.
4. **Visual e animações:** apresenta o corpo serpentino com apenas duas patas dianteiras longas e garras curvas e tomadas próximas da juba e do focinho em três quartos, perfil e frente, além das fases de preparação e impacto da mordida, carga e disparo do raio. Confere poses finitas, ondulação lateral, repetição sem acumular transformações e malha com vértices finitos e iluminação válida nas pontas. As capturas permitem avaliar forma, tamanho e postura; valores de estado sozinhos não comprovam qualidade visual.

5. **Áudio real no motor do jogo:** recebe do servidor rugido, mordida, raio, ferimento e morte, a 26 blocos do dragão. Exige os eventos resolvidos no motor OpenAL carregado e distâncias de atenuação de pelo menos 48 blocos. Nesta execução foi usado o dispositivo digital OpenAL Soft Wave File Writer; a saída PCM gravada contém áudio não nulo. Não é um teste de caixas de som nem da configuração do Windows.

Na amostra de geração natural desta execução, o teste encontrou 599 blocos de tronco, 6.032 de folhas e 12 de minério, sem componentes de rocha separados do solo. O guardião continuou sendo o mesmo após salvar e reabrir. As [contagens completas](dragao/natural-generation.json) descrevem essa amostra; não garantem a mesma densidade para qualquer semente.

As capturas são do jogo, e não das imagens de conceito:

- [Dragões selvagem e domado](dragao/01-dragoes.png).
- [Menu com valores confirmados pelo servidor](dragao/02-configuracao.png).
- [Jogador montado](dragao/03-montaria.png).
- [Bosque e ninho gerados naturalmente](dragao/04-bosque-e-ninho.png).
- [Novo corpo e ondulação em movimento](dragao/ondulacao.gif).
- [Perfil do dragão serpentino](dragao/05-dragao-serpentino.png).
- [Mordida: preparação](dragao/06-mordida-preparo.png) e [impacto](dragao/07-mordida-impacto.png).
- [Raio: carga](dragao/08-carga-raio.png) e [disparo](dragao/09-raio-disparado.png).
- [Aba do Criativo com os 17 itens](dragao/10-criativo-itens.png).
- [Cabeça e juba em três quartos](dragao/11-cabeca-tres-quartos.png).
- [Perfil do focinho e mandíbula](dragao/12-perfil-cabeca.png).
- [Juba vista de frente](dragao/13-juba-frontal.png).
- [Garras curvas em detalhe](dragao/14-garras-ombro.png).
- [Escamas pontudas das costas e do corpo](dragao/15-escamas-costas.png).
- [Genki Dama: formação](dragao/16-genki-formacao.png), [viagem lenta](dragao/17-genki-voo.png) e [cratera real](dragao/18-genki-cratera.png).
- [Guardião dormindo no santuário](dragao/19-guardiao-dormindo.png) e [despertar com barra de chefe](dragao/20-chefe-desperto.png).
- [Aceleração com raios](dragao/21-aceleracao-raios.png).
- [Pilares apoiados e modelos simplificados à noite](dragao/22-pilares-corrigidos-noite.png).
- [Contagens do mundo e resultado da recarga](dragao/natural-generation.json).

Os arquivos originais de cada execução ficam em `dragon-mod/build/reports/`, nas pastas de cada cenário: `client-smoke`, `client-natural`, `client-eggs` `client-visual-v5` e `client-audio`.

## Recursos e distribuição

O JAR instalável inclui o modelo articulado, texturas originais, sons OGG originais, blocos, receita, bioma e estrutura. Os testes de desenvolvimento ficam fora dele. O empacotador verifica o Fabric API oficial por SHA-256, os recursos obrigatórios, os sons referenciados e o CRC dos arquivos ZIP/JAR; o pacote contém os dois JARs e os guias. `downloads/SHA256SUMS.txt` registra o checksum do ZIP.

Os sons foram gerados proceduralmente pelo projeto; não são gravações de outra obra. Os seis OGG são mono, 22.050 Hz. O OpenAL foi iniciado e recebeu os cinco efeitos do dragão emitidos pelo servidor a 26 blocos. A captura digital da execução contém áudio não nulo (média -24.9 dB, pico -0.0 dB). O [relatório de áudio](dragao/audio-validation.json) registra formato, alcance e limites; a gravação completa inclui sons do Minecraft e não é redistribuída. A saída física de áudio no Windows do jogador ainda precisa ser conferida.

## Limites desta versão

É uma versão experimental com frequência de bioma aumentada para testes. As verificações cobrem cenários definidos; não comprovam um aumento específico de FPS nem compatibilidade com todos os mods, biomas de terceiros ou todas as sementes. A reação acrescentada às criaturas usa a IA de objetivos do Minecraft; chefes e criaturas de outros mods que usam sistemas próprios podem se comportar de outra maneira. Itens, blocos e entidades sem comportamento de criatura não passam a atacar.

Não houve teste de desempenho na RTX 4060 do jogador, de instalação no Windows nem de uma sessão multiplayer entre computadores reais. O teste gráfico utiliza renderização por software na nuvem. Os ataques e as configurações são decididos pelo servidor; isso também é exercitado pelos testes, mas não substitui uma partida multiplayer real.

Use um mundo novo para o primeiro teste e faça backup de mundos importantes. O conteúdo natural só aparece em regiões novas. Remover um mod que adiciona blocos pode causar perda desses blocos ao reabrir o mundo.

## Repetir as verificações

Com JDK 25, na raiz do repositório:

```bash
./gradlew --no-daemon -p dragon-mod build
./gradlew --no-daemon -p dragon-mod runClientGameTest
python3 scripts/package-dragon.py
```

O segundo comando exige ambiente gráfico OpenGL e um dispositivo OpenAL. Nesta nuvem, os testes usaram `ALSOFT_DRIVERS=wave` e `ALSOFT_CONF=/workspace/.cache/celestial-dragon/audio-v031/alsoft.conf`, com destino WAV no workspace. Um dispositivo digital valida o processamento sem representar a saída física do jogador. O `build` executa os testes JUnit e do servidor, sem precisar de tela. O empacotamento exige o build concluído e o pacote anterior `downloads/Luma-Fabric-26.2.zip`, de onde reaproveita o Fabric API oficial verificado; não instala a Luma junto com o dragão.
