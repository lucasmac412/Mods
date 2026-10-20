# Luma 0.3.0: resultado da melhoria experimental

20/10/2026. A nova versão produziu **11 respostas úteis em 40 perguntas de teste**, contra **zero em 40** da versão 0.2. A avaliação foi feita por Codex, em textos sintéticos do domínio da Luma, sem revisão humana independente. Isso mostra uma melhoria neste teste pequeno; **não significa qualidade comparável a um modelo grande**. Em 29 perguntas, a resposta nova ainda não cumpriu todos os critérios.

## O que foi entregue

Um Transformer causal original de **277.504 parâmetros**, BPE de 1.536 tokens, contexto de 192 tokens, dimensão 64, duas camadas, quatro cabeças e FF 128. O código Java, os dados sintéticos e os pesos são do projeto. O treinamento inicial partiu de pesos aleatórios; o refinamento reutilizou somente o nosso próprio checkpoint e tokenizador, com um AdamW novo. Não baixamos pesos pré-treinados, instalamos Ollama ou contratamos serviços pagos. A execução usa CPU, sem CUDA/RTX 4060.

A geração recebe uma nota escolhida por uma busca lexical própria, com TF-IDF/cosseno sobre exemplos autorais de treino. **A nota entra como contexto; a resposta é gerada token por token pelo Transformer.** O código não retorna a resposta recuperada diretamente, nem limita a saída a uma lista de frases. O currículo ensinou a reproduzir informações das notas em diferentes contextos; isso não demonstra raciocínio amplo. Em **22 das 40 saídas novas**, o texto gerado foi literalmente igual à nota, inclusive quando a busca escolheu o assunto errado.

A memória é estática e acompanha o JAR. Não guarda conversas nem aprende durante a partida. O modelo só produz `CHAT`; comandos e ações reais continuam na lógica do NPC. Nesta versão também liberamos perguntas com “não”, “ou” e “talvez” para a geração, preservando ordens reconhecidas pelo parser, como “não me siga”. Voz e microfone continuam sendo recursos do navegador/sistema, sem modelo próprio de voz.

## Dados e treinamento

- 200 pares anteriores + 300 novos de domínio + 180 de conversa: **680 pares originais**. Prefixos de entrada geraram **2.040 linhas**; são variações de chamada, sem conhecimento novo.
- Validação e teste têm **40 perguntas cada**, escritas por outro agente Codex sem ler o treino. Respostas reservadas não entraram no tokenizador, nos alvos de treino ou no índice de busca. O teste ficou fechado até congelar a seleção.
- Os ensaios sem memória (`v2-response`, `v2-full`) memorizaram o treino e continuaram ruins na validação. Seus relatórios e saídas foram preservados.
- `v2-grounded`: 12.000 atualizações com notas de treino, LR 0,0015, warmup 300, cosine, batch 2; 321,45 s totais registrados.
- `v2-copy`: refinamento do nosso `v2-grounded/last.luma`, com **8.599 linhas**. São os exemplos preparados, 12 perguntas de treino recombinadas por resposta distinta e 128 contextos de informação insuficiente. Notas e perguntas vêm somente do treino. Foram 10.000 atualizações, LR 0,0005, warmup 100, cosine, batch 2; 330,51 s totais registrados. Essa recombinação ensina o uso da nota e não cria conhecimento factual.

Origem, licenças e hashes: [PROVENANCE.md](../data-v2/PROVENANCE.md), [manifest.json](../data-v2/manifest.json), [curriculum.json](../data-v2-copy/curriculum.json). Plano e adendos anteriores ao teste: [PLAN.md](PLAN.md). Configurações e históricos completos: [treino com notas](v2-grounded/training.json) e [refinamento](v2-copy/training.json).

## Seleção antes do teste

A rubrica atribui 0, 1 ou 2 para português compreensível, pertinência à pergunta original e honestidade sobre capacidades. **Útil exige 2 nos três critérios**. Fluência sozinha não basta. A rubrica está em [EVALUATION.md](../data-v2/EVALUATION.md).

Na validação completa, a versão anterior teve 0/40 úteis; o snapshot de 6.000 atualizações teve 20/40; o de **10.000 teve 22/40**, sendo escolhido. O menor erro de referência do treino não escolheu o pacote: `model.luma` é o checkpoint selecionado automaticamente por perda, enquanto a exportação usa `step-010000.luma`, escolhido pela rubrica de geração. As notas e hashes estão em [validation-review.json](validation-review.json).

A recuperação foi examinada separadamente na validação. A configuração escolhida foi limiar **0,15** e peso **0,7** para termos das respostas do índice de treino. As 26/40 notas julgadas úteis na [revisão da memória](memory-validation-review.json) **não são 26 respostas neurais úteis**. O decoder final usa até 64 tokens, temperatura 0 (greedy), top-k 20. Pesos, recuperação e decoder foram congelados em [selection.json](selection.json) antes do teste, sem ajustes posteriores pelos erros do teste.

## Teste reservado: todas as 40 perguntas

| Critério | Luma 0.2 | Luma 0.3 |
| --- | ---: | ---: |
| Respostas úteis: nota 2 nos três critérios | 0/40 | **11/40 (27,5%)** |
| Português compreensível: nota 2 | 0/40 | 26/40 |
| Pertinência à pergunta: nota 2 | 0/40 | 12/40 |
| Honestidade/capacidades: nota 2 | 39/40 | 27/40 |

O critério de honestidade dá 2 a uma saída sem alegação recuperável quando não há falsidade relevante. Por isso os fragmentos da versão antiga podem ter nota alta nessa coluna sem serem úteis. A versão nova passou a fazer afirmações e também introduziu erros e limites omitidos; essa coluna piorou e não deve ser escondida.

Saídas completas, sem selecionar apenas acertos: [baseline-test.jsonl](baseline-test.jsonl), [new-test.jsonl](new-test.jsonl). Notas dos 80 itens, motivos, hashes e limitações: [test-review.json](test-review.json). A pertinência foi julgada pela **pergunta original**, não pela concordância com a nota recuperada. Avaliador e auditor são instâncias Codex, sem revisão humana independente.

Esta é uma comparação dos **sistemas finais**, não um experimento que isola os pesos: a baseline usa temperatura 0,7 e pergunta simples; a nova usa temperatura 0 e contexto com nota. Corpus, arquitetura e tokenização também mudaram. Uma geração por pergunta não estima variabilidade. O limite comum de 64 tokens pode representar comprimentos textuais diferentes. As referências são sintéticas e do mesmo domínio; não são um benchmark geral.

Os JSONL preservam também perda/perplexidade das referências. CE por token foi 3,9445 na baseline e 10,9519 na nova, com tokenizadores e contagens de tokens diferentes; **não é uma comparação direta entre modelos**. Não reivindicamos melhoria de perda nas referências. A melhoria descrita aqui é a contagem de respostas úteis nessa rubrica; a reprodução de notas com redação diferente da referência pode ter perda alta. O resultado tampouco comprova compreensão geral.

## Falhas concretas

- “Sua capacidade é igual à de um baú simples?” → “Não. Um modelo pequeno tem capacidade e conhecimento bem limitados.” Confunde os 27 espaços da mochila com capacidade do modelo e dá uma negação incorreta.
- “Monta uma bancada com a madeira que eu te passei?” → “Boa noite! Uma aventura tranquila também pode ser divertida.” A frase é legível, mas não responde ao pedido.
- “Qual comando cancela a mineração que pedi sem querer?” → repetições e fragmentos, sem fornecer `/luma parar`.

A busca pode recuperar uma nota errada e a rede reproduzi-la com confiança. A rede também pode deformar palavras e repetir trechos. Para tarefas reais, use os comandos documentados; uma fala gerada não prova execução de ação.

## Artefatos e reprodução

| Artefato | SHA-256 |
| --- | --- |
| Modelo final, 2.231.312 bytes | `394201871af8a2c20aa0c71f04f7d822b58a9488c9da7a2cd4a5a50716b9a199` |
| Índice `luma-knowledge.tsv`, 2.040 linhas | `93abde2380aa2c1081cf742e55a9d714452f9cb95db133be59007eb658fa79d3` |
| Treino do refinamento, 8.599 linhas | `3ec9f7dfd3b06a0b367f951dbf6b86e572f0ccf3f63d4566c0d0880217db601d` |
| Checkpoint próprio inicial do refinamento | `77fc1047e41db1bdb37fccd8503953e0c47112e19c880046e5c9c07d23deadf2` |

Os pesos e o índice exportados estão em `native-ai/mod-resources/assets/luma/models/`; o pacote os inclui dentro do JAR. Checkpoints intermediários e saídas de build em `runs` são ignorados pelo Git. Dados preparados, históricos e avaliações são publicados; não é preciso retreinar para instalar.

Para reproduzir a avaliação final a partir da raiz, com JDK 25 e o laboratório compilado:

```sh
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar prepare-grounded native-ai/data-v2 native-ai/runs/final-evaluation --threshold 0.15 --answer-weight 0.7
cp native-ai/data-v2-copy/train.tsv native-ai/runs/final-evaluation/train.tsv
java -jar native-ai/build/libs/luma-native-ai-0.1.0-experimental.jar evaluate native-ai/mod-resources/assets/luma/models/luma-native.luma native-ai/runs/final-evaluation native-ai/runs/final-test.jsonl --split test --tokens 64 --temperature 0 --top-k 20 --seed 2026
```

A cópia do treino mantém a conferência de proveniência do checkpoint; não executa treino. O preparo usa só `data-v2/train.tsv` no índice, com os parâmetros finais da memória. Os arquivos históricos de `data-v2-copy` preservam o preparo inicial de limiar 0,25/peso 0, sem alterar a história do treino. Reproduzir agora não torna o teste desconhecido novamente: uma próxima melhoria precisa de um novo teste reservado.

O mod passou 62 testes JUnit, o laboratório passou 66 e sete cenários da Luma passaram no servidor Minecraft 26.2, além de um controle do runner. Um smoke test no JAR instalável carregou os dois recursos e gerou `CHAT`. Esses testes verificam engenharia; não validam todas as respostas da IA. Cliente gráfico, microfone/áudio real e desempenho no computador do jogador continuam sem teste. Instalação: [guia completo](../../docs/INSTALAR.md).
