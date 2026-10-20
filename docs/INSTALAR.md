# Instalar e usar a Luma 0.4.0-experimental, do zero

Este guia é para **Minecraft Java Edition 26.2 no computador**. Não serve para Minecraft de celular, console ou Bedrock Edition. Você precisará de uma conta com acesso ao Minecraft Java Edition.

**[Baixar Luma-Fabric-26.2-Ollama-e-Voz-v0.4.0-Experimental.zip](https://raw.githubusercontent.com/lucasmac412/Mods/v0.4.0-experimental/downloads/Luma-Fabric-26.2-Ollama-e-Voz-v0.4.0-Experimental.zip)**

No Chrome, abra o link acima e aguarde o download. Aperte `Ctrl + J` para encontrar o ZIP baixado e clique em **Mostrar na pasta**. Clique com o botão direito no ZIP e escolha **Extrair tudo**. O pacote contém Luma, Luma Voz, Fabric API, o modelo offline de reconhecimento de fala em português e este guia. Você não precisa programar, compilar ou treinar um modelo para instalar. O Ollama e o modelo de conversa são baixados separadamente no passo 7.

A conversa pode usar o **Ollama local**, e o complemento **Luma Voz** permite segurar uma tecla para falar dentro do Minecraft. O pequeno modelo próprio anterior continua disponível por uma opção separada. A IA começa desligada a cada sessão; as ordens e tarefas do NPC funcionam com ela desligada. Ollama, Qwen e Vosk são componentes de terceiros, conforme as respectivas licenças.

Um ZIP com pastas como `src` e `gradle` é o **código-fonte**. Se você recebeu apenas esse projeto, veja a seção **Gerar o JAR a partir deste projeto** no fim do guia.

## 1. Separe os arquivos

No pacote pronto há **três JARs**, que vão na pasta `mods` do jogo:

1. **Luma**: `luma-companion-0.4.0-experimental.jar`, a companheira e a integração com Ollama.
2. **Luma Voz**: `luma-voice-0.4.0-experimental.jar`, o complemento cliente para microfone e respostas faladas dentro do jogo.
3. **Fabric API**: `fabric-api-0.161.0+26.2.jar`, dependência compatível com Minecraft 26.2.

Há também a pasta **`config/luma-voice/model`**, com o modelo de reconhecimento de português. Ela vai na pasta `config` do jogo, **fora de `mods`**. O modelo de conversa do Ollama não está no ZIP; será baixado pelo próprio Ollama. Consulte [os testes e pendências](TESTES.md).

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

## 4. Copie os três JARs e o modelo de voz

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
3. Se já houver JARs antigos da Luma, Luma Voz ou Fabric API, mova os que serão substituídos para uma pasta de backup **fora de `mods`**. Não deixe duas versões do mesmo mod juntas.
4. Abra a pasta **mods do ZIP extraído** e copie os **três JARs** para a pasta **mods do jogo**. Não copie o ZIP inteiro.
5. Na pasta extraída, abra `config`. Copie a pasta `luma-voice` para a pasta `config` do jogo; crie `config` se ela não existir. **Mescle as pastas**, sem substituir a pasta `config` inteira nem apagar configurações de outros mods.
6. Confira o resultado: dentro da pasta de jogo deve existir `config/luma-voice/model/final.mdl`. Não deixe uma pasta extra `vosk-model-small-pt-0.3` entre `model` e os arquivos.
7. No Launcher, selecione o perfil **Fabric 26.2** e clique em **Jogar**.

Não coloque o projeto inteiro dentro de `mods` e não descompacte os JARs. O complemento Luma Voz é opcional para quem prefere só texto; para usar a tecla de microfone, instale-o e copie o modelo conforme acima. Não instale Luma Voz no servidor dedicado: ele funciona no cliente.

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
| `/luma ia ollama` ou `/luma ia ligar` | Seleciona o Ollama local nesta sessão. |
| `/luma ia modelo qwen2.5:3b` | Escolhe o modelo já instalado no Ollama; vale só para a sessão. |
| `/luma ia status` | Mostra o provedor e o modelo configurados, sem testar a conexão. |
| `/luma ia propria` | Seleciona o pequeno modelo próprio experimental incluído no mod. |
| `/luma ia desligar` | Desliga a conversa com o modelo e descarta o histórico; preserva ordens e respostas básicas. |

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

## 6. Fale pelo microfone dentro do Minecraft

Com o complemento Luma Voz e a pasta do modelo instalados:

1. Conecte o microfone e configure-o como dispositivo de entrada padrão do sistema.
2. Abra o mundo, crie a companheira com `/luma criar` e feche o chat e os menus.
3. **Segure V**, aguarde o aviso **“Pode falar agora”**, diga uma frase curta como “me siga” e **solte V** para transcrever e enviar. Você não precisa começar com “Luma”.
4. Confira no chat o texto reconhecido e a resposta da companheira. O reconhecimento pode errar; para interromper uma tarefa, use `/luma parar` pelo teclado se necessário.
5. Para alterar a tecla, abra **Opções → Controles → Teclas** e procure a categoria **Luma Voz**. Resolva conflitos se outro mod também usar V.

Na primeira tentativa, a tecla prepara o modelo; se não gravar ainda, aguarde e tente novamente. A gravação só acontece durante o acionamento da tecla, com limite de **15 segundos**. O Vosk reconhece a fala em português **no seu computador**, sem enviar a gravação ao Ollama. O texto reconhecido é enviado à Luma como `/luma falar ...`; em servidor, ele chega ao servidor que executa o mod. Isto não é um chat de voz por proximidade nem transmite sua voz aos outros jogadores.

**K** liga/desliga a leitura das respostas dentro do jogo e salva a preferência em `config/luma-voice.json`. A fala usa a voz padrão do sistema, sem escolha de idioma pelo complemento. No Windows/macOS, instale/configure uma voz de **português do Brasil**; no Linux, a biblioteca nativa usa Flite em inglês e não garante pronúncia pt-BR; não há dublagem humana gravada nem clonagem de voz. Veja os controles de fala e detalhes do sistema em [OLLAMA-E-VOZ.md](OLLAMA-E-VOZ.md). Microfone real, áudio e desempenho precisam ser conferidos no seu computador.

No Windows, se não gravar, procure **Configurações → Privacidade e segurança → Microfone** e permita o acesso para aplicativos de área de trabalho; em **Sistema → Som → Entrada**, selecione e teste o dispositivo padrão. Os nomes variam entre versões do Windows. Use fones de ouvido para reduzir a chance de a Luma ouvir a própria voz.

### Alternativa pelo Chrome/Edge

O painel antigo continua disponível em um mundo **de um jogador, no mesmo computador**. Use `/luma voz`, abra o link completo no Chrome/Edge e clique em **Ativar conexão e voz**. Você pode digitar no painel ou clicar no botão de microfone. Escolha uma voz pt-BR disponível e mantenha a página e o jogo abertos.

O reconhecimento do navegador pode enviar áudio ao fornecedor dele e precisar de internet. Algumas vozes também usam serviços online. Não compartilhe o link privado; para encerrar, use `/luma voz parar`. Não use a leitura do painel e do complemento ao mesmo tempo, para evitar duas vozes lendo a mesma resposta. O painel não é oferecido a clientes de servidor dedicado.

## 7. Instale o Ollama e ative a conversa

O Ollama executa um modelo de linguagem no computador que hospeda o mundo. Para seu mundo de um jogador, é o mesmo computador em que você joga. **Instalar o Ollama sozinho não basta: também é necessário baixar um modelo.**

1. Acesse [ollama.com/download](https://ollama.com/download), baixe o instalador do seu sistema e conclua a instalação.
2. Abra o Ollama e mantenha o aplicativo/serviço em execução.
3. No Windows, abra o menu Iniciar, procure **PowerShell** e abra-o. No Linux/macOS, abra o terminal.
4. Execute:

   ```text
   ollama pull qwen2.5:3b
   ```

   Aguarde o download terminar. Precisa de internet para baixar; não exige chave de API ou assinatura. Se o comando não for reconhecido, feche/reabra o PowerShell após instalar.

5. Para testar fora do jogo, execute `ollama run qwen2.5:3b` e escreva uma pergunta em português. Para sair dessa conversa, digite `/bye`. Deixe o aplicativo/serviço Ollama aberto.
6. No mundo de teste, digite uma linha por vez:

   ```text
   /luma criar
   /luma ia ollama
   /luma falar Como podemos começar nossa aventura?
   ```

   Se a Luma já existir, pule a primeira linha. `/luma ia ligar` também seleciona o Ollama.

7. Aguarde a resposta; o primeiro carregamento pode levar até dois minutos. Depois, você também pode segurar V para conversar por voz. Ordens claras, como “me siga” e “parar”, continuam locais e imediatas, mesmo sem o Ollama.

O mod usa `http://127.0.0.1:11434/api/chat`. Não é necessário editar endereço, abrir porta na internet ou configurar `LUMA_AI_*`. Em servidor dedicado, o Ollama deve estar na máquina do **servidor**; instalá-lo só no seu computador cliente não fornece IA ao servidor. Combine a configuração com o administrador.

A Luma fornece ao modelo um resumo do próprio estado, vida/dimensão/posição do jogador e até quatro turnos recentes. O histórico é temporário e é descartado ao desligar, trocar o provedor/modelo ou sair. As escolhas de IA/modelo também valem só para a sessão: ative novamente quando entrar. `/luma ia status` mostra a seleção, **sem confirmar se o Ollama está funcionando**.

A fala do modelo não executa comandos nem cria habilidades novas. Ele pode orientar a conversa, mas as tarefas continuam limitadas às capacidades documentadas: seguir, esperar, combater, minerar alguns blocos e construir o abrigo fixo. Não confie numa frase dizendo “já fiz” como prova de execução; confira o jogo e `/luma status`.

### Se seu computador ficar lento

Sua RTX 4060 com 16 GB de RAM pode experimentar um modelo pequeno, mas Minecraft e IA competem por memória e processamento. O Ollama pode usar hardware compatível; não garantimos FPS ou velocidade. Se precisar, baixe a opção menor no PowerShell:

```text
ollama pull qwen2.5:1.5b
```

No jogo:

```text
/luma ia modelo qwen2.5:1.5b
/luma ia ollama
```

Para interromper uma resposta pendente e uma tarefa, use `/luma parar`. Para desligar a IA, use `/luma ia desligar`; o Ollama continua sendo um aplicativo separado. Não compre hardware ou serviços para experimentar esta versão.

### Usar o experimento próprio anterior

`/luma ia propria` seleciona o modelo próprio de 277.504 parâmetros e a memória local incluídos no JAR, sem Ollama. Ele continua pequeno e pouco confiável: teve 11/40 respostas úteis num teste sintético avaliado por Codex, sem revisão humana independente. Esse resultado é do **modelo próprio**, não do modelo Qwen/Ollama. Ele usa CPU, não aprende durante a partida e não guarda conversas. Consulte [o relatório histórico](https://github.com/lucasmac412/Mods/blob/v0.3.0-experimental/native-ai/experiments-v2/RESULTADOS.md).

## Gerar o JAR a partir deste projeto

Esta etapa é necessária se você recebeu apenas o código-fonte. Você não precisa editar Java, mas precisa executar um comando para transformar os arquivos em um mod. A primeira compilação precisa de internet para baixar o Gradle, o Minecraft e as dependências do Fabric.

1. Instale o **JDK 25** pelo [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25). Escolha JDK, versão 25, seu sistema e arquitetura. No Windows, habilite `PATH` e `JAVA_HOME` no instalador, se oferecidos.
2. Baixe o [código-fonte da versão 0.4.0-experimental](https://github.com/lucasmac412/Mods/archive/refs/tags/v0.4.0-experimental.zip), ou use a cópia do projeto que recebeu. Extraia o ZIP para uma pasta simples, como `Documentos\Luma`.
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
8. Abra a pasta `build/libs` e copie **`luma-companion-0.4.0-experimental.jar`** para a pasta `mods`, conforme o passo 4 deste guia. Para compilar o complemento, execute também `./gradlew -p voice-input build` no Linux/macOS ou `.\gradlew.bat -p voice-input build` no PowerShell; o JAR estará em `voice-input/build/libs`. Deixe arquivos `-sources.jar` fora de `mods`. O modelo de reconhecimento de fala é um dado separado; o pacote pronto já inclui a pasta correta, e a origem está documentada em `voice-input/README.md`.

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
| V não inicia a gravação | Confira se Luma Voz está em `mods`, se há um mundo aberto sem chat/menu, se a tecla não está em conflito e se o sistema permitiu o microfone para aplicativos de área de trabalho. |
| Aparece que falta o modelo de voz | Confira `config/luma-voice/model/final.mdl` dentro da pasta do perfil usado. Copie a pasta de configuração do pacote, sem criar um nível extra de diretório. |
| O texto reconhecido está errado | Confira o microfone padrão, reduza ruído, fale uma ordem curta em português e solte V. Use `/luma parar` digitado para interromper se o reconhecimento falhar. |
| Há resposta no chat, mas não há voz no jogo | Confira a opção de leitura do complemento, volume e disponibilidade da voz do sistema. Instale/configure uma voz pt-BR; veja OLLAMA-E-VOZ.md. |
| O microfone do painel não funciona | No Chrome/Edge, confira a permissão e o serviço de reconhecimento do navegador; isso é separado da tecla V do complemento. |
| A Luma diz que não conseguiu falar com Ollama | Abra o Ollama no computador que hospeda o mundo/servidor. Teste `ollama run qwen2.5:3b` no terminal; confira o modelo selecionado com `/luma ia status`. |
| O modelo não está instalado | No PowerShell/terminal execute `ollama pull` seguido do mesmo nome escolhido em `/luma ia modelo`, aguarde terminar e tente novamente. |
| O Ollama demora ou o jogo perde desempenho | Aguarde o primeiro carregamento, feche aplicativos pesados ou experimente `qwen2.5:1.5b`. Você pode usar `/luma ia desligar` e continuar com ordens locais. |
| A IA diz que fez algo que não aconteceu | Texto gerado pode errar. Confira o mundo e use os comandos reais; a conversa não executa ações arbitrárias. |
| A IA própria responde sem sentido | O pequeno modelo próprio continua experimental. Use `/luma ia ollama` para a integração nova, ou `/luma ia desligar` para respostas básicas. |
| A compilação falha ao baixar arquivos | Confira a internet e os domínios indicados no erro. Não desative certificados, assinaturas ou verificações de segurança para contornar o problema. |

Para relatar um problema, informe as versões de Minecraft, Java, Fabric Loader e Fabric API; a ação realizada; o que aconteceu; e o trecho relevante de `logs/latest.log` na pasta do jogo. Remova links privados do painel de voz, chaves, tokens e dados pessoais antes de compartilhar.
