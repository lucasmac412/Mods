# Plano registrado antes da avaliação final

Data: 2026-10-20. Objetivo: verificar o pipeline original e medir um protótipo pequeno, com orçamento financeiro zero. Não é uma tentativa de demonstrar equivalência a modelos grandes.

Os dados permanecem fixos: 200 pares de treino, 24 de validação e 24 de teste. O tokenizador BPE é treinado apenas no treino, com vocabulário máximo de 384 tokens. Contexto 128, batch 2, AdamW com taxa 0,002, decay 0,01, clip 1 e seed 412 em todas as execuções. CPU Java, uma thread de cálculo por execução; sem GPU.

| Execução | Dimensão | Camadas | Cabeças | Feed-forward | Limite de passos | Limite de segundos |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| pilot-32 | 32 | 1 | 4 | 64 | 1.000 | 120 |
| candidate-64 | 64 | 2 | 4 | 128 | 8.000 | 180 |
| candidate-96 | 96 | 3 | 4 | 192 | 8.000 | 180 |

O piloto já foi executado. Os candidatos serão executados com os limites acima; não haverá busca adicional guiada pelo teste. Cada execução retém o checkpoint de menor loss de validação entre as medições a cada 100 passos, no primeiro passo e no fim. Escolheremos entre as três execuções pela menor loss de validação. O limite de tempo inclui validação e gravação, mas é verificado entre passos e pode ser ultrapassado pelo último passo/relatório.

Após a escolha, `evaluate` será executado uma vez com as 24 perguntas de teste, temperatura 0,7, top-k 20, até 48 tokens novos e seed 2026. Todas as respostas serão preservadas, inclusive saídas vazias, erros linguísticos e respostas inadequadas. Loss/perplexidade medem previsão de tokens, não inteligência, verdade ou êxito no Minecraft. Não faremos ajuste do modelo após ler o teste.

O conjunto de 10 prompts adicionais é destinado a inspeção qualitativa separada; não será usado para selecionar checkpoints. O protótipo só será oferecido como experimental, com aviso explícito de que pode produzir texto sem sentido. Ações do NPC continuarão controladas pelo parser e pela lógica do jogo, nunca pelo texto gerado.
