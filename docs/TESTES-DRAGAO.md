# Validação do Dragão Celestial 0.1.1 experimental

Ambiente de desenvolvimento Linux x86_64, JDK 25, Minecraft Java 26.2, Fabric Loader 0.19.5 e Fabric API 0.161.0+26.2. O mod é independente da Luma e do Ollama.

A versão **0.1.1** usa frequência aumentada temporária para facilitar o desenvolvimento: um centro de bosque e ninho por região de **256 × 256 blocos**, com raio de 192 blocos. São 36 vezes mais centros por área do que na 0.1.0; bosques vizinhos podem se unir. Essa mudança só aparece em regiões novas do Overworld. Prefira um mundo novo para testar.

## Servidor e configuração

Em 20/10/2026, `./gradlew --no-daemon -p dragon-mod build` terminou com `BUILD SUCCESSFUL`: **quatro testes JUnit e 18 GameTests passaram**, sem falhas. Dos GameTests, **17 são cenários do mod e um é o controle interno do Fabric**.

A amostragem real da fonte de biomas encontrou o Bosque Celestial em **65.216 de 65.536 posições (99,51%)**, numa grade de 8.192 × 8.192 blocos, de −4.096 até 4.064 em cada eixo, com passo de 32 blocos. A verificação exige mais de 98% de cobertura e falharia na frequência antiga. Essa porcentagem descreve a amostra; não significa que cada bloco de qualquer mundo seja celestial.

Os cenários verificam:

- Quebrar cada seção do ovo em Sobrevivência solta um único item; perder o suporte ou sofrer uma explosão também não duplica ovos. Quebrar no Criativo não solta item.
- Incubação conserva dono e progresso ao salvar, gera um único dragão domado e espera quando o nascimento está bloqueado. Quebrar a incubadora ocupada devolve seu ovo.
- O ninho gigante contém um ovo completo e um guardião selvagem, sem duplicação ao reprocessar a mesma estrutura.
- O bioma é registrado no Overworld, cobre mais de 98% da grade de amostragem na frequência de testes e preserva a seleção do Nether.
- Dono, animais domados, dragões do mesmo dono e membros da equipe do dono ficam protegidos da mordida e do raio.
- Paredes interrompem o raio; o ataque não destrói blocos, não passa de 35 blocos e causa um único acerto por alvo em cada disparo.
- Os intervalos de mordida e raio são respeitados. Uma vaca consegue reagir e causar dano mesmo sem atributo de ataque vanilla.
- Dono, ninho, vida e intervalos sobrevivem ao salvamento. Somente o dono montado consegue enviar controles válidos.
- Alterar vida máxima atualiza dragões existentes sem curá-los e vale para novos dragões; diminuir o máximo limita a vida atual.
- Configurações inválidas, NaN e infinitos são rejeitados. Falhas de gravação não aplicam valores que não foram salvos.

Relatórios reproduzíveis: `dragon-mod/build/test-results/test/TEST-*.xml` e `dragon-mod/build/test-results/gametest.xml`.

## Cliente gráfico e geração natural

`./gradlew --no-daemon -p dragon-mod runClientGameTest` executa dois cenários em um cliente real do Minecraft, com OpenGL por software e uma tela virtual na nuvem:

1. **Modelo, configuração e montaria:** renderiza os dragões selvagem e domado; abre o botão do Esc; rejeita NaN na tela e no servidor; salva vida 240, mordida 14 e raio 35, confirmando os valores no servidor. Monta com o dono, usa as entradas reais W/Espaço/Ctrl, aciona o raio pelo botão direito e desmonta com Shift. A configuração original é restaurada no final.
2. **Geração natural e persistência:** cria um mundo **Normal, semente 1**, usando o gerador real de terreno, sem montar um ninho por comando. Verifica bosque, fundação do ninho com 41 blocos de largura, ovo de três partes, árvores, folhas, solo, afloramentos de rocha, minério e localização da estrutura. Confirma um guardião selvagem; fecha e reabre o mundo e exige o mesmo UUID, sem duplicação. Uma verificação de conexão das rochas ao solo detecta o erro visual de rochas flutuantes encontrado e corrigido durante o desenvolvimento.

**Os dois cenários gráficos passaram em 20/10/2026**, com `BUILD SUCCESSFUL` e saída 0. Na amostra de 16 chunks periféricos foram contados **563 blocos de tronco, 5.905 de folhas, 25 de minério e zero grupos de rochas sem apoio**. O mesmo guardião selvagem permaneceu após reabrir o mundo. As contagens estão no [relatório de geração natural](dragao/natural-generation.json); descrevem esta amostra, não uma garantia de densidade para qualquer semente.

As capturas são do jogo, e não das imagens de conceito:

- [Dragões selvagem e domado](dragao/01-dragoes.png).
- [Menu com valores confirmados pelo servidor](dragao/02-configuracao.png).
- [Jogador montado](dragao/03-montaria.png). O enquadramento não mostra claramente o feixe; a ativação foi verificada pelo estado do servidor e o dano pelos GameTests.
- [Bosque e ninho gerados naturalmente](dragao/04-bosque-e-ninho.png).
- [Contagens do mundo e resultado da recarga](dragao/natural-generation.json).

Os arquivos originais de cada execução ficam em `dragon-mod/build/reports/client-smoke` e `dragon-mod/build/reports/client-natural`.

## Recursos e distribuição

O JAR instalável inclui o modelo articulado, texturas originais, sons OGG originais, blocos, receita, bioma e estrutura. Os testes de desenvolvimento ficam fora dele. O empacotador verifica o Fabric API oficial por SHA-256, os recursos obrigatórios, os sons referenciados e o CRC dos arquivos ZIP/JAR; o pacote contém os dois JARs e os guias. `downloads/SHA256SUMS.txt` registra o checksum do ZIP.

Os sons foram gerados proceduralmente pelo projeto; não são gravações de outra obra. Os seis OGG são mono, 22.050 Hz. **A reprodução audível não foi conferida:** o ambiente gráfico de nuvem não disponibiliza um dispositivo OpenAL, e o Minecraft desativou sua saída de áudio. Confira volume e sons no seu computador.

## Limites desta versão

É uma versão experimental com frequência de bioma aumentada para testes. As verificações cobrem cenários definidos; não comprovam compatibilidade com todos os mods, biomas de terceiros ou todas as sementes. A reação acrescentada às criaturas usa a IA de objetivos do Minecraft; chefes e criaturas de outros mods que usam sistemas próprios podem se comportar de outra maneira. Itens, blocos e entidades sem comportamento de criatura não passam a atacar.

Não houve teste de desempenho na RTX 4060 do jogador, de instalação no Windows nem de uma sessão multiplayer entre computadores reais. O teste gráfico utiliza renderização por software na nuvem. Os ataques e as configurações são decididos pelo servidor; isso também é exercitado pelos testes, mas não substitui uma partida multiplayer real.

Use um mundo novo para o primeiro teste e faça backup de mundos importantes. O conteúdo natural só aparece em regiões novas. Remover um mod que adiciona blocos pode causar perda desses blocos ao reabrir o mundo.

## Repetir as verificações

Com JDK 25, na raiz do repositório:

```bash
./gradlew --no-daemon -p dragon-mod build
./gradlew --no-daemon -p dragon-mod runClientGameTest
python3 scripts/package-dragon.py
```

O segundo comando exige ambiente gráfico OpenGL. O `build` executa os testes JUnit e do servidor, sem precisar de tela. O empacotamento exige o build concluído e o pacote anterior `downloads/Luma-Fabric-26.2.zip`, de onde reaproveita o Fabric API oficial verificado; não instala a Luma junto com o dragão.
