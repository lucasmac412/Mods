# Instalar e usar a Luma 0.3.0-experimental, do zero

Este guia é para **Minecraft Java Edition 26.2 no computador**. Não serve para Minecraft de celular, console ou Bedrock Edition. Você precisará de uma conta com acesso ao Minecraft Java Edition.

**[Baixar Luma-Fabric-26.2-IA-Propria-v0.3.0-Experimental.zip](https://raw.githubusercontent.com/lucasmac412/Mods/v0.3.0-experimental/downloads/Luma-Fabric-26.2-IA-Propria-v0.3.0-Experimental.zip)**

No Chrome, abra o link acima e aguarde o download. Aperte `Ctrl + J` para encontrar o ZIP baixado e clique em **Mostrar na pasta**. Clique com o botão direito no ZIP e escolha **Extrair tudo**. O pacote contém a Luma, o Fabric API e este guia: você não precisa programar, compilar ou treinar um modelo para instalá-lo.

A Luma inclui uma **IA de texto própria, pequena e experimental, orientada por uma memória local de informações autorais**. O modelo e a memória já vêm no JAR; basta ativar a opção no jogo. A memória pode escolher uma informação inadequada e o modelo ainda pode gerar texto errado ou sem sentido. A IA começa desligada por padrão. As ordens e as tarefas do NPC continuam disponíveis com ela desligada.

Um ZIP com pastas como `src` e `gradle` é o **código-fonte**. Se você recebeu apenas esse projeto, veja a seção **Gerar o JAR a partir deste projeto** no fim do guia.

## 1. Separe os arquivos

Você precisa destes dois mods:

1. **Luma**: `luma-companion-0.3.0-experimental.jar`. Use o JAR da pasta `mods` do pacote extraído. O modelo de texto e a memória local já estão dentro dele. Para quem compila, a saída original fica em `build/libs`.
2. **Fabric API para Minecraft 26.2**: versão `0.161.0+26.2` ou posterior compatível com 26.2. Use o arquivo incluído no pacote, se houver, ou baixe pela [página do Fabric API no Modrinth](https://modrinth.com/mod/fabric-api/versions), selecionando a versão correta do jogo.

Consulte [o estado dos testes](TESTES.md) para saber o que já foi verificado. A qualidade do modelo de texto é registrada separadamente no [relatório de resultados](https://github.com/lucasmac412/Mods/blob/v0.3.0-experimental/native-ai/experiments-v2/RESULTADOS.md).

Não abra o JAR da Luma com dois cliques para instalar. Ele será colocado em uma pasta específica no passo 4. Não instale o arquivo `-sources.jar`.

## 2. Instale e abra o Minecraft 26.2

1. Abra o Minecraft Launcher oficial e entre na sua conta.
2. Na área **Minecraft: Java Edition**, abra **Instalações** e crie uma instalação para a versão **26.2**.
3. Abra essa versão uma vez até aparecer o menu principal. Depois feche o jogo.

Use exatamente 26.2. Uma versão parecida pode exigir outro mod e outro Fabric API. Se 26.2 não aparecer, atualize o Launcher e confirme que a versão já está disponível para sua conta; não escolha outra versão só por ter um número semelhante.

## 3. Instale o Fabric Loader

1. Acesse o [instalador oficial do Fabric](https://fabricmc.net/use/installer/).
2. Baixe e abra o instalador apropriado para seu sistema.
3. Na aba **Client**, escolha Minecraft **26.2** e Fabric Loader **0.19.5 ou superior compatível**.
4. Mantenha a opção de criar o perfil e clique em **Install**.
5. Abra novamente o Launcher. Selecione o perfil **Fabric** correspondente a 26.2.

Se o instalador Java não abrir, instale o **JDK 25** pelo [site oficial do Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25), escolhendo seu sistema e arquitetura. No Windows, o instalador `.msi` é uma opção simples; habilite as opções de adicionar Java ao `PATH` e definir `JAVA_HOME` quando oferecidas. Depois tente novamente. Se o Fabric disponibilizar um instalador Windows executável, você também pode usá-lo conforme as instruções oficiais.

O perfil precisa executar Java 25. O Launcher oficial costuma gerenciar seu próprio Java. Se houver erro dizendo `requires Java 25`, abra **Instalações → seu perfil Fabric → Editar → Mais opções → Executável Java** e selecione o `javaw.exe` do JDK 25 no Windows ou o `java` correspondente no Linux/macOS.

Se houver erro envolvendo o módulo **`jdk.httpserver`** ou classes **`com.sun.net.httpserver`**, use o **JDK 25 completo** do Temurin nesse mesmo campo do Launcher. Alguns runtimes reduzidos não incluem o componente Java necessário ao painel local de voz.

## 4. Coloque os dois JARs na pasta certa

O que importa é a pasta de jogo usada pelo **perfil Fabric selecionado**. Se você usa Prism Launcher ou outro gerenciador, use a opção de abrir a pasta da instância e depois abra `mods`.

No Launcher oficial, confira **Instalações → seu perfil Fabric → Editar → Diretório do jogo**. Se o campo estiver vazio, o local padrão costuma ser:

| Sistema | Pasta padrão do jogo |
| --- | --- |
| Windows | `%APPDATA%\.minecraft` |
| macOS | `~/Library/Application Support/minecraft` |
| Linux | `~/.minecraft` |

No Windows, aperte `Windows + R`, cole `%APPDATA%\.minecraft` e pressione Enter. A pasta pode ser diferente se você definiu outro diretório no perfil.

1. Feche o Minecraft. Se já usa mods em um mundo importante, copie a pasta `saves` do jogo para outro local antes de atualizar.
2. Entre na pasta do jogo e abra a subpasta **mods**. Crie a pasta `mods` se ela não existir.
3. Se já houver um JAR antigo da Luma, mova-o para uma pasta de backup **fora de `mods`**. Faça o mesmo com um Fabric API antigo que será substituído. Não deixe duas versões do mesmo mod juntas.
4. Abra a pasta **mods do ZIP extraído** e copie os dois JARs para a pasta **mods do jogo**: `luma-companion-0.3.0-experimental.jar` e o Fabric API para 26.2. Não copie o ZIP inteiro.
5. No Launcher, selecione o perfil **Fabric 26.2** e clique em **Jogar**.

Não é necessário colocar o projeto inteiro dentro de `mods`, descompactar os JARs ou instalar outro mod de voz.

## 5. Faça o primeiro teste em um mundo novo

Faça uma cópia de segurança antes de usar qualquer mod em um mundo importante. Para começar, crie um mundo novo em **Criativo**, com comandos ativados, e escolha um lugar plano e aberto.

Abra o chat com `T`, digite uma linha por vez e pressione Enter:

```text
/luma criar
/luma status
/luma seguir
```

Você deverá ver a companheira com aparência humanoide e roupa roxa e ciano. Ande alguns blocos para observar se ela acompanha você. Depois teste:

```text
/luma ficar
/luma falar Oi, Luma!
```

Se o jogo nem chegar ao menu, vá à seção de solução de problemas. Se uma função ainda não foi confirmada em jogo, registre o que aconteceu conforme [TESTES.md](TESTES.md).

## Comandos e conversa

Você controla a sua própria companheira. No chat, comece a frase com o nome dela, por exemplo **`Luma, me siga`**. Para ordens claras, os comandos abaixo são a opção mais previsível.

| Comando | Uso |
| --- | --- |
| `/luma ajuda` | Mostra a ajuda resumida dentro do jogo. |
| `/luma criar` | Cria sua companheira. |
| `/luma seguir` | Pede para acompanhar você. |
| `/luma ficar` | Pede para esperar no lugar. |
| `/luma proteger` | Procura defender você de inimigos próximos. |
| `/luma autonomo` | Ativa a escolha automática entre comportamentos limitados. |
| `/luma minerar` | Procura blocos que consiga minerar com as ferramentas disponíveis. |
| `/luma construir` | Tenta montar um abrigo de formato fixo usando os blocos recebidos. |
| `/luma parar` | Interrompe a tarefa atual. |
| `/luma status` | Mostra a situação da companheira. |
| `/luma entregar` | Entrega o item da mão principal; aproxime-se a até seis blocos. |
| `/luma devolver` | Solicita a devolução dos itens; aproxime-se a até seis blocos. |
| `/luma chamar` | Traz a companheira carregada de volta para perto de você, na mesma dimensão. |
| `/luma localizar` | Mostra a última dimensão e as coordenadas conhecidas da sua companheira. |
| `/luma falar Oi, Luma!` | Envia uma frase diretamente para a Luma. |
| `/luma remover` | Remove sua companheira e devolve os itens; aproxime-se a até seis blocos. |
| `/luma voz` | Abre o acesso ao painel local de voz. |
| `/luma voz parar` | Desliga o painel de voz local. |
| `/luma ia ligar` | Ativa as falas geradas pela IA própria experimental com memória local incluída no mod. |
| `/luma ia desligar` | Desativa as falas do modelo e mantém as respostas básicas e as ordens. |

Exemplos de frases simples: `Luma, me proteja`, `Luma, fique aqui`, `Luma, minere`, `Luma, construa`, `Luma, parar` e `Luma, status`. O modo local reconhece um conjunto limitado de frases. Para interromper, use **parar**; frases negativas como “não construa” não iniciam uma nova tarefa.

### Entregar ferramentas, armas e materiais

1. Aproxime-se da Luma, a até **seis blocos**, na mesma dimensão.
2. Coloque a ferramenta, arma ou pilha de blocos na sua mão principal.
3. Digite `/luma entregar`.
4. Consulte `/luma status` e peça a tarefa desejada.
5. Use `/luma devolver` para recuperar o que ela ainda carrega.

A Luma tem **27 espaços de mochila** e equipa **uma ferramenta ou arma na mão**. Ao entregar uma ferramenta nova, ela devolve a anterior. São aceitos machados, picaretas, espadas, pás e enxadas; isso não significa que ela execute todas as ações de um jogador com cada uma. O combate é corpo a corpo: não há uso de arco, escudo ou armadura. Ferramentas e armas gastam durabilidade.

Se sua mochila estiver cheia na devolução, o excesso cai no chão perto de você. O excesso dos itens coletados pela Luma também pode cair no chão. Se ela morrer, os itens carregados são soltos no mundo; volte ao local para recuperá-los.

### Minerar

Entregue um **machado para troncos** ou uma **picareta adequada para pedras**, e use `/luma minerar`. Cada ordem tenta coletar até **16 blocos**. Ela procura blocos expostos e acessíveis perto de vocês, na altura dos pés do dono ou acima: não cava até minérios nem desce abrindo túneis.

As pedras aceitas são pedra, pedregulho, granito, diorito, andesito, ardósia e pedregulho de ardósia. **Minérios não fazem parte desta versão.** Ela evita alguns riscos, como líquidos ao lado do alvo e areia ou cascalho acima. Isso não garante navegação perfeita: rios e outros obstáculos podem impedir a aproximação.

A Luma não distingue blocos naturais de blocos colocados por jogadores. **Não mande minerar perto de casas, plantações ou estruturas que queira preservar.** Ela também não oferece integração genérica com sistemas de proteção de terrenos de outros mods.

### Construir o abrigo

1. Prepare uma área plana, com chão firme e um espaço **5 × 5 completamente livre**, com **cinco blocos de altura vazios**. Retire capim, flores, neve e outros obstáculos. Deixe espaço aberto ao redor para ela caminhar.
2. Entregue pelo menos **96 blocos** de construção; por exemplo, uma pilha de 64 tábuas e outra com 32. Tábuas ou pedregulho são escolhas simples. Também são aceitos troncos, as pedras listadas acima, terra, terra infértil, tijolos e tijolos de pedra.
3. Fique próximo dessa área e use `/luma construir`. Ela procura posições próximas ao seu redor; se não encontrar, afaste-se de obstáculos, escolha um terreno mais aberto e tente novamente.

O abrigo tem **5 × 5 na parte externa**, piso, paredes com três blocos de altura e teto. A entrada tem um bloco de largura e dois de altura, fica aberta e **não inclui porta, cama, móveis ou tochas**. São 25 blocos de piso, 46 de paredes e 25 de teto: **96 no total**, consumidos da mochila da Luma.

Ela não substitui blocos já existentes. Se o espaço for ocupado ou ela não conseguir alcançar a construção, pode parar, deixando a parte já feita no mundo e os materiais restantes na mochila. Uma nova ordem pode procurar outro local; ela não promete concluir automaticamente um abrigo interrompido. Pedidos como “construa um castelo igual à minha casa” não criam projetos novos.

### Autonomia e distância

Use `/luma autonomo` para ela acompanhar, combater inimigos próximos e procurar madeira. Com um machado, ela tenta coletar **até oito troncos por ativação** desse modo; não mantém uma fazenda infinita nem joga uma campanha sozinha. Você pode ativar novamente para uma nova tarefa limitada.

Ela pausa quando o dono sai do jogo ou vai para outra dimensão, e não trabalha enquanto o jogo está fechado. Não atravessa dimensões automaticamente. O terreno pode impedir que ela siga você; use `/luma localizar` para consultar o último local conhecido e se aproxime para carregar a região. Depois, na mesma dimensão, tente `/luma chamar`.

Mineração e construção respeitam a regra do mundo **`minecraft:mob_griefing`**. Se ela estiver desativada, as tarefas que alteram blocos não funcionam. Em um mundo novo de teste com comandos ativados, você pode permitir essas tarefas com:

```text
/gamerule minecraft:mob_griefing true
```

Em servidor, a alteração dessa regra cabe ao administrador e também afeta outros comportamentos do Minecraft. A Luma continua podendo seguir e combater quando a regra impede alterações em blocos.

## 6. Ouvir e falar com a Luma

O painel de voz funciona no **mesmo computador de um mundo de um jogador**. Esta versão não oferece esse painel para quem entra em um servidor dedicado. Em multiplayer, use as mensagens e os comandos de texto.

1. Com o mundo aberto, use `/luma voz`.
2. Abra o link completo mostrado no chat no **Chrome ou Edge**. O endereço começa com `http://127.0.0.1:` e inclui uma porta e um código depois de `#token=`. A porta pode mudar a cada sessão.
3. Clique em **Ativar conexão e voz** no painel. Mantenha o jogo e a página abertos.
4. Deixe marcada a opção de ler as respostas em voz alta e escolha uma voz **pt-BR**, se estiver disponível.
5. Para falar, clique em **Falar pelo microfone** e permita o acesso ao microfone quando o navegador perguntar. Diga, por exemplo, “Luma, me siga”.
6. Você também pode escrever no campo de mensagem e clicar em **Enviar para a Luma**.
7. Para interromper a sessão, use o botão de pausa. Para desligar o serviço local, use `/luma voz parar` no jogo.

O microfone só começa a ser usado depois da sua ação no painel. O reconhecimento de fala pode enviar o áudio ao serviço do navegador e precisar de internet. A fala da Luma usa as vozes do sistema/navegador e também pode depender do fornecedor da voz. **Não criamos um modelo próprio de voz ou reconhecimento de áudio.** Não é uma dublagem gravada e não inclui clonagem de voz. A IA própria descrita abaixo gera somente o texto.

Se não aparecer uma voz brasileira, instale uma voz de português do Brasil nas configurações de idioma e fala do seu sistema. No Windows, procure por **Configurações → Hora e idioma → Fala**; os nomes variam conforme a versão do Windows. Depois reinicie o navegador e abra um novo link pelo jogo.

Não compartilhe o link do painel. O código do link autoriza o controle da Luma naquela sessão local. Depois de uma desconexão, obtenha um novo link com `/luma voz` em vez de reutilizar um favorito antigo. Se você atualizar a página e a conexão parar, abra novamente o link completo pelo jogo.

## 7. Experimentar a IA própria de texto — opcional

O projeto usa um gerador próprio, pesos treinados pelo próprio projeto e uma memória local de informações autorais. A memória procura uma nota relacionada à pergunta, e o modelo usa essa nota para gerar a fala. A busca pode escolher o assunto errado; não significa que a personagem saiba tudo sobre o jogo. O modelo e a memória já estão incluídos no JAR, sem arquivo extra ou configuração para você fazer.

Você não precisa instalar Ollama, baixar outro modelo, criar conta de IA, configurar uma API ou pagar por conversas. Minecraft, Java e Fabric continuam sendo necessários para jogar. A IA começa desligada em cada sessão.

1. Abra seu mundo de teste e crie a companheira com `/luma criar`, se ainda não a criou.
2. No chat do jogo, digite `/luma ia ligar` e leia o aviso de qualidade experimental.
3. Digite `/luma falar Como podemos começar nossa aventura?` e aguarde a resposta.
4. Para continuar, envie outra mensagem com `/luma falar` ou pelo painel de voz.
5. Para desligar o modelo, digite `/luma ia desligar`. Você ainda poderá usar `/luma seguir`, `/luma construir` e os demais comandos.

**A resposta pode ter palavras sem sentido, erros ou não responder à pergunta.** Este é um modelo muito pequeno, ainda distante de uma IA generativa comparável aos modelos grandes usados com Ollama. Não confie nas falas como instruções corretas sobre o jogo. Veja os resultados reais no [relatório do modelo](https://github.com/lucasmac412/Mods/blob/v0.3.0-experimental/native-ai/experiments-v2/RESULTADOS.md).

Perguntas como `/luma falar Não sei por onde começar` e `/luma falar Uso madeira ou pedra?` agora podem chegar ao modelo quando forem reconhecidas como conversa. Usar palavras como “não”, “ou” e “talvez” não bloqueia a conversa por si só. Isso não garante que a resposta esteja correta.

O modelo **gera falas**. As ações da Luma continuam sendo escolhidas pelas regras que reconhecem ordens e pelos comportamentos já programados. Uma ordem clara, como `/luma seguir` ou `/luma parar`, continua imediata; a fala gerada não executa tarefas. Ativar o modelo não acrescenta novas construções, receitas, ferramentas ou capacidade de jogar sozinha. Para uma tarefa, use uma ordem clara; para experimentar a conversa, use `/luma falar`.

A memória incluída contém informações do projeto; ela não registra automaticamente o que você conta, não aprende enquanto você joga e não dá ao modelo acesso à sua tela ou inventário.

A geração de texto funciona no próprio computador, sem enviar a conversa a um serviço de IA, e pode funcionar **offline** depois da instalação. Microfone e leitura em voz alta continuam usando Chrome/Edge e o sistema operacional, podendo precisar de internet conforme explicado no passo 6.

Este gerador usa o **processador (CPU), em Java**. Ele não usa sua RTX 4060 para a IA. Ter 16 GB de RAM não melhora por si só o conhecimento ou a qualidade do modelo; isso depende do treinamento e do tamanho dele. Não compre hardware nem serviços para esta versão. Se o jogo ficar lento ao conversar, desligue a IA com `/luma ia desligar` e continue usando os comandos.

## Gerar o JAR a partir deste projeto

Esta etapa é necessária se você recebeu apenas o código-fonte. Você não precisa editar Java, mas precisa executar um comando para transformar os arquivos em um mod. A primeira compilação precisa de internet para baixar o Gradle, o Minecraft e as dependências do Fabric.

1. Instale o **JDK 25** pelo [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25). Escolha JDK, versão 25, seu sistema e arquitetura. No Windows, habilite `PATH` e `JAVA_HOME` no instalador, se oferecidos.
2. Baixe o [código-fonte da versão 0.3.0-experimental](https://github.com/lucasmac412/Mods/archive/refs/tags/v0.3.0-experimental.zip), ou use a cópia do projeto que recebeu. Extraia o ZIP para uma pasta simples, como `Documentos\Luma`.
3. Abra a pasta que contém `gradlew.bat`, `gradlew` e `build.gradle`. Não abra uma pasta acima dela.
4. No Windows, clique na barra de endereço do Explorador de Arquivos, digite `powershell` e pressione Enter. No Linux/macOS, abra um terminal nessa pasta.
5. Confirme o Java:

   ```text
   java -version
   ```

   A versão exibida deve ser 25. Se não for, ajuste `JAVA_HOME` para a pasta do JDK 25 e o `PATH` para seu subdiretório `bin`, feche e reabra o terminal. `JAVA_HOME` aponta para a pasta do JDK, não para o arquivo `java.exe`.

6. No **Windows PowerShell**, execute:

   ```powershell
   .\gradlew.bat build
   ```

   No Prompt de Comando do Windows, use `gradlew.bat build`. No **Linux/macOS**, execute:

   ```sh
   chmod +x gradlew
   ./gradlew build
   ```

7. Aguarde o comando terminar. Só prossiga se aparecer **`BUILD SUCCESSFUL`**. O primeiro download pode levar vários minutos.
8. Abra a pasta `build/libs` e copie **`luma-companion-0.3.0-experimental.jar`** para a pasta `mods`, conforme o passo 4 deste guia. Deixe o arquivo `-sources.jar` fora de `mods`.

Se aparecer **`BUILD FAILED`**, a compilação não foi concluída. Não renomeie um ZIP para `.jar` e não use um arquivo de uma tentativa anterior como se tivesse passado. Guarde a primeira mensagem de erro para identificar a causa; consulte [TESTES.md](TESTES.md) para o estado conhecido desta versão.

## Se algo der errado

| Problema | O que conferir |
| --- | --- |
| Aparece “não foi possível baixar o artefato” no Codex | Use o link do ZIP no início deste guia, que aponta para o GitHub. No Chrome, confira o download com `Ctrl + J`. O ZIP de instalação é diferente do ZIP do código-fonte. |
| O jogo abre sem a Luma ou diz que `/luma` não existe | Selecione o perfil Fabric 26.2 e confira se o JAR está na pasta `mods` usada por esse perfil. |
| Fabric reclama de dependência | Instale Fabric API para **26.2** e confira Java 25, Loader e versão do jogo. |
| O jogo não abre depois da instalação | Retire temporariamente outros mods da instância de teste, confira versões e leia a primeira causa no relatório. Não teste em um mundo importante. |
| A Luma não minera | Entregue machado para troncos ou picareta adequada para pedras simples expostas, escolha um local acessível e confira `/luma status` e `minecraft:mob_griefing`. Ela não minera minérios. |
| A Luma não constrói | Entregue 96 blocos aceitos, libere uma área plana 5 × 5 com cinco blocos de altura vazios, retire até capim e confira `/luma status` e `minecraft:mob_griefing`. |
| A Luma ficou longe | Use `/luma localizar`, vá à dimensão e ao local indicados para carregar a região e depois tente `/luma chamar`. |
| O painel de voz não conecta | Mantenha o mundo de um jogador aberto no mesmo computador e obtenha um link novo com `/luma voz`. Abra o endereço completo. |
| Erro `jdk.httpserver` ou `com.sun.net.httpserver` | Instale o JDK 25 completo e selecione seu executável Java nas opções do perfil Fabric. |
| O botão de microfone não funciona | Use Chrome/Edge, confira a permissão de microfone, o dispositivo selecionado e a conexão exigida pelo reconhecimento. Você pode continuar por texto. |
| Há texto, mas não há som | Ative a conexão, marque a leitura em voz alta, confira o volume da aba/sistema e escolha ou instale uma voz pt-BR. |
| A IA responde sem sentido ou muda de assunto | A memória local pode escolher uma nota inadequada e o modelo experimental ainda pode errar ao gerar. Use `/luma ia desligar` para continuar com respostas básicas e comandos. Instalar outro programa ou comprar hardware não corrige o treinamento deste modelo. |
| A IA própria não inicia ou fica lenta | Confira se está usando o JAR `0.3.0-experimental` do pacote completo e leia a mensagem no chat. Use `/luma ia desligar` para continuar pelas ordens básicas e guarde o trecho relevante do log para relatar. |
| A compilação falha ao baixar arquivos | Confira a internet e os domínios indicados no erro. Não desative certificados, assinaturas ou verificações de segurança para contornar o problema. |

Para relatar um problema, informe as versões de Minecraft, Java, Fabric Loader e Fabric API; a ação realizada; o que aconteceu; e o trecho relevante de `logs/latest.log` na pasta do jogo. Remova links privados do painel de voz, chaves, tokens e dados pessoais antes de compartilhar.
