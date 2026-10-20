# Luma: laboratório de IA generativa própria

Este diretório contém um protótipo de modelo de texto com código de treinamento e execução escrito em Java para o projeto Luma. O motor usa apenas a biblioteca padrão do Java: não chama Ollama, não consulta uma API de IA e não carrega pesos pré-treinados de terceiros. O JUnit é usado apenas para testar o código.

O treinamento começa com pesos aleatórios. Um tokenizador BPE aprende a dividir o texto dos exemplos de treino, e um Transformer causal aprende a prever os próximos tokens das respostas. A geração usa esses pesos aprendidos; não escolhe respostas de uma lista de frases prontas.

**Isso ainda não entrega qualidade comparável a modelos grandes.** Os dados disponíveis são um microcorpus sintético escrito por Codex para este projeto: 200 exemplos de treino, 24 de validação e 24 de teste. O modelo pode misturar palavras, repetir texto, responder fora do assunto ou não produzir uma resposta útil. Leia [a origem dos dados](docs/PROVENANCE.md) e [os limites do experimento](docs/LIMITES.md).

## Relação com o mod

Este é o laboratório de treinamento usado pelo **mod Luma 0.2.0-experimental**. O mod inclui o checkpoint próprio de `mod-resources` e oferece `/luma ia ligar` para experimentá-lo. Os [resultados registrados](experiments/RESULTADOS.md) mostram que ele ainda produz principalmente texto sem sentido. Compilar ou treinar somente este diretório não atualiza o mod instalado. O JAR deste laboratório é um programa de terminal e não deve ser colocado na pasta `mods` do Minecraft.

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

- `model.luma`: tokenizador, configuração, pesos aprendidos e hash do arquivo de treino, com verificação de integridade SHA-256.
- `history.jsonl`: registros dos passos e da avaliação de validação.
- `training.json`: configuração, semente, passos realizados e métricas antes e depois do treino.

O checkpoint salvo é o que teve a menor perda de validação entre os pontos avaliados. Ele contém pesos, mas não os estados internos do otimizador. Rodar `train` novamente inicia outro treinamento do zero e pode substituir esses arquivos; troque `piloto` por outro nome para guardar experimentos separados.

As pastas `runs`, `models` e `build` são ignoradas pelo Git. Elas não aparecem em um checkout novo apenas porque o código foi publicado.

## 3. Avaliar sem escolher só as melhores respostas

Após terminar o treinamento e escolher a configuração com treino/validação, execute o teste:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar evaluate native-ai/runs/piloto/model.luma native-ai/data native-ai/runs/piloto/evaluation.jsonl --tokens 48 --temperature 0.7 --top-k 20 --seed 2026
```

Esse comando registra as métricas e uma geração para **cada pergunta de `test.tsv`**, na ordem do arquivo, junto com a resposta de referência. Ele também confere se o arquivo de treino corresponde ao hash gravado no checkpoint. As saídas ruins devem continuar no relatório.

O arquivo `data/evaluation-prompts.tsv` é um roteiro adicional com dez perguntas sem resposta de referência. Para registrar todas elas, execute:

```text
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar samples native-ai/runs/piloto/model.luma native-ai/data/evaluation-prompts.tsv native-ai/runs/piloto/extra.jsonl --tokens 48 --temperature 0.7 --top-k 20 --seed 2026
```

Cada linha desse TSV contém um identificador e uma pergunta separados por tabulação. O comando preserva a ordem e todas as saídas, sem filtrar resultados ruins.

Uma perda ou perplexidade menor mostra melhoria nessa medida, nesses dados e com essa tokenização. Isso não comprova compreensão, conhecimento amplo, conversa coerente ou segurança. Os relatórios também indicam quantos exemplos precisaram ser truncados para caber no contexto. Se você ajustar o modelo depois de analisar o teste, registre isso e prepare um teste independente para a próxima avaliação.

## 4. Gerar uma resposta no terminal

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
