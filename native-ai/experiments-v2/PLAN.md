# Plano de melhoria v2, antes do teste reservado

20/10/2026. Meta: melhorar português e pertinência em relação à Luma 0.2.0, mantendo pesos aleatórios, código Java original, dados sintéticos autorais e custo financeiro zero. Não prometemos qualidade de modelo grande.

Os 40 pares de validação e 40 de teste foram escritos por outro agente Codex que não leu os dados de treino. O conteúdo do teste fica fechado até congelar o modelo e a geração. Todos os textos continuam sintéticos e do mesmo domínio; isso não equivale a avaliação humana independente. Os critérios estão em `data-v2/EVALUATION.md`.

O treino combina os exemplos originais, novos exemplos de domínio e 180 exemplos de conversa com paráfrases. `prepare-data-v2.py` elimina perguntas normalizadas duplicadas e colisões exatas com perguntas reservadas. Acrescenta duas formas de chamar a personagem a cada pergunta: `Luma, ...` e `Me ajuda: ...`. Isso aumenta formas de entrada, não conhecimento. As respostas reservadas não entram no treino/tokenizador. O manifesto dos dados registra números e hashes.

## Candidatos registrados

Dois candidatos com o mesmo tokenizador aprendido somente no treino: BPE até 1.536 tokens, contexto 128, dimensão 64, quatro cabeças, uma camada, feed-forward 128, batch 4, seed 412, AdamW com LR 0,0015, decay 0,01, clip 1, warmup 400, schedule cosine até 10% da taxa inicial. Limites: 16.000 passos ou 420 segundos por execução. Validação a cada 500 passos; snapshots a cada 2.000, além do menor erro e do último estado.

- `response`: supervisão apenas na resposta/EOS, como antes.
- `full`: supervisão no diálogo inteiro, incluindo a pergunta; métricas de comparação continuam pontuando somente resposta/EOS.

Se ambos falharem em melhorar português/pertinência na validação, é permitido um terceiro ensaio de até420segundos: mesma configuração `response`, dimensão96, duas camadas e FF192. Outras mudanças exigem registro explícito neste plano antes de executá-las; não usaremos o teste para orientar ajustes.

## Escolha e comparação

Geraremos respostas para as 40 perguntas de validação usando o checkpoint de menor custo, o último estado e os snapshots intermediários. Primeiro, triagem dos checkpoints por fluência nas primeiras dez perguntas fixas da validação; os finalistas serão avaliados nas 40. Decoder: até64tokens, temperatura0 (greedy),0,2 ou0,7, top-k20, seed2026+índice. A comparação bruta inclui o modelo0.2 com seu decoder publicado0,7. Nada de resposta recuperada de lista ou substituição silenciosa por falas programadas.

Critério de seleção: maior número de respostas úteis (2 em português, pertinência e honestidade); depois maior número de textos compreensíveis; em empate, menor NLL por byte de referência. A avaliação de texto será feita por Codex e identificada como tal. Não compararemos perplexidade/CE por token entre vocabulários diferentes; o denominador comum será o número de bytes UTF-8 das respostas de referência. O custo inclui EOS em todas as medições.

O relatório `baseline-validation.jsonl` foi produzido pelo CLI antigo, que só oferecia o nome de split `test`: usamos um diretório temporário cujo `test.tsv` era exatamente a validação v2. Ele contém validação, não o teste reservado. O CLI novo oferece `--split validation` explicitamente.

Também registramos a baseline com temperaturas 0 e 0,2 na validação, para verificar se trocar somente o decoder resolveria o problema. A comparação do produto publicado inclui mudanças conjuntas de pesos, corpus, tokenização e decoder; não isola um único fator. O teto comum de 64 tokens novos pode representar comprimentos diferentes de texto nos dois vocabulários, portanto não será chamado de orçamento textual idêntico. A análise considera também finais cortados e saídas incompletas.

Depois de congelar modelo e decoder, rodaremos as 40 perguntas de teste uma vez em cada versão. Serão preservadas todas as saídas e notas, inclusive erros e textos vazios. Se a melhoria falhar no teste, relataremos isso sem continuar a ajustar nesse teste. Ordens do NPC permanecem no parser/lógica do jogo, separadas da fala gerada.

## Adendo registrado ainda com o teste fechado: geração com memória local

Os primeiros snapshots nas dez perguntas fixas de validação formaram algumas frases legíveis, mas continuaram confundindo assuntos. Por isso testaremos também um candidato com uma memória textual local autoral, em vez de atribuir a falta de conhecimento apenas ao tamanho do modelo.

O índice usa somente as perguntas/respostas de treino, com TF-IDF/cosseno em palavras e partes de palavras. A nota recuperada é inserida no prompt; a resposta final continua sendo produzida pelos logits do Transformer, sem restringir os tokens a uma lista de frases. Precisaremos medir a recuperação e a geração separadamente. Se essa técnica for escolhida, ela será declarada como geração com memória/grounding, e não como conhecimento aprendido exclusivamente nos pesos.

Primeiro ensaio: BPE 1536, contexto 192, dimensão 64, quatro cabeças, duas camadas, FF 128, batch 2, seed 412, LR 0,0015, decay 0,01, clip 1, warmup 300, cosine, até 12.000 passos ou 420 segundos; objetivo response, snapshots a cada 2.000 e validação a cada500. O limiar de busca começa em 0,25; pode ser escolhido entre0,15/0,25/0,35 pela pertinência das notas nas 40 perguntas de validação, antes do teste. O texto de referências reservadas não entra no índice nem na nota. Ausência de nota é representada por uma indicação explícita de informação insuficiente, não por uma resposta retirada do teste.

Mantemos os relatórios dos ensaios sem memória, inclusive saídas ruins. A adoção do grounding e do decoder dependerá da mesma rubrica de validação. Ainda não foi executado o teste reservado nem lidas suas respostas para orientar esta mudança.

## Adendo após retomar a nuvem: usar a nota apesar de perguntas diferentes

A triagem do primeiro modelo com memória também revelou erros de português: ele frequentemente seguia o começo da nota e depois misturava palavras. O teste reservado continua fechado. Faremos um refinamento dos nossos próprios pesos, sem baixar pesos externos: `prepare-copy-curriculum.py` recombina perguntas de treino com notas/respostas de treino para ensinar o uso da informação fornecida apesar de mudanças na pergunta. Essas recombinações não criam conhecimento novo e não devem ser descritas como diálogos naturais corretos sem a nota.

São 12 contextos aleatórios por resposta autoral distinta, além dos 2.040 exemplos originais e 128 casos de nota desconhecida. A origem é somente treino, seed 412. Validação e teste são copiados dos conjuntos preparados; referências reservadas nunca viram notas. O refinamento começa no nosso `v2-grounded/last.luma`, preservando tokenizador/configuração, e usa um AdamW novo: LR 0,0005, warmup 100, cosine, decay 0,01, clip 1, batch 2, até 10.000 passos ou 420 segundos, snapshots a cada 1.000 e validação a cada500. Não é retomada do estado de otimizador nem uso de modelo de terceiros.

A recuperação poderá dar peso0 / 0,35 / 0,7 aos termos das respostas do próprio índice de treino; a escolha usa apenas validação. A geração continua livre pelo Transformer. Serão distinguidos acertos da recuperação e acertos da resposta neural, sem chamar cópia direta de nota por código de geração.
