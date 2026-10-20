# Luma: laboratório de IA generativa própria

Este diretório contém um protótipo de modelo de texto com código de treinamento e execução escrito em Java para o projeto Luma. O motor usa apenas a biblioteca padrão do Java: não chama Ollama, não consulta uma API de IA e não carrega pesos pré-treinados de terceiros. O JUnit é usado apenas para testar o código.

O treinamento começa com pesos aleatórios. Um tokenizador BPE aprende a dividir o texto dos exemplos de treino, e um Transformer causal aprende a prever os próximos tokens. A geração neural usa esses pesos aprendidos. O laboratório também oferece uma memória lexical opcional: ela recupera uma nota de treino para acrescentar ao prompt, e não deve ser apresentada como uma resposta gerada pela rede.

**Isso ainda não entrega qualidade comparável a modelos grandes.** O primeiro experimento usa 200 exemplos de treino, 24 de validação e 24 de teste. A segunda experiência usa 680 pares autorais de base,2.040 formas de entrada e um currículo de 8.599 contextos para aprender a usar a nota. As recombinações não acrescentam conhecimento. A comparação está em [experiments-v2](experiments-v2/RESULTADOS.md). O modelo pode misturar palavras, repetir texto, responder fora do assunto ou não produzir uma resposta útil. Leia [a origem dos dados iniciais](docs/PROVENANCE.md), [a origem do novo corpus](data-v2/PROVENANCE.md) e [os limites do experimento](docs/LIMITES.md).

## Relação com o mod

Este é o laboratório de treinamento usado pelo mod Luma. O mod inclui o checkpoint próprio de `mod-resources` e oferece `/luma ia ligar` para experimentá-lo. Os [resultados do primeiro modelo, incluído na versão 0.2.0](experiments/RESULTADOS.md), mostram que ele produzia principalmente texto sem sentido. Novas ferramentas ou mais dados não comprovam uma melhoria: ela precisa ser medida nas respostas. Compilar ou treinar somente este diretório não atualiza o mod instalado. O JAR deste laboratório é um programa de terminal e não deve ser colocado na pasta `mods` do Minecraft.

Este programa de terminal trabalha com texto. Ele não cria reconhecimento de microfone ou síntese de voz próprios, não observa o mundo automaticamente e não transforma a saída do modelo em comandos do jogo. A integração com o NPC precisa de sua própria validação no mod.

## O que você precisa

- **JDK 25**, com os comandos `java` e `javac` disponíveis no terminal. O JDK inclui as ferramentas necessárias para compilar; não basta ter apenas um Java antigo instalado.
- Uma cópia do repositório, incluindo `gradlew`, `gradlew.bat` e a pasta `native-ai`.
- Internet para o primeiro build baixar o Gradle e as dependências de teste. Treinar e gerar texto com os arquivos locais não usa um serviço de IA online.

O backend atual é **CPU**, com matemática em uma thread. **A RTX 4060 não é usada por este código**, mesmo que esteja instalada. Não há backend CUDA, treinamento em GPU nem contratação de nuvem. Aumentar os parâmetros não ativa a placa de vídeo.

Os comandos abaixo são executados na **pasta principal do repositório**, onde estão `gradlew.bat`, `README.md` e `native-ai`, e não dentro da pasta `native-ai`.

## 1. Compilar e testar

No Windows, abra uma nova janela do PowerShell nessa pasta e confira o Java:

```powershell
java -version
```

A versão deve ser 25. Se você acabou de instalar o JDK, habilite as opções de `PATH` e `JAVA_HOME` no instalador e abra um terminal novo.

Para compilar e executar os testes:

```powershell
.\gradlew.bat -p native-ai build
```

No Linux/macOS, o comando equivalente é:

```sh
./gradlew -p native-ai build
```

No ambiente de nuvem já preparado para este repositório, ative primeiro o JDK e o cache que foram instalados:

```sh
export JAVA_HOME=/workspace/.tools/jdk-25.0.4.1+1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.cache/gradle
./gradlew -p native-ai build
```

Espere o comando terminar com `BUILD SUCCESSFUL`. O programa será gerado em:

```text
native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar
```

Os relatórios de teste ficam em `native-ai/build/reports/tests/test/index.html` e `native-ai/build/test-results/test`. Os testes verificam, entre outras coisas, gradientes, causalidade, aprendizagem de uma sequência pequena, tokenização, checkpoints e amostragem. Esses testes não medem a qualidade de conversa de um modelo grande.

## 2. Treinar o primeiro modelo

O mesmo comando `java -jar` pode ser usado no PowerShell e no terminal Linux/macOS. Copie a linha inteira:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar train native-ai/data native-ai/runs/piloto --steps 1000 --seconds 180 --context 128 --dim 32 --heads 4 --layers 1 --ff 64 --vocab 384 --batch 2 --seed 412 --lr 0.002
```

Esse é um experimento pequeno. Ele pede até 1.000 atualizações e pode encerrar ao alcançar o orçamento de 180 segundos do laço de treino. Carregamento, avaliações e gravações também levam tempo; esse valor não é um prazo exato para o comando inteiro.

Os três arquivos de dados precisam estar na pasta informada: `train.tsv`, `validation.tsv` e `test.tsv`. Cada linha contém uma pergunta, uma tabulação e uma resposta. O vocabulário é aprendido somente com `train.tsv`. A escolha do checkpoint usa a validação; o teste não é pontuado durante esse comando.

Você receberá estes arquivos em `native-ai/runs/piloto`:

- `model.luma`: checkpoint de menor erro de validação entre os pontos avaliados, com tokenizador, configuração, pesos e hash do treino.
- `last.luma`: último estado do treinamento, mesmo quando seu erro de validação é maior.
- `step-002000.luma`, por exemplo: estados intermediários, quando você habilita `--save-every 2000`.
- `history.jsonl`: registros dos passos e da avaliação de validação.
- `training.json`: configuração, semente, passos realizados e métricas iniciais, do menor erro e do último estado.

Os arquivos `.luma` têm verificação de integridade SHA-256. Contêm pesos, mas não os estados internos do otimizador. Sem `--init`, rodar `train` novamente inicia outro treinamento do zero e pode substituir os arquivos; troque `piloto` por outro nome para guardar experimentos separados.

Para refinar um checkpoint próprio, use `--init <caminho.luma>`. O programa herda pesos, tokenizador e arquitetura e cria um AdamW novo; não restaura momentos. Flags de arquitetura fornecidas precisam concordar com o checkpoint. O manifesto registra hashes da origem e do novo corpus. Este projeto refinou somente pesos que ele próprio treinou do zero; não baixou pesos de terceiros.

`model.luma` é o menor erro medido, não uma garantia de melhor conversa. Para comparar fluência e pertinência, gere as respostas completas da validação também com `last.luma` e os snapshots. Defina os critérios antes da comparação e escolha antes de abrir o teste. Preserve saídas ruins no relatório.

### Controles do treinamento

| Opção | Padrão | Efeito |
| --- | --- | --- |
| `--schedule constant` ou `--schedule cosine` | `constant` | Depois do warmup, mantém a taxa ou reduz por uma curva de cosseno até 10% da taxa inicial no último passo solicitado. |
| `--warmup 400` | `0` | Aumenta a taxa gradualmente nos primeiros 400 passos. Com `cosine`, o warmup deve ser menor que `--steps`. |
| `--save-every 2000` | `0` | Salva um snapshot a cada 2.000 passos. Zero desliga snapshots periódicos, mas mantém `model.luma` e `last.luma`. |
| `--validation-every 500` | `100` | Mede a validação nesse intervalo, além do primeiro passo, do último e da parada por tempo. |
| `--objective response` ou `--objective full` | `response` | `response` aprende a prever resposta/EOS; `full` também pontua os tokens do prompt durante o treinamento. |

As métricas de comparação continuam pontuando somente a resposta/EOS, inclusive quando o objetivo de treino é `full`. A agenda não reinicia os momentos do AdamW. Se o limite de tempo interromper um treino com cosseno, a taxa pode parar antes de alcançar 10%, pois a curva usa o total de passos solicitado.

As pastas de saída `native-ai/runs`, `native-ai/models` e `native-ai/build` são ignoradas pelo Git. Elas não aparecem em um checkout novo apenas porque o código foi publicado. O modelo empacotado em `mod-resources` é uma exportação explícita, separada desses experimentos locais.

## 3. Preparar e treinar com o corpus v2

O script de preparação precisa de Python 3 apenas para reconstruir os arquivos de dados. Isso não é uma dependência de execução do mod ou do gerador Java. Na raiz do repositório, execute:

```sh
python3 native-ai/prepare-data-v2.py
```

No Windows com Python instalado, o equivalente pode ser `py -3 native-ai/prepare-data-v2.py`.

O script combina os 300 pares de `data-v2/base.tsv`, os 180 pares de `data-v2/conversation.tsv` e os 200 pares do treino antigo. Na preparação registrada, são **680 pares originais e 2.040 com as formas de entrada** `Luma, ...` e `Me ajuda: ...`. As variações reutilizam a resposta original: aumentam formas de chamar a personagem, **não acrescentam conhecimento**. Todos esses textos foram escritos sinteticamente por Codex; não são conversas humanas coletadas nem pesos pré-treinados.

O preparo elimina perguntas normalizadas duplicadas e colisões exatas com perguntas reservadas. As respostas de validação/teste não entram nas linhas de treinamento nem no tokenizador. `data-v2/manifest.json` registra contagens, exclusões, origem e hashes. Se você mudar os arquivos-fonte, confira esse manifesto em vez de presumir que os números continuam iguais.

Um exemplo de treino com os novos controles é:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar train native-ai/data-v2 native-ai/runs/v2-response --steps 16000 --seconds 420 --context 128 --dim 64 --heads 4 --layers 1 --ff 128 --vocab 1536 --batch 4 --seed 412 --lr 0.0015 --warmup 400 --schedule cosine --save-every 2000 --validation-every 500 --objective response
```

Troque o nome da pasta de saída para comparar outro objetivo ou configuração. Essa linha demonstra as opções; não promete bom resultado nem velocidade no computador do jogador. O backend continua sendo CPU em Java, sem usar a RTX 4060 ou contratar recursos pagos.

## 4. Avaliar sem escolher só as melhores respostas

Use a validação enquanto escolhe arquitetura, checkpoint e forma de geração:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar evaluate native-ai/runs/v2-response/last.luma native-ai/data-v2 native-ai/runs/v2-response/validation.jsonl --split validation --tokens 64 --temperature 0 --top-k 20 --seed 2026
```

Depois de congelar essas escolhas, execute o teste uma vez. Para o exemplo inicial:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar evaluate native-ai/runs/piloto/model.luma native-ai/data native-ai/runs/piloto/evaluation.jsonl --split test --tokens 48 --temperature 0.7 --top-k 20 --seed 2026
```

`--split validation` lê `validation.tsv`; `--split test` lê `test.tsv`. O padrão é `test`, por isso escreva a opção explicitamente durante o desenvolvimento. O comando registra as métricas e uma geração para **cada pergunta da divisão escolhida**, na ordem do arquivo, junto com a resposta de referência. Ele também confere se o arquivo de treino corresponde ao hash gravado no checkpoint. Use o mesmo diretório de dados que produziu esse modelo. As saídas ruins devem continuar no relatório.

O arquivo `data/evaluation-prompts.tsv` é um roteiro adicional com dez perguntas sem resposta de referência. Para registrar todas elas, execute:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar samples native-ai/runs/piloto/model.luma native-ai/data/evaluation-prompts.tsv native-ai/runs/piloto/extra.jsonl --tokens 48 --temperature 0.7 --top-k 20 --seed 2026
```

Cada linha desse TSV contém um identificador e uma pergunta separados por tabulação. O comando preserva a ordem e todas as saídas, sem filtrar resultados ruins.

Uma perda ou perplexidade menor mostra melhoria nessa medida, nesses dados e com essa tokenização. **CE e perplexidade por token não são diretamente comparáveis entre vocabulários/tokenizações diferentes.** Para essa comparação, use um denominador comum, como NLL por byte da mesma resposta de referência, e analise as gerações completas. Isso não comprova compreensão, conhecimento amplo, conversa coerente ou segurança. Os relatórios também indicam quantos exemplos precisaram ser truncados para caber no contexto. Se você ajustar o modelo depois de analisar o teste, registre isso e prepare um teste independente para a próxima avaliação.

## 5. Preparar notas locais para a geração neural

`LocalMemory` é uma busca lexical própria com TF-IDF/cosseno em palavras e partes de palavras. Ela constrói o índice **somente com o treino** e pode recuperar a resposta de um exemplo de treino como uma nota. Essa busca não é um modelo de linguagem nem uma memória de conversas anteriores.

Para criar uma cópia dos dados com notas no prompt:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar prepare-grounded native-ai/data-v2 native-ai/runs/data-v2-grounded --threshold 0.25
```

O limiar `--threshold` deve estar entre 0 e 1. Uma similaridade abaixo dele produz uma nota de informação insuficiente. `--answer-weight`, também entre 0 e 1, inclui termos das respostas do próprio índice de treino na busca; o padrão é 0. Os parâmetros podem ser escolhidos com a validação; não use o teste para ajustá-los. A entrada e a saída precisam ser pastas diferentes. O mod 0.3 usa limiar 0,15 e peso 0,7, registrados em `experiments-v2/selection.json`.

O comando grava `train.tsv`, `validation.tsv` e `test.tsv` preparados, `knowledge.tsv` com o treino original usado no índice e `grounding.json` com os parâmetros e hashes. As respostas reservadas são mantidas somente como alvos de avaliação, **nunca inseridas no índice ou nas notas**. A estrutura do prompt é:

```text
Pergunta: <fala do jogador> | Informação: <nota recuperada do treino>
```

Treine um modelo separado com essa pasta preparada; por exemplo:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar train native-ai/runs/data-v2-grounded native-ai/runs/v2-grounded --steps 12000 --seconds 420 --context 192 --dim 64 --heads 4 --layers 2 --ff 128 --vocab 1536 --batch 2 --seed 412 --lr 0.0015 --warmup 300 --schedule cosine --save-every 2000 --validation-every 500 --objective response
```

Ao avaliar esse modelo, informe a mesma pasta preparada e use `--split validation` durante a escolha. Na execução, o chamador precisa aplicar a mesma recuperação e formatação antes de chamar `TextGenerator`; `chat` não acrescenta notas automaticamente.

**A nota não deve ser copiada diretamente pelo código e chamada de geração.** A resposta neural continua sendo produzida token a token pelos logits do Transformer, sem uma lista de respostas permitidas. A nota pode estar errada para a pergunta e a rede pode ignorá-la, distorcê-la ou reproduzi-la. É necessário avaliar recuperação e geração separadamente e declarar quando o sistema usa conhecimento externo aos pesos. A comparação final registrou 11/40 respostas úteis, contra zero da versão anterior, pela avaliação Codex em um teste pequeno e sintético; 22/40 saídas reproduziram literalmente a nota. Isso não demonstra qualidade de modelo grande. O [relatório](experiments-v2/RESULTADOS.md) contém falhas, limites e comandos para reproduzir a configuração selecionada.

O refinamento usa `python3 native-ai/prepare-copy-curriculum.py` para preparar 8.599 exemplos com notas e perguntas somente do treino. Começa no nosso próprio checkpoint com `--init native-ai/runs/v2-grounded/last.luma`, LR 0,0005, warmup 100, cosine, batch 2, 10.000 passos e snapshots a cada 1.000. Preserva pesos/tokenizador/configuração, com momentos AdamW novos. Recombinar notas não acrescenta conhecimento factual; o currículo ensina principalmente a produzir texto a partir da nota fornecida. Consulte os manifestos e históricos antes de repetir o experimento.

## 6. Gerar uma resposta no terminal

Depois de treinar, experimente:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar chat native-ai/runs/piloto/model.luma "O que podemos fazer hoje?"
```

O programa acrescenta os rótulos de jogador/Luma, gera tokens e mostra o texto produzido. Esse comando é um exemplo de uso; não promete uma resposta correta ou legível. Cada chamada começa com a pergunta fornecida, sem memória automática de conversas anteriores.

Para consultar os comandos disponíveis:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar help
```

## O próximo passo para um modelo maior

Este laboratório permite verificar o caminho completo de dados autorais a pesos próprios e geração. Para uma IA de conversa muito mais capaz, ainda são necessários dados diversos com origem documentada, avaliações independentes, um backend de treinamento adequado e medições no hardware disponível. Mais passos sobre as mesmas poucas frases não substituem esses requisitos.

A licença do projeto está em [LICENSE](../LICENSE). Ao compartilhar um pacote separado deste laboratório, inclua também essa licença e a documentação de origem dos dados.
