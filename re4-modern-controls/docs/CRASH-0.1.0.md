# Crash ao iniciar caminhada: correção 0.1.1

O relato foi de fechamento imediato ao começar a andar. O log fornecido identifica RE4 Steam UHD 1.1.0, confirma a ativação do controlador 0.1.0 e chega à captura de mouse. Ele não contém exceção, endereço da falha ou stack trace. Esses dados não permitem afirmar que existe uma única causa para o fechamento observado.

A revisão encontrou uma falha reproduzível nos callbacks de prólogo `TurnHookWalk` e `TurnHookRun`, incluídos na base que o protótipo compilava. Eles substituíam `mov ebp, esp; sub esp, 8`, depois do `push ebp`, por um `MakeInline`. O callback atribuía a EBP o **conteúdo** de ESP (o EBP salvo do chamador), alterava esse valor salvo e não alocava os oito bytes locais. O `popad` do injector não restaura ESP a partir do campo do `reg_pack`.

Consequências medidas em x86: EBP passa a apontar para o quadro do chamador; ESP não reserva os locais; o EBP salvo é alterado; `[ebp+8]` lê outro argumento. A função nativa passa a usar um ponteiro incorreto e seu retorno também fica comprometido. Isso é compatível com o gatilho informado pelo usuário.

A 0.1.1 deixa os prólogos originais de Walk/Run intactos. Esses dois hooks de MouseTurning são dispensáveis no controlador atual, que já desativa essa opção e trata a orientação no dispatcher. Não foi necessário inventar uma nova reconstrução de pilha.

`tests/native_prologue_test.cpp` executa os dois callbacks antigos, extraídos da revisão fixada do upstream, com o **injector real** e probes x86 independentes do jogo. O probe mede a falha e recupera seu próprio frame por variáveis externas para não executar o epílogo corrompido. Em seguida, restaura os bytes nativos e confirma 64 chamadas consecutivas corretas para cada caso. Os callbacks defeituosos são incluídos somente no executável de regressão, nunca na DLL do jogo.

O mesmo executável valida o novo diagnóstico local: uma exceção tratada por SEH continua sendo tratada normalmente, enquanto o arquivo registra código, EIP/ESP/EBP e etapa. As primeiras chamadas e retornos de cada ação são registrados com limite; o arquivo é truncado a cada inicialização. Não é um minidump nem coleta de entradas de texto.

A [compilação Windows 39740706200](https://github.com/lucasmac412/Mods/actions/runs/39740706200) passou: preparação, controlador, regressões de prólogo/diagnóstico, DLL Win32 e empacotamento. A DLL publicada é a mesma que saiu desse build; o guia em português foi atualizado após a conclusão. A origem e os checksums estão em `../reports/v0.1.1-windows-build.json`.

Etapas do diagnóstico: `1` coleta de contexto/entrada; `2` chamada nativa em andamento; `3` chamada nativa retornou; `4` ajuste de entrada/câmera. Ações: `0` nativa, `1` parado, `2` caminhada, `3` corrida, `4` deslocamento durante mira. Exceções de primeira chance podem ser tratadas por outras rotinas e não são, por si, prova de crash.

Essa regressão verifica a falha de ABI/pilha, além da compilação. Ela **não** executa RE4 nem comprova colisões, animações ou estabilidade na instalação real do usuário. A confirmação do fechamento relatado exige repetir o movimento no jogo com a nova DLL e, se necessário, analisar os novos logs. A 0.1.0 permanece arquivada para rastreabilidade, mas não deve ser instalada.
