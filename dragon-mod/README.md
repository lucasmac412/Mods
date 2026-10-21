# Dragão Celestial — Ouro e Obsidiana

Mod independente para **Minecraft Java Edition 26.2 + Fabric**, versão **0.2.0-experimental**. Adiciona um dragão chinês negro e dourado, voo montado, mordida, raio de energia, o Bosque Celestial, ninhos gigantes, ovos coletáveis e uma incubadora. Pode ser instalado sem a Luma.

**[Baixar o pacote pronto](https://raw.githubusercontent.com/lucasmac412/Mods/dragao-v0.2.0-experimental/downloads/Dragao-Celestial-Fabric-26.2-v0.2.0-Experimental.zip)** · **[Instalação passo a passo](https://github.com/lucasmac412/Mods/blob/dragao-v0.2.0-experimental/docs/INSTALAR-DRAGAO.md)** · **[Ver o movimento no jogo](https://raw.githubusercontent.com/lucasmac412/Mods/dragao-v0.2.0-experimental/docs/dragao/ondulacao.gif)**

O ZIP contém o JAR do dragão e o Fabric API compatível. Instale Minecraft 26.2, Fabric Loader 0.19.5 ou superior compatível e Java 25. Copie os dois JARs para a pasta `mods` do perfil Fabric. No multiplayer, instale os mesmos arquivos no servidor e nos clientes. Esta é uma versão experimental; faça uma cópia do mundo antes de usá-la e prefira um mundo novo para explorar a geração.

**Bioma comum para testes:** o Bosque Celestial continua muito comum, como na 0.1.1. Há um centro de bosque e ninho por região de **256 × 256 blocos**, em vez de 1.536 × 1.536 na 0.1.0: **36 vezes mais centros por área**. O raio continua em 192 blocos, então bosques vizinhos podem se unir em áreas extensas. Essa frequência é temporária durante o desenvolvimento; o equilíbrio final será definido depois.

**Novo na 0.2.0:** dragão maior, com cerca de 30 blocos de comprimento, corpo de 24 segmentos com ondulação lateral e cabeça mais ornamentada. A mordida tem preparação, avanço e recuperação; o raio reúne energia antes do disparo. O inventário Criativo oferece todos os 12 itens do mod e dois ovos para criar dragões diretamente.

Para atualizar de qualquer versão 0.1.x, feche o jogo e substitua o JAR antigo por `celestial-dragon-0.2.0-experimental.jar`; mantenha apenas uma versão do dragão na pasta `mods`. O Fabric API continua o mesmo. Para ver a geração mais comum, crie um mundo novo ou explore regiões ainda não geradas: áreas já exploradas não são refeitas.

## Testar pelo Criativo

No modo Criativo, pressione **E**. A aba **Dragão Celestial** reúne todos os itens e blocos deste mod. Se a aba estiver em outra página, clique no botão **Dragão Celestial**, no canto superior esquerdo do inventário, para abri-la diretamente.

Os dois itens abaixo criam um dragão imediatamente ao clicar com o botão direito num bloco, em uma área ampla e sem água:

- **Ovo de criação — Dragão celestial selvagem:** cria um dragão sem dono.
- **Ovo de criação — Dragão celestial domado:** cria um dragão que pertence ao jogador que usou o ovo e pode ser montado por ele.

Esses ovos funcionam somente no Criativo e não têm receita. São diferentes do **Ovo celestial** grande, de três blocos, usado na incubadora em Sobrevivência.

```text
/give @s celestial_dragon:wild_dragon_spawn_egg
/give @s celestial_dragon:tamed_dragon_spawn_egg
```

## Encontrar e criar seu dragão em Sobrevivência

Os dragões selvagens aparecem nos ninhos do **Bosque Celestial**, no Overworld. Não há aparecimento natural fora dos ninhos. O bosque adiciona terreno, rochas, madeira, folhas e minério próprios. A geração aparece em áreas novas do mundo; chunks já explorados mantêm seu terreno.

Com comandos ativados, use:

```text
/locate biome celestial_dragon:celestial_grove
/locate structure celestial_dragon:dragon_nest
```

O ovo ocupa três blocos de altura e cerca de dois de largura. Em Sobrevivência, quebrar qualquer uma das três partes devolve um único ovo, que pode ser colocado novamente em um espaço livre. Em Criativo não há drop: use o inventário criativo ou `/give @s celestial_dragon:celestial_egg`. O dragão selvagem protege o ninho e não pode ser montado diretamente.

Fabrique a **Incubadora celestial** em uma bancada, sem formato obrigatório:

| Ingrediente | Quantidade | Como reconhecer |
| --- | ---: | --- |
| Pedra celestial | 2 | Mineral em forma de gema, extraído do minério celestial. |
| Madeira celestial | 1 | Tronco das árvores do bosque. |
| Rocha celestial | 1 | Bloco de terreno rochoso do bosque. |

Coloque a incubadora, segure o ovo e clique nela com o botão direito. A incubação leva **6.000 ticks, aproximadamente cinco minutos**, enquanto a região permanece carregada e processando o mundo. O dono do dragão será o jogador que inserir o ovo. O dragão é maior nesta versão. Deixe uma área bem aberta acima e ao redor: se o dragão não couber, o ovo aguarda dentro da incubadora. Quebrá-la devolve o ovo inserido; em Sobrevivência também cai o bloco da incubadora. A incubação continua do ponto salvo quando a região é carregada novamente.

## Montar, voar e atacar

Somente o dono pode montar o dragão domado: aproxime-se com a mão vazia e clique com o botão direito, sem segurar Shift.

| Controle padrão | Ação enquanto montado |
| --- | --- |
| W, A, S, D | Mover. |
| Mouse | Orientar a direção. |
| Espaço | Subir. |
| Ctrl, tecla de correr | Descer. |
| Shift, tecla de agachar | Desmontar. |
| Botão esquerdo do mouse | Morder. |
| Botão direito do mouse, com as duas mãos vazias | Disparar o raio de energia. |
| Botão direito com item em qualquer mão | Usar o item normalmente, inclusive ovos de criaturas do Minecraft. |

As teclas de pular, correr e agachar acompanham suas configurações de controles do Minecraft. A mordida prepara o golpe antes de causar dano. O raio carrega por aproximadamente meio segundo antes do feixe. Um ataque aguarda o fim da animação do outro, e há intervalos entre usos. Dragões podem atacar criaturas e jogadores; o dragão domado protege seu dono, os animais do dono e outros dragões do mesmo dono. Aliados da mesma equipe são respeitados. Jogadores em Criativo ou Espectador não são alvos.

A percepção alcança 35 blocos, como o atributo do zumbi vanilla. A mordida tem alcance curto; o raio alcança até 35 blocos e para em paredes. A seleção de alvos e os ataques exigem linha de visão.

## Vida e dano

Dentro do mundo, abra **Esc → Dragão Celestial**. O dono do mundo individual ou um administrador do servidor pode alterar a vida máxima e o dano dos dois ataques. Os valores são globais para dragões selvagens e domados. Jogadores sem permissão podem consultar os valores.

Os padrões são **200 pontos de vida**, **12 de dano de mordida** e **30 de dano do raio**. Dois pontos de vida correspondem a um coração. Alterar a vida máxima não cura os dragões existentes. A configuração é salva no servidor, em `config/celestial-dragon.json`.

## Compilar e verificar

Com JDK 25 instalado, execute a partir da raiz deste repositório:

```bash
./gradlew -p dragon-mod build
./gradlew -p dragon-mod runGameTest
```

No Windows, use `gradlew.bat -p dragon-mod build`. O mod instalável fica em `dragon-mod/build/libs/celestial-dragon-0.2.0-experimental.jar`; o arquivo `-sources.jar` contém o código-fonte e não deve ser instalado como mod.

A suíte do servidor verifica configuração, ovos, incubação, combate e proteção de aliados. Os testes gráficos verificam modelo, menu, controles montados, uso dos ovos normais do Minecraft e geração em mundo Normal com recarga. Veja os [resultados, capturas reais e limites](https://github.com/lucasmac412/Mods/blob/dragao-v0.2.0-experimental/docs/TESTES-DRAGAO.md). A nuvem não tem saída de áudio; a reprodução dos sons precisa ser conferida no computador do jogador.
