# Resultado do primeiro modelo próprio

Registro histórico da versão 0.2.0. Os arquivos e hashes deste relatório correspondem à [tag v0.2.0-experimental](https://github.com/lucasmac412/Mods/tree/v0.2.0-experimental). A versão atual tem outra avaliação em [experiments-v2](../experiments-v2/RESULTADOS.md).

**Data: 20/10/2026. O motor generativo funciona, mas este modelo ainda não serve para conversar de forma útil.** Nas 24 perguntas reservadas e nas dez perguntas adicionais, ele produziu principalmente fragmentos de palavras sem sentido. Não atingimos a qualidade pedida de um modelo comparável aos modelos grandes executados pelo Ollama.

O mod inclui esse checkpoint como uma experiência opcional, desligada por padrão. Digite `/luma ia ligar` somente se quiser observar o protótipo. `/luma ia desligar` volta às respostas locais. Os comandos do NPC continuam disponíveis nos dois modos. Nada neste relatório comprova uma voz original ou uma IA capaz de jogar Minecraft como uma pessoa.

## O que foi criado

O código de tokenização BPE, Transformer causal, derivadas/backpropagation, AdamW, checkpoint e amostragem foi escrito para este projeto em Java, usando a biblioteca padrão. Os pesos começaram aleatórios; não foram carregados pesos de terceiros. Os exemplos são sintéticos, escritos por Codex nesta sessão, conforme a [proveniência](../docs/PROVENANCE.md). “Próprio” não significa que inventamos a arquitetura Transformer nem que os dados foram escritos por uma pessoa.

Java, Minecraft e Fabric continuam sendo dependências. JUnit testa o código, mas não participa do motor de geração. Voz/microfone continuam usando o navegador/sistema. Não instalamos Ollama, não usamos API de IA para gerar as respostas durante o jogo e não contratamos recursos pagos.

## Escolha antes de abrir o teste

O [plano](PLAN.md) registra três configurações. Todas usam contexto 128, vocabulário BPE 384 aprendido só no treino, batch 2, seed 412, taxa 0,002, decay 0,01 e clip 1. São 200 exemplos de treino, 24 de validação e 24 de teste. A função de custo pontua somente tokens da resposta/EOS; o prompt pode receber gradientes pela atenção, mas não recebe custo direto. Não houve truncamento nos três conjuntos.

| Execução | Parâmetros | Passos concluídos | Melhor passo de validação | Loss de validação | Tempo registrado |
| --- | ---: | ---: | ---: | ---: | ---: |
| [pilot-32](pilot-32/training.json) | 37.664 | 1.000 | 900 | 3,283754 | 4,29 s |
| [candidate-64](candidate-64/training.json) | 124.800 | 8.000 | 500 | 3,385112 | 122,54 s |
| [candidate-96](candidate-96/training.json) | 310.944 | 4.508 | 400 | 3,442722 | 181,78 s |

Menor loss é melhor nessa medida. O menor modelo venceu; os maiores começaram a decorar o treino e pioraram na validação. O terceiro parou pelo limite de tempo. A seleção usou exclusivamente validação, conforme [selection.json](selection.json). Não ajustamos o modelo após ler as respostas do teste.

Esses tempos foram registrados nesta nuvem, não no computador do jogador. O código calcula em uma thread por execução; o Java informou quatro processadores disponíveis. Houve sobreposição de processos de treino/build em parte da execução, portanto a tabela não é um benchmark controlado de velocidade entre arquiteturas. O backend não usa CUDA/GPU; a nuvem não apresentou dispositivos NVIDIA. A RTX 4060 do usuário não está acessível daqui e este código também não a utiliza localmente.

## O que as métricas dizem

| Medida do modelo escolhido | Antes do treino | Checkpoint escolhido |
| --- | ---: | ---: |
| Loss de treino | 5,957442 | 2,570198 |
| Perplexidade de treino | 386,62 | 13,07 |
| Loss de validação | 5,962894 | 3,283754 |
| Perplexidade de validação | 388,73 | 26,68 |

Depois da escolha, o teste reservado apresentou **loss 3,332644 e perplexidade 28,01**, em 24 exemplos e 894 tokens de resposta/EOS. O treino tem apenas 6.700 tokens de resposta/EOS. São medidas de previsão de tokens desse microcorpus, não notas de inteligência ou de compreensão. A queda do erro não foi suficiente para gerar português compreensível.

## Todas as respostas preservadas

- [24 perguntas de teste, referências e gerações](test-generations.jsonl).
- [10 perguntas adicionais e gerações](extra-generations.jsonl).

Ambas as execuções usam até 48 tokens novos, temperatura 0,7, top-k 20, seed inicial 2026 e seed + índice para cada pergunta, na ordem dos arquivos. Não houve correção ortográfica, escolha das melhores respostas ou substituição por falas prontas.

Exemplos literais de falhas:

| Pergunta | Texto gerado |
| --- | --- |
| Como começar a aprender redstone? | Poblida, mus para ceira pode escantar ser áhes quena rota corção prefor. |
| Como guardar ingredientes de poções? | Paretochos mobre com muito. Clitencos perisavárecisa. |
| Como preparo uma travessia entre ilhas do End? | Um precorilar uma talar o pequedados corgurago d. |

Inspeção qualitativa realizada por Codex: nas 34 saídas não foi identificada uma resposta que atendesse à pergunta em português compreensível. Não houve avaliador humano independente nem teste amplo de conhecimento. Isso registra uma falha deste checkpoint nessas amostras; não estabelece uma taxa geral de qualidade.

## Integridade e testes de engenharia

O arquivo incluído é `native-ai/mod-resources/assets/luma/models/luma-native.luma`, com **302.982 bytes** e SHA-256:

```text
c0912769c29be52590938af792d7440afef78664294b50342e6ca27abe66604c
```

Uma repetição com a mesma configuração/semente e o código atual produziu um checkpoint idêntico byte a byte, registrada em [reproducibility.json](reproducibility.json). Essa repetição verificou reprodutibilidade; não selecionou outro modelo.

Passaram 48 testes do laboratório, cobrindo gradientes, causalidade, aprendizado de sequência pequena, máscaras, tokenização Unicode, clipping, corrupção de arquivos, geração e separação treino/teste. No mod, passaram 60 testes JUnit, incluindo carga/geração com esse checkpoint real, cancelamento, fila e timeout. Sete cenários Luma passaram no servidor Minecraft 26.2, além de um controle interno do runner. Esses testes validam a implementação e o comportamento programado, não a qualidade da conversa.

Ainda faltam validação gráfica no computador do jogador, teste audível e captura real do microfone. Não medimos desempenho em RTX 4060/16 GB. No mod, a geração roda fora da thread do jogo, tem limite de tempo/fila e só produz `CHAT`; o texto aprendido não escolhe ações. Cada pergunta é independente, sem histórico e sem aprendizado durante a partida.

## O que falta para o objetivo maior

O próximo avanço exige dados em português muito mais amplos, diversos e com direitos/origem registrados, avaliação independente e um backend de treino adequado. Repetir milhares de vezes estas 200 falas ou aumentar apenas as camadas não resolve a falta de dados. Uma GPU disponível no computador não é utilizada automaticamente por código Java de CPU.

Treinar um modelo grande do zero, produzir reconhecimento e síntese de voz próprios e validar um agente capaz de jogar livremente são trabalhos adicionais. Não há neste pacote um caminho gratuito já demonstrado que entregue esse nível de qualidade. Não compre hardware ou serviços para tentar transformar este checkpoint em um assistente completo. O projeto entregue permite estudar e reproduzir o pipeline e experimentar a integração; a capacidade de conversa solicitada continua pendente.
