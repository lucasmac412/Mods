# Validação do Dragão Celestial 0.3.4 experimental

Ambiente de desenvolvimento Linux x86_64, JDK 25, Minecraft Java 26.2, Fabric Loader 0.19.5 e Fabric API 0.161.0+26.2. O mod é independente da Luma e do Ollama.

A versão **0.3.4** prolonga os dois rugidos para cerca de 6,8 segundos, baixa seu tom e substitui as duas mordidas por impactos curtos. A mandíbula abre menos de 45 graus, fecha no quinto tick, junto ao dano, e retorna gradualmente ao repouso. O pescoço se desloca menos de 0,3 bloco; a cabeça mantém a direção do golpe. A Lanterna rúnica celestial recebe três texturas próprias e opacas de 32 × 32, com modelo cúbico de seis faces, sem camadas sobrepostas nem animação de textura. Seu registro continua `celestial_dragon:celestial_lantern`; os 17 itens e os blocos já colocados são preservados. Mantém as demais mecânicas da 0.3.3.

**Bioma raro novamente na 0.3.5:** a distribuição original foi restaurada: um centro de bosque e ninho por região de **1.536 × 1.536 blocos**, com raio de 192 blocos. São **36 vezes menos centros por área** do que nas versões de testes 0.1.1–0.3.4. Os bosques ficam separados. A redução vale para regiões ainda não geradas; bosques e ninhos já existentes continuam no mundo.

## Resultados desta versão

Em **21 de outubro de 2026**, passaram **7 testes JUnit**, **31 GameTests de servidor** (30 cenários do mod e um controle interno do Fabric) e os **cinco cenários de cliente** descritos abaixo.

No teste com entradas reais, a Genki Dama acionada por R removeu **480 blocos da superfície** da plataforma. Em duas janelas de 16 ticks, W percorreu cerca de **11,15 blocos** e W + Shift cerca de **25,08 blocos**, mantendo o jogador montado. Isso confirma a aceleração nesse cenário; não é uma medição de desempenho no computador do jogador.

Os três novos testes JUnit verificam mandíbula fechada no tick do dano, limites de abertura e deslocamento sem saltos e recuperação sem acumular transformações entre ataques.

## Raridade restaurada na 0.3.5

Build aprovado com **7 testes JUnit** e **31 testes do servidor** (inclui o teste padrão `minecraft:always_pass`). A seleção real de biomas encontrou **3152/65536 amostras celestiais: 4,81%** de uma grade de 8.192 × 8.192 blocos, com passo de 32 blocos. A separação e o espaçamento voltaram aos valores da 0.1.0, compartilhados pelo bioma e pelo ninho. [Relatório desta revisão](dragao/rarity-v035.json).

A comparação binária dos JARs 0.3.4 e 0.3.5 encontra somente três entradas alteradas: os metadados da versão e as classes de distribuição do bioma e dos ninhos (o compilador incorpora as constantes nas duas classes). Sons, texturas, animações e combate mantêm os mesmos bytes. Os testes gráficos descritos abaixo são das revisões anteriores; não foram repetidos nesta alteração de frequência. Regiões já geradas não são reescritas nem apagadas.

## Servidor e configuração

O comando `./gradlew --no-daemon -p dragon-mod build` compila as partes de servidor e cliente e executa JUnit e GameTests. Os cenários do servidor verificam:

- Quebrar cada seção do ovo em Sobrevivência solta um único item; perder o suporte ou sofrer uma explosão também não duplica ovos. Quebrar no Criativo não solta item.
- Incubação conserva dono e progresso ao salvar, gera um único dragão domado e espera quando o nascimento está bloqueado. Quebrar a incubadora ocupada devolve seu ovo.
- Os pilares do santuário são contínuos do piso até o topo. O reparo de uma arena antiga preenche os espaços vazios, conserva um bloco colocado pelo jogador e pode ser repetido sem danos.
- O ninho gigante contém um ovo completo e um guardião selvagem, sem duplicação ao reprocessar a mesma estrutura.
- Os dois ovos de criação produzem as versões selvagem e domada com o dono correto, não funcionam em Sobrevivência e esperam uma área desobstruída. Dispensers apenas devolvem esses ovos como itens.
- A aba do Criativo contém todos os 17 itens registrados, sem duplicatas, e permite encontrá-los na busca.
- O bioma é registrado no Overworld e, na 0.3.5, deve cobrir entre 4% e 6% da grade de amostragem de 8.192 × 8.192 blocos; o teste usa a seleção real de biomas, verifica o alinhamento do ninho e preserva a seleção do Nether.
- Dono, animais domados, dragões do mesmo dono e membros da equipe do dono ficam protegidos da mordida e do raio.
- Paredes interrompem o raio; o ataque não destrói blocos, não passa de 35 blocos e causa um único acerto por alvo em cada disparo.
- A mordida não causa dano durante a preparação e acerta no quinto tick. O raio carrega por dez ticks antes de causar dano e não atinge novos alvos durante a recuperação. Um golpe não começa durante a animação do outro.
- Os intervalos de mordida e raio são respeitados. A morte durante a preparação cancela o ataque pendente. Uma vaca consegue reagir e causar dano mesmo sem atributo de ataque vanilla.
- A Genki Dama carrega por 40 ticks, atinge 60 blocos de diâmetro e viaja a 0,30 bloco por tick. O impacto destrói exatamente a faixa de 20 × 24 × 6 no cenário de pedra preparado, preservando as bordas e um bloco de bedrock. Recarga e exclusão mútua com outros golpes são verificadas.
- O impacto numa copa de folhas também remove o solo seis blocos abaixo da superfície; a mira horizontal acerta um corredor de terreno mesmo com bordas da esfera em chunks não carregados.
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
4. **Visual e animações:** apresenta o corpo serpentino com apenas duas patas dianteiras longas e garras curvas e tomadas próximas da juba e do focinho em três quartos, perfil e frente, além das fases de preparação e impacto da mordida, carga e disparo do raio. Confere poses finitas, ondulação lateral, repetição sem acumular transformações e mais de uma volta na pose de sono, além de malha com vértices finitos e iluminação válida nas pontas. O cenário também fotografa a nova lanterna de dia e à noite. Para o GIF, o mundo é congelado e avança um tick por quadro usando os comandos vanilla; são poses do modelo real, apresentadas a 10 quadros por segundo (metade da velocidade de 20 ticks por segundo). As capturas permitem avaliar forma, tamanho e postura; valores de estado sozinhos não comprovam qualidade visual.

5. **Áudio real no motor do jogo:** recebe do servidor rugido, respiração, vento, mordida, carga e disparo de raio, ferimento, morte e formação/lançamento/impacto da Genki Dama. Começa a 26 blocos e depois exercita a montaria e a câmera ampliada. Exige os eventos resolvidos no motor OpenAL carregado e distâncias de atenuação de pelo menos 48 blocos. Nesta execução foi usado o dispositivo digital OpenAL Soft Wave File Writer; a saída PCM gravada contém áudio não nulo. Não é um teste de caixas de som nem da configuração do Windows.

Na amostra de geração natural desta execução, o teste encontrou 599 blocos de tronco, 6.032 de folhas e 12 de minério, sem componentes de rocha separados do solo. O guardião continuou sendo o mesmo após salvar e reabrir. As [contagens completas](dragao/natural-generation.json) descrevem essa amostra; não garantem a mesma densidade para qualquer semente.

As capturas são do jogo, e não das imagens de conceito. As novas capturas da mordida e da lanterna são da 0.3.4. As demais imagens e contagens foram registradas na 0.3.2 e ilustram os recursos preservados:

- [Dragões selvagem e domado](dragao/01-dragoes.png).
- [Menu com valores confirmados pelo servidor](dragao/02-configuracao.png).
- [Jogador montado](dragao/03-montaria.png).
- [Bosque e ninho gerados naturalmente](dragao/04-bosque-e-ninho.png).
- [Novo corpo e ondulação em movimento](dragao/ondulacao.gif).
- [Perfil do dragão serpentino](dragao/05-dragao-serpentino.png).
- [Mordida: preparação](dragao/06-mordida-preparo.png) e [impacto](dragao/07-mordida-impacto.png).
- [Mordida em câmera lenta na 0.3.4](dragao/mordida.gif).
- [Lanterna rúnica celestial de dia](dragao/24-lanterna-runica.png) e [à noite](dragao/25-lanterna-runica-noite.png).
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
- [Sono em espiral, visto de perto](dragao/23-sono-espiral.png).
- [Pilares apoiados e modelos simplificados à noite](dragao/22-pilares-corrigidos-noite.png).
- [Contagens do mundo e resultado da recarga](dragao/natural-generation.json).

Os arquivos originais de cada execução ficam em `dragon-mod/build/reports/`, nas pastas de cada cenário: `client-smoke`, `client-natural`, `client-eggs` `client-visual-v7` e `client-audio`.

## Recursos e distribuição

O JAR instalável inclui o modelo articulado, texturas originais, sons OGG com créditos e licença de áudio, blocos, receita, bioma e estrutura. Os testes de desenvolvimento ficam fora dele. O empacotador verifica o Fabric API oficial por SHA-256, os recursos obrigatórios, os sons referenciados e o CRC dos arquivos ZIP/JAR; o pacote contém os dois JARs e os guias. `downloads/SHA256SUMS.txt` registra o checksum do ZIP.

As vocalizações agora combinam trechos gravados de **“Large Monster”, Michael Klier (CC BY-SA 3.0)**, e **“Lion raring-sound1TamilNadu178”, த*உழவன் (domínio público, PD-self)**. A edição usa filtros, velocidade/tom, prolongamento com Rubber Band, camadas graves e fades, sem saturação nem estalos de ruído agudo. Os efeitos de energia e vento complementam essa montagem com síntese. Os 15 recursos desta revisão estão sob **CC BY-SA 3.0**; os créditos e o texto integral acompanham o JAR e o ZIP. O código Java mantém sua licença MIT. Fontes e hashes: `third-party/dragon-audio/` no repositório. O YouTube bloqueou o acesso ao vídeo indicado; a referência efetivamente usada foi a resposta textual do jogador: “Grave e prolongado, como um monstro gigante”. Não houve extração de áudio desse vídeo nem avaliação humana do resultado na nuvem.

São **15 OGG mono em 44.100 Hz**, registrados em 12 eventos (11 do dragão e um do bioma). Rugido, mordida e dor têm duas variações. `dragon-mod/tools/generate-sounds.py` reproduz os recursos a partir das fontes incluídas usando NumPy, SciPy e FFmpeg; essas ferramentas não são necessárias para jogar. Para regenerar só estes sons, use `python3 dragon-mod/tools/generate-sounds.py --only roar roar_2 bite bite_2`. As texturas da lanterna são geradas por `dragon-mod/tools/generate-lantern.py` com Pillow, usado apenas no desenvolvimento. A revisão muda quatro arquivos; os outros onze mantêm os mesmos bytes da 0.3.3. A prévia `OUVIR-SONS.html` no ZIP permite ouvir cada recurso no Chrome. O [relatório de áudio](dragao/audio-validation.json) registra os eventos e alcances reais; o [relatório dos arquivos](dragao/sound-design.json) registra duração, níveis, fontes e SHA-256 dos 15 recursos. O teste digital verifica o carregamento e a reprodução pelo motor, não a qualidade artística do timbre. A reprodução física no Windows e a avaliação humana do novo timbre continuam pendentes. A gravação completa inclui sons do Minecraft e não é redistribuída.

## Limites desta versão

É uma versão experimental com a frequência original rara do bioma restaurada na 0.3.5. As verificações cobrem cenários definidos; não comprovam um aumento específico de FPS nem compatibilidade com todos os mods, biomas de terceiros ou todas as sementes. A reação acrescentada às criaturas usa a IA de objetivos do Minecraft; chefes e criaturas de outros mods que usam sistemas próprios podem se comportar de outra maneira. Itens, blocos e entidades sem comportamento de criatura não passam a atacar.

Não houve teste de desempenho na RTX 4060 do jogador, de instalação no Windows nem de uma sessão multiplayer entre computadores reais. O teste gráfico utiliza renderização por software na nuvem. Os ataques e as configurações são decididos pelo servidor; isso também é exercitado pelos testes, mas não substitui uma partida multiplayer real.

Use um mundo novo para o primeiro teste e faça backup de mundos importantes. O conteúdo natural só aparece em regiões novas. Remover um mod que adiciona blocos pode causar perda desses blocos ao reabrir o mundo.

## Repetir as verificações

Com JDK 25, na raiz do repositório:

```bash
./gradlew --no-daemon -p dragon-mod build
./gradlew --no-daemon -p dragon-mod runClientGameTest
python3 scripts/package-dragon.py
```

O segundo comando exige ambiente gráfico OpenGL e um dispositivo OpenAL. Nesta nuvem, os testes usaram `ALSOFT_DRIVERS=wave` e `ALSOFT_CONF=/workspace/.cache/celestial-dragon/audio-v034/alsoft.conf`, com destino WAV no workspace. Um dispositivo digital valida o processamento sem representar a saída física do jogador. O `build` executa os testes JUnit e do servidor, sem precisar de tela. O empacotamento exige o build concluído e o pacote anterior `downloads/Luma-Fabric-26.2.zip`, de onde reaproveita o Fabric API oficial verificado; não instala a Luma junto com o dragão.
