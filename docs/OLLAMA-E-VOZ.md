# Ollama e Luma Voz: configuração e uso

A versão **0.4.0-experimental** acrescenta duas partes separadas: o Ollama gera o texto da conversa e o complemento **Luma Voz** reconhece o microfone ao segurar uma tecla. O complemento também oferece leitura das respostas dentro do Minecraft. O [guia de instalação](INSTALAR.md) explica desde o download do pacote até a pasta do perfil.

| Parte | Função | Onde executa |
| --- | --- | --- |
| Luma | NPC, comandos, contexto do jogo e conexão com Ollama | Mundo de um jogador ou servidor com o mod |
| Ollama + modelo escolhido | Gerar respostas de conversa | Computador que executa o mundo/servidor |
| Luma Voz + modelo Vosk PT | Transcrever o microfone ao segurar V | Cliente Minecraft do jogador |
| Voz nativa do sistema | Ler respostas com prefixo `[Luma]` | Computador do jogador |

## Instalar as partes

1. Use Minecraft **Java Edition 26.2**, Fabric Loader **0.19.5+ compatível**, Fabric API **0.161.0+26.2** e Java **25**.
2. Copie os três JARs do pacote para `mods`: Luma, Luma Voz e Fabric API. Mova versões antigas desses mods para fora dessa pasta.
3. Copie `config/luma-voice` do pacote para `config` do perfil. Mescle pastas, preservando configurações existentes. O arquivo deve ficar em `config/luma-voice/model/final.mdl`; não crie uma subpasta extra com o nome do arquivo ZIP original.
4. Instale o Ollama pelo [site oficial](https://ollama.com/download), abra-o e mantenha o serviço em execução.
5. No PowerShell/terminal, execute `ollama pull qwen2.5:3b` e aguarde terminar.
6. Para testar o modelo sem Minecraft, execute `ollama run qwen2.5:3b`, envie uma pergunta em português e use `/bye` para sair da conversa do terminal. O aplicativo/serviço Ollama precisa continuar aberto.

Não precisa programar, compilar, configurar chave de API ou pagar por mensagem. O modelo de conversa do Ollama não acompanha o ZIP de instalação. Ollama, Qwen, Vosk e o modelo de reconhecimento são componentes de terceiros; os avisos e licenças de voz acompanham o pacote. Após os downloads, a geração do Ollama local e o reconhecimento Vosk podem funcionar sem internet.

## Conversa no jogo

Em um mundo de teste, digite no chat uma linha por vez:

```text
/luma criar
/luma ia ollama
/luma falar Como podemos começar nossa aventura?
```

Pule `/luma criar` se já tiver a companheira. A primeira geração pode demorar até dois minutos para carregar o modelo. A mensagem de seleção da IA confirma a configuração, não a disponibilidade do Ollama.

| Comando | Resultado |
| --- | --- |
| `/luma ia ollama` ou `/luma ia ligar` | Seleciona o Ollama nesta sessão. |
| `/luma ia modelo qwen2.5:3b` | Seleciona esse nome de modelo; baixe-o antes com `ollama pull`. Se já estiver usando Ollama, a mudança reinicia a conversa. |
| `/luma ia status` | Mostra seleção e modelo configurados, sem consultar o Ollama. |
| `/luma ia propria` | Troca para o pequeno modelo próprio experimental incluído no JAR. |
| `/luma ia desligar` | Cancela a geração, descarta histórico e mantém comandos/respostas locais. |
| `/luma parar` | Interrompe a tarefa da companheira e cancela a resposta pendente. |

O modelo recebe o estado descrito pela Luma, sua vida, dimensão/posição e até quatro turnos de conversa recentes. Esse resumo é uma fotografia do momento, sem visão da tela ou leitura completa do mundo. Não há aprendizado durante a partida. O histórico fica na memória da sessão e é descartado ao desligar, trocar provedor/modelo ou sair. As escolhas de IA/modelo também não persistem entre sessões.

A integração acessa somente o Ollama local em `http://127.0.0.1:11434/api/chat`. Não precisa editar URL ou variáveis `LUMA_AI_*`. Em servidor dedicado, é o **servidor** que faz a requisição ao Ollama da própria máquina; não é o Ollama instalado no computador de cada cliente. O administrador precisa instalar Luma e configurar Ollama no servidor. O complemento Luma Voz é apenas cliente.

A geração produz somente falas. Comandos simples, como seguir/parar, continuam na lógica do mod e não aguardam a IA. O modelo não executa comandos de servidor, não fabrica receitas e não acrescenta construções arbitrárias. Uma resposta dizendo “já fiz” não prova uma ação: confira o jogo e `/luma status`.

## Microfone por tecla

1. Configure seu microfone como dispositivo de entrada padrão do sistema.
2. No mundo, feche o chat e os menus.
3. Segure **V**, aguarde **“Pode falar agora”**, diga uma frase curta e solte para transcrever/enviar. Por exemplo, “me siga”, “fique aqui”, “parar” ou “o que podemos fazer agora?”. Não precisa dizer “Luma”.
4. Leia o texto reconhecido no chat para conferir o que chegou à companheira.

Na primeira tentativa, a tecla prepara o modelo de reconhecimento. Se você soltar antes de a gravação estar pronta, a frase não é enviada: aguarde e tente de novo. Há um limite de **15 segundos por gravação**. O microfone usa áudio PCM de 16 kHz, mono; alguns dispositivos podem não aceitar esse formato. Se não houver fala reconhecida, tente uma frase mais curta em local silencioso.

O complemento não transmite áudio ao Ollama ou a outros jogadores. A transcrição é enviada ao servidor como `/luma falar <texto>`. O servidor recebe esse texto como qualquer comando enviado ao mod; isso não é chat de voz por proximidade. Quando o reconhecimento errar, use `/luma parar` digitado para interromper uma tarefa com segurança.

Para mudar V ou K, abra **Opções → Controles → Teclas**, procure **Luma Voz** e escolha uma tecla sem conflito. O jogo precisa estar em foco e com o mundo aberto; o microfone não é um gravador permanente.

## Ouvir respostas dentro do Minecraft

**K** liga ou desliga a leitura das mensagens com prefixo `[Luma]`. A opção começa ligada e é salva em `config/luma-voice.json`, no campo `speakReplies`. Não é necessário ativar o narrador de todo o chat do Minecraft.

A fala usa o mecanismo nativo de voz disponível ao Minecraft, sem escolha de idioma/timbre pelo complemento:

- **Windows/macOS:** instale/configure a voz padrão do sistema em **português do Brasil**. O nome das opções varia por sistema; no Windows, procure **Hora e idioma → Fala**. A disponibilidade real depende das vozes e do mecanismo nativo instalados.
- **Linux:** a biblioteca nativa incluída usa **Flite em inglês**. Esta opção não oferece pronúncia pt-BR garantida. Para escolher uma voz pt-BR, o painel opcional no navegador pode ser uma alternativa.

Não é dublagem gravada ou clonagem de voz. O timbre não vem do Ollama nem do Vosk. Use fones de ouvido para reduzir eco; iniciar a gravação interrompe a fala atual. Microfone, reprodução audível e desempenho precisam ser testados no computador do jogador.

## Modelo menor e uso de memória

Com RTX 4060 e 16 GB de RAM, `qwen2.5:3b` é um ponto de partida, sem garantia de FPS ou velocidade junto com Minecraft. Se faltar memória ou ficar lento, feche aplicativos pesados ou experimente:

No PowerShell/terminal:

```text
ollama pull qwen2.5:1.5b
```

No chat do jogo:

```text
/luma ia modelo qwen2.5:1.5b
/luma ia ollama
```

O Ollama pode usar a GPU quando suportada. O pequeno modelo próprio de `/luma ia propria` continua usando CPU em Java. Não compre hardware ou serviços para esta versão; use `/luma ia desligar` para continuar só com comandos locais.

## Solução de problemas

| Sintoma | O que fazer |
| --- | --- |
| `ollama` não é reconhecido | Conclua a instalação, abra o aplicativo e feche/reabra o PowerShell. |
| Modelo não encontrado | Execute `ollama pull` seguido do nome exato mostrado em `/luma ia status`; aguarde o download. |
| Não conseguiu falar com Ollama | Mantenha o serviço aberto na máquina do mundo/servidor; teste `ollama run qwen2.5:3b`. |
| Primeira resposta demora | Aguarde o carregamento inicial; se passar do limite, tente novamente ou use um modelo menor. |
| V não grava | Confira mod cliente, tecla, mundo aberto sem menus, foco da janela e microfone padrão. |
| Modelo de voz ausente | Confira `config/luma-voice/model/final.mdl` no diretório do perfil Fabric usado. |
| Microfone bloqueado no Windows | Em **Privacidade e segurança → Microfone**, permita aplicativos de área de trabalho. Em **Sistema → Som → Entrada**, teste o dispositivo padrão. |
| Microfone falha mesmo permitido | Confira o formato suportado pelo dispositivo; esta versão usa PCM 16 kHz mono. Use texto como alternativa e guarde o erro do log. |
| Transcrição erra palavras | Fale curto, reduza ruído, use fones e confira a transcrição. Não há garantia de reconhecimento perfeito. |
| Não há voz | Use K para ligar a leitura, confira o volume e a voz do sistema. No Linux, veja a limitação Flite acima. |
| Duas vozes leem a resposta | Não use ao mesmo tempo a leitura de Luma Voz e a do painel `/luma voz`; desligue uma delas. |
| A Luma “promete” uma ação e não a realiza | A resposta do modelo só conversa. Use o comando real da tarefa e confira os recursos necessários. |

O modelo Vosk distribuído tem bibliotecas nativas para Windows/Linux x86_64 e macOS Intel/ARM64. Windows ARM e Linux ARM não são cobertos por esses binários; compilação em nuvem não confirma funcionamento do áudio em todas as plataformas. Consulte [TESTES.md](TESTES.md) para as verificações efetivamente concluídas.

## Painel do navegador como alternativa

Em um mundo de um jogador no mesmo computador, `/luma voz` fornece um link privado temporário. Abra-o no Chrome/Edge, clique em **Ativar conexão e voz**, escolha uma voz pt-BR e use texto ou o botão de microfone. O reconhecimento do navegador e algumas vozes podem usar serviços online e enviar dados ao fornecedor. Não compartilhe o link; `/luma voz parar` encerra o painel. Esta alternativa continua separada da tecla V do complemento.
