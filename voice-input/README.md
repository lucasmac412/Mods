# Luma — microfone dentro do Minecraft

Complemento cliente para **Minecraft Java 26.2, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.2 e Luma 0.4.0 experimental**, com Java 25. O JAR instalável é `build/libs/luma-voice-0.4.0-experimental.jar`; `-sources.jar` contém código e não deve ir para `mods`.

Instale o JAR na pasta `mods` do computador em que joga. Copie também a pasta `config` do pacote de instalação para a pasta desse perfil Minecraft. O modelo português deve ficar diretamente em `config/luma-voice/model/`, contendo `final.mdl`, `mfcc.conf`, `HCLr.fst`, `Gr.fst`, `phones.txt` e `ivector/`. Não coloque uma pasta adicional `vosk-model-small-pt-0.3` entre `model` e esses arquivos. O mod não baixa modelos automaticamente.

## Falar com a Luma

1. Entre em um mundo e crie sua companheira com `/luma criar`.
2. Se quiser conversa pelo Ollama, ative-a conforme o guia da Luma. Ordens simples não precisam de Ollama.
3. Com o jogo em foco e sem chat/menu aberto, **segure V**, aguarde o aviso **“Pode falar agora”**, fale uma ordem e **solte V** para enviar. A primeira tentativa prepara o modelo, o que pode levar alguns instantes; se você soltar antes de ele ficar pronto, tente novamente. O limite é de **15 segundos por fala**.
4. A transcrição aparece no chat com o prefixo `[Voz da Luma]`. O texto reconhecido é enviado pelo comando `luma falar`; o áudio não é enviado à Luma, ao Ollama ou a outro serviço.

Exemplo: segure V, aguarde o aviso, diga **“me siga”**, solte e confira a transcrição e a ação. Para parar, diga **“parar”** ou use `/luma parar` no chat. O nome “Luma” pode ser reconhecido como outra palavra; não é preciso dizê-lo. Se o reconhecimento entender outra coisa, use o comando escrito para interromper. A captura usa o microfone padrão do sistema; confira permissões de microfone do Windows/macOS/Linux. Alguns dispositivos não aceitam o formato PCM mono 16 kHz solicitado pela API Java Sound; nesse caso, use texto ou o painel no Chrome.

Você pode trocar V em **Opções → Controles → Atalhos de teclado → Luma — voz**. A gravação é cancelada ao abrir menus, perder foco, sair do servidor ou mudar de mundo; resultados antigos não devem virar ordens na próxima sessão. A tecla só começa uma nova fala depois de ser solta. Não há escuta contínua, gravação em arquivo, envio de áudio pela rede ou transmissão de voz para outros jogadores.

## Ouvir as respostas no próprio jogo

O complemento lê apenas mensagens do sistema que começam com `[Luma] `. **K** liga/desliga a leitura; o atalho também é reconfigurável. A preferência é salva em `config/luma-voice.json` como `{"speakReplies":true}` e começa ligada. Isso usa uma instância própria do mecanismo de voz já distribuído com Minecraft, sem ligar o narrador global do chat. Uma resposta nova pode interromper a anterior para evitar uma fila de falas atrasadas. Pressionar V interrompe a leitura e respostas que cheguem durante a captura/transcrição não são faladas, reduzindo retorno do alto-falante para o microfone. Fones de ouvido ajudam.

**Não é um modelo próprio de voz nem uma dublagem humana.** No Windows e macOS, o mecanismo usa uma voz padrão disponível no sistema; configurar uma voz padrão de português do Brasil pode ajudar, mas instalar a voz não garante que o mecanismo do Minecraft vá escolhê-la. A API do Minecraft não permite selecionar idioma ou timbre neste mod. **No Linux, o mecanismo distribuído com Minecraft usa a voz inglesa Flite Kal16**, então a pronúncia de texto português pode ser ruim. O painel no Chrome continua como alternativa para selecionar uma voz pt-BR do navegador/sistema; não use os dois leitores ao mesmo tempo. O reconhecimento Vosk em português é independente dessa limitação de saída.

## Tecnologia e plataformas

O reconhecimento usa **Vosk 0.3.45**, **JNA 5.17.0** e o modelo **vosk-model-small-pt-0.3**, todos de terceiros. Essas dependências são distintas do modelo autoral experimental de texto da Luma. As bibliotecas Vosk e JNA acompanham o JAR; o modelo de fala acompanha o pacote de instalação na pasta `config`. O reconhecimento pode funcionar offline depois da instalação.

Os binários nativos distribuídos no Vosk JAR incluem **Windows x64, Linux x64 e macOS Intel/Apple Silicon**. Linux ARM e Windows ARM não têm binário incluído neste pacote. A presença do binário não comprova compatibilidade com todo dispositivo ou versão do sistema.

As licenças e a origem das dependências ficam em `THIRD-PARTY-NOTICES.md`. O código do complemento é MIT. Ele não usa a RTX 4060: reconhecimento e captura são locais em CPU. Ollama é uma instalação separada e pode usar GPU conforme sua configuração.

## Compilar e verificar

Da raiz do repositório, com JDK 25:

```sh
./gradlew --no-daemon -p voice-input build
```

O projeto é independente do build principal. Os testes verificam limites de duração/tamanho, silêncio, liberação de recursos, cancelamento durante preparação/reconhecimento, rejeição de gravações sobrepostas e envio do texto como argumento literal da Luma. A compilação verifica as APIs cliente reais de Minecraft 26.2. **Não foram testados microfone físico, interface gráfica ou reprodução audível no computador do jogador.** A aprovação desses testes não demonstra precisão de reconhecimento ou qualidade da voz.
