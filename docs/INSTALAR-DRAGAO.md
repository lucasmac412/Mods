# Instalar o Dragão Celestial no Minecraft Java 26.2

Este guia é para **Minecraft Java Edition no computador**, versão **26.2**. O Dragão Celestial é um mod Fabric independente da Luma.

**[Baixar Dragao-Celestial-Fabric-26.2-v0.3.3-Experimental.zip](https://raw.githubusercontent.com/lucasmac412/Mods/dragao-v0.3.3-experimental/downloads/Dragao-Celestial-Fabric-26.2-v0.3.3-Experimental.zip)**

**Bioma comum para testes:** como na versão 0.1.1, enquanto desenvolvemos e testamos o mod, o Bosque Celestial está muito comum: um centro de bosque e ninho por região de **256 × 256 blocos**, com raio de 192 blocos. São **36 vezes mais centros por área** do que na 0.1.0, e bosques vizinhos podem se unir. Essa frequência é temporária para facilitar os testes.

A **0.3.3 troca as vocalizações sintéticas por gravações de rugido de leão e de criatura**, editadas em camadas. Remove a saturação e os estalos agudos dos efeitos anteriores e suaviza a energia. São 15 arquivos em 44,1 kHz, com variações de rugido, dor e mordida e efeitos próprios para o raio e a Genki Dama. O áudio está sob CC BY-SA 3.0, com créditos e licença incluídos no pacote. O ZIP também inclui **OUVIR-SONS.html**: abra no Chrome para ouvir cada som sem iniciar o jogo.

A **Genki Dama agora tem 60 blocos de diâmetro**, três vezes o anterior. Forma acima do dragão durante dois segundos e continua viajando devagar. O impacto abre uma faixa de **20 × 24 blocos**, até seis blocos abaixo do terreno de cada coluna, preservando bedrock e blocos indestrutíveis. Quando atinge copas, remove também folhas e troncos entre o contato e o chão (busca limitada a 64 blocos). A mira horizontal que não encontra bloco procura terreno carregado à frente; bordas da esfera em chunks não carregados não cancelam mais um impacto válido. Não força carregamento de regiões.

O guardião dorme com o corpo em uma espiral de voltas mais apertadas e sobrepostas, cabeça elevada e respiração leve, seguindo a referência da cobra. A postura mistura gradualmente com o voo ao acordar. A ondulação do voo ficou mais lenta, longa e suave, com menor balanço vertical e esforço interpolado; a fase não reinicia ao acelerar ou parar.

A nova arena do ninho mede **113 blocos de diâmetro**, com oito torres, anéis rúnicos, muralha e um santuário elevado. Acrescenta **tijolos celestiais, pedra rúnica, obsidiana celestial dourada, pilar do santuário e lanterna do santuário**, disponíveis na aba do Criativo e coletáveis na arena. Seus modelos combinam texturas do projeto e texturas já fornecidas pelo Minecraft.

O guardião natural começa **dormindo enrolado**, imóvel em sua plataforma. Visitas em Criativo ou Espectador não o despertam. A entrada de um jogador em Sobrevivência ou Aventura no interior da arena o acorda; atacá-lo também o acorda. Surge a barra de chefe no topo da tela para jogadores próximos. O estado de sono é salvo com o mundo; durante o repouso há respiração discreta. O dragão criado pelo ovo selvagem do Criativo não gera uma arena ao redor de si.

Estas correções funcionam no mundo atual. O visual dos blocos muda ao atualizar o mod; os apoios antigos são reparados com o guardião presente. A arena maior introduzida na 0.3.0 continua aparecendo somente em regiões geradas com essa estrutura; mundos da 0.1.x ou 0.2.x precisam de regiões novas para encontrá-la. Cliente e servidor precisam usar a mesma versão 0.3.3.

## 1. Baixe e extraia o pacote

Abra o link acima. No navegador, `Ctrl + J` mostra os downloads. Localize o ZIP baixado, clique com o botão direito e escolha **Extrair tudo**. Dentro da pasta extraída há dois arquivos para instalar:

| Arquivo | Função |
| --- | --- |
| `celestial-dragon-0.3.3-experimental.jar` | Dragão, bosque, ninho, ovo e incubadora. |
| `fabric-api-0.161.0+26.2.jar` | Dependência usada pelo mod. |

Esses JARs ficam na pasta `mods` do pacote. Você vai copiá-los para o jogo no passo 4. Mantenha os JARs fechados; não é necessário executá-los ou descompactá-los. Um ZIP que contém apenas `src`, `gradle` e arquivos de programação é o código-fonte, e precisa ser compilado conforme o [README do projeto](https://github.com/lucasmac412/Mods/blob/dragao-v0.3.3-experimental/dragon-mod/README.md).

## 2. Abra o Minecraft Java 26.2 uma vez

1. Abra o Minecraft Launcher e entre na conta que possui Minecraft Java Edition.
2. Em **Minecraft: Java Edition → Instalações**, crie ou selecione uma instalação da versão **26.2**.
3. Abra o jogo até o menu principal e feche-o.

Use a versão exata. Minecraft Bedrock, celular e console não carregam este mod.

## 3. Instale o Fabric

1. Baixe o [instalador oficial do Fabric](https://fabricmc.net/use/installer/).
2. Abra o instalador. Na aba **Client**, selecione Minecraft **26.2** e Fabric Loader **0.19.5 ou superior compatível**.
3. Deixe a criação do perfil habilitada e clique em **Install**.
4. Abra o Launcher e selecione o perfil **Fabric 26.2**.

O jogo precisa de **Java 25**. O Launcher oficial normalmente administra seu próprio Java. Se o instalador não abrir ou aparecer a mensagem `requires Java 25`, instale o [JDK 25 do Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25) apropriado ao seu sistema. Nas opções avançadas do perfil Fabric, selecione o executável desse Java quando necessário.

## 4. Copie os dois JARs para o jogo

Confira **Instalações → seu perfil Fabric → Editar → Diretório do jogo**. É a pasta desse perfil que deve receber os arquivos. Se você usa Prism ou outro gerenciador, abra a pasta da instância por ele.

Os locais padrão do Launcher oficial são:

| Sistema | Pasta do jogo |
| --- | --- |
| Windows | `%APPDATA%\.minecraft` |
| macOS | `~/Library/Application Support/minecraft` |
| Linux | `~/.minecraft` |

No Windows, pressione `Windows + R`, cole `%APPDATA%\.minecraft` e aperte Enter para abrir a pasta padrão.

1. Feche o Minecraft. Faça uma cópia da pasta `saves` antes de usar esta versão experimental em um mundo importante.
2. Abra a subpasta **mods** do jogo. Crie-a se ainda não existir.
3. Copie os **dois JARs** da pasta `mods` do ZIP extraído para essa pasta do jogo.
4. Se já houver outra versão do Dragão Celestial ou do Fabric API, guarde a versão substituída fora de `mods`. Deixe uma única versão de cada mod.
5. Abra o Launcher, selecione **Fabric 26.2** e clique em **Jogar**.

**Se você já usa a 0.3.1 ou qualquer versão anterior:** feche o jogo, mova o JAR antigo do dragão para fora de `mods` e coloque `celestial-dragon-0.3.3-experimental.jar` no lugar. Nunca deixe duas versões do dragão juntas. O Fabric API não mudou; mantenha uma única cópia compatível.

O resultado deve ser parecido com:

```text
.minecraft/
  mods/
    celestial-dragon-0.3.3-experimental.jar
    fabric-api-0.161.0+26.2.jar
```

Não coloque o ZIP inteiro ou o arquivo `-sources.jar` em `mods`. A Luma não é necessária para jogar com o dragão. Em um servidor multiplayer, os mesmos dois JARs devem estar no servidor Fabric e nos computadores dos jogadores.

## 5. Abra a aba do Criativo e crie um dragão de teste

Crie um **mundo novo do tipo Normal, em Criativo, com comandos ativados**. Pressione **E** para abrir o inventário. A aba **Dragão Celestial** reúne todos os 17 itens e blocos do mod.

Se a aba não estiver visível, clique no botão **Dragão Celestial** no canto superior esquerdo do inventário. Ele abre diretamente a página e a aba do mod.

Para testar sem esperar a incubação, pegue um destes ovos:

| Item | O que acontece ao usá-lo |
| --- | --- |
| **Ovo de criação — Dragão celestial selvagem** | Cria um dragão sem dono, que não aceita montaria direta. |
| **Ovo de criação — Dragão celestial domado** | Cria um dragão que pertence a você e pode ser montado por você. |

Segure o ovo e clique com o botão direito num bloco ou no chão, em uma área bem aberta e sem água. Esses ovos funcionam somente no **Criativo**, não têm receita e são diferentes do ovo grande usado na incubadora.

Com comandos, os mesmos itens podem ser obtidos assim:

```text
/give @s celestial_dragon:wild_dragon_spawn_egg
/give @s celestial_dragon:tamed_dragon_spawn_egg
```

O **Ovo celestial** de três blocos e a incubadora continuam disponíveis para testar o processo de Sobrevivência.

## 6. Encontre um ninho

Para o primeiro teste, crie um **mundo novo em Criativo, com comandos ativados**. O Bosque Celestial aparece com muita frequência no Overworld, com árvores, solo, rochas e minério próprios. Cada centro de geração contém um ninho gigante com um dragão selvagem e um ovo. Como bosques vizinhos podem se unir, uma área celestial extensa pode conter vários ninhos. Os dragões selvagens surgem nesses ninhos.

O aumento da frequência não modifica áreas já exploradas. Se atualizar um mundo antigo, viaje para regiões ainda não geradas; criar um mundo novo é a forma mais simples de experimentar esta versão.

Abra o chat com `T`, digite uma linha e pressione Enter:

```text
/locate biome celestial_dragon:celestial_grove
```

Para localizar o ninho diretamente, use:

```text
/locate structure celestial_dragon:dragon_nest
```

O jogo informa as coordenadas. Você pode viajar até elas ou, com comandos, usar `/tp @s X Y Z`, substituindo X, Y e Z por números e escolhendo uma altura segura. Em um mundo antigo, explore regiões ainda não geradas: o mod preserva o terreno de chunks já explorados.

O dragão selvagem protege o ninho. Em Sobrevivência, para ter um dragão seu, recolha o ovo e use a incubadora; o dragão selvagem não aceita montaria direta.

## 7. Incube seu ovo

O ovo tem **três blocos de altura e cerca de dois de largura**. Em **Sobrevivência**, quebre qualquer uma das partes para recolher **um único ovo**. Você pode recolocá-lo em uma área livre com piso firme. Em **Criativo**, quebrar o ovo o remove sem soltar item; pegue outro pela aba **Dragão Celestial** ou pelo comando `/give` abaixo.

Fabrique uma **Incubadora celestial** na bancada usando os quatro ingredientes abaixo, em qualquer posição:

| Quantidade | Ingrediente |
| ---: | --- |
| 2 | **Pedra celestial**: o mineral em forma de gema, obtido ao minerar minério celestial com picareta de ferro ou superior. |
| 1 | **Madeira celestial**: tronco das árvores do bosque. |
| 1 | **Rocha celestial**: o bloco rochoso do terreno, obtido com picareta. |

1. Coloque a incubadora em uma área bem aberta, com espaço livre acima e ao redor para o dragão nascer.
2. Segure o ovo e clique com o botão direito na incubadora. Ela recebe um ovo por vez.
3. Aguarde **cinco minutos de mundo em processamento**, equivalentes a 6.000 ticks. Fique perto para a região continuar carregada. Em servidores lentos, os cinco minutos de ticks podem levar mais tempo no relógio.
4. Clique na incubadora para consultar a porcentagem. Se o aviso pedir espaço, desobstrua a área acima e ao redor; o ovo permanece seguro aguardando.
5. Quando nascer, o dragão já estará domado para **o jogador que inseriu o ovo**.

Não é preciso combustível adicional. Ao sair do mundo ou descarregar a região, a incubação pausa e guarda o progresso. Quebrar uma incubadora ocupada devolve o ovo; em Sobrevivência também cai o bloco da incubadora. Uma nova incubação começa do zero ao reinserir o ovo.

No Criativo, a aba **Dragão Celestial** também oferece o ovo, os materiais e a incubadora. Para obter um ovo de teste por comando:

```text
/give @s celestial_dragon:celestial_egg
/give @s celestial_dragon:celestial_incubator
```

## 8. Monte e controle o dragão

Com a mão vazia, clique com o botão direito no seu dragão. Somente o dono consegue montar. Solte Shift antes de tentar subir nele.

| Controle padrão | Ação |
| --- | --- |
| W, A, S, D | Mover enquanto montado. |
| Mouse | Orientar a direção do voo. |
| Espaço | Subir. |
| Ctrl | Descer, usando a tecla configurada para correr. |
| Shift | Acelerar com aura elétrica; combine com W para avançar mais rápido. |
| Dois toques rápidos em Ctrl | Desmontar, usando a tecla configurada para correr. |
| R | Genki Dama. |
| Clique esquerdo | Mordida. |
| Clique direito com as duas mãos vazias | Raio de energia. |
| Clique direito com item em qualquer mão | Usa o item normalmente, inclusive ovos de criaturas do Minecraft. |

Se você mudou as teclas do Minecraft, use as suas teclas de **pular**, **correr** e **agachar** para subir, descer e acelerar; desmontar exige dois toques rápidos na tecla de correr. A mordida recolhe a cabeça antes do impacto. O raio carrega por aproximadamente meio segundo antes de disparar. Os ataques têm recuperação e intervalo entre usos; não começam juntos.

Os ovos normais do Minecraft continuam sendo usados com clique direito, inclusive enquanto você está montado. Aponte para um bloco próximo, dentro do alcance do jogador. Se um animal aparece e logo morre perto do dragão, ele pode ter sido atacado: o dragão continua agressivo com outras criaturas. Afaste-se do dragão para conferir um ovo sem esse combate. No modo Pacífico, monstros como zumbis são removidos pelo próprio Minecraft.

Dragões podem atacar criaturas e jogadores. O seu dragão respeita você, seus animais domados, outros dragões do mesmo dono e aliados de equipe. Jogadores em Criativo ou Espectador não são alvos.

O alcance de percepção é de **35 blocos**, igual ao atributo padrão de um zumbi nesta versão. Isso não é a distância de uma mordida: ela atinge apenas de perto. O raio alcança até 35 blocos e para em paredes. O dragão precisa ter linha de visão para escolher um alvo e atacá-lo.

## Sons e reparo da arena antiga

Em **Opções → Música e sons**, deixe **Volume geral** e **Criaturas hostis** acima de zero. O guardião dormindo só respira baixinho; acordá-lo e usar ataques produz os efeitos sonoros. Com comandos ativados, teste o recurso diretamente:

```text
/playsound celestial_dragon:dragon.roar hostile @s ~ ~ ~ 1 1
```

A atualização troca automaticamente os modelos de todos os blocos já colocados. Para os apoios dos pilares de uma arena da 0.3.0, volte à arena com o guardião presente e deixe a região carregar. O reparo preenche apenas o ar sob as colunas existentes, sem reconstruir o ninho nem trocar seus blocos sólidos. Se o guardião já tiver sido removido, esse reparo automático não roda; arenas novas já nascem corretas.

## 9. Ajuste vida e dano

Dentro do mundo, pressione **Esc** e clique em **Dragão Celestial**. O dono do mundo individual ou um administrador do servidor pode editar os valores e salvar. Os demais jogadores conseguem consultar a configuração.

Os padrões são **200 pontos de vida**, **12 de dano da mordida** e **30 de dano do raio**. **Dois pontos de vida equivalem a um coração.** Os valores valem para todos os dragões, selvagens e domados. Aumentar a vida máxima não cura os dragões existentes.

O arquivo de configuração fica em `config/celestial-dragon.json`, na pasta do jogo ou do servidor. A tela permite alterar vida de 1 a 1.024 pontos e danos de 0 a 1.000. O limite de vida acompanha o atributo padrão do Minecraft.

## Se algo não funcionar

| O que aconteceu | O que conferir |
| --- | --- |
| O jogo acusa dependência ausente. | Confira os dois JARs, Fabric Loader e Minecraft 26.2. |
| Aparece erro de Java. | Confira se o perfil usa Java 25. |
| O conteúdo não aparece. | Confirme que iniciou o perfil Fabric e copiou os JARs para a pasta daquele perfil. |
| A aba do mod não aparece no Criativo. | Pressione E e clique no botão Dragão Celestial no canto superior esquerdo. |
| O ovo de criação do dragão não funciona. | Use o modo Criativo e clique num bloco em uma área ampla, sem água. Ele é diferente do ovo da incubadora. |
| Um ovo normal do Minecraft não cria a criatura ou ela desaparece. | Aponte para um bloco próximo. Confira se o modo Pacífico removeu um monstro ou se o dragão atacou a criatura recém-criada. |
| O dragão não faz som. | Confira Volume geral e Criaturas hostis. Teste o comando de som acima; o guardião dormindo só respira baixinho. |
| O raio não dispara enquanto estou montado. | Esvazie as duas mãos, incluindo a mão secundária. Com um item na mão, o clique direito usa esse item. |
| O bosque não aparece perto de casa. | Explore chunks novos ou use os comandos `/locate` em um mundo de teste. |
| O ovo não pode ser colocado. | Libere uma área de três por três blocos e três blocos de altura, sobre um piso firme. |
| A incubação fica em 100%. | Abra espaço acima e ao redor da incubadora para o corpo do dragão. |
| Não consigo montar. | Use seu dragão nascido da incubadora ou criado por você com o ovo domado do Criativo, mão vazia e sem Shift. |
| Não consigo alterar vida e dano. | Somente o dono do mundo individual ou um administrador do servidor pode salvar alterações. |

Ao relatar um problema, informe a versão do Minecraft, a versão do mod, o que fez e o que aconteceu. O arquivo `logs/latest.log` da pasta do jogo ajuda a localizar falhas de carregamento.
