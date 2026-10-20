# Origem e licença dos exemplos

Este microcorpus foi escrito por Codex nesta sessão de desenvolvimento, em 19 de outubro de 2026, especificamente para o experimento Luma. São exemplos sintéticos autorais, em português brasileiro, sobre conversa e planejamento no Minecraft. Não foram baixados textos, conjuntos de dados ou pesos de modelos para compor estes arquivos. Não há mensagens privadas do usuário, credenciais ou dados pessoais nos exemplos.

“Autoral” descreve como estes arquivos foram preparados para o projeto; não significa que o texto foi redigido por uma pessoa. Codex gerou os exemplos. O treino proposto começa com parâmetros aleatórios e estes arquivos, sem carregar pesos pré-treinados. O pequeno corpus sintético não constitui um treino independente em grande escala, nem prova inteligência comparável a um modelo conhecido.

Os arquivos de dados e esta documentação são disponibilizados sob a licença MIT do repositório, em [LICENSE](../../LICENSE). Mantenha o aviso de licença ao redistribuir o material.

## Arquivos e formato

`train.tsv`, `validation.tsv` e `test.tsv`, em `native-ai/data`, são arquivos UTF-8 sem cabeçalho. Cada linha contém exatamente dois campos: o texto do jogador, uma tabulação literal e a resposta da Luma. Não há tabulações ou quebras de linha dentro de um campo. Não há linha de comentário ou linha vazia. As respostas são sugestões textuais; uma frase gerada não executa ações no mundo.

São 200 exemplos de treino, 24 de validação e 24 de teste, totalizando 248 pares. Os dez cenários de geração ficam em um arquivo separado e não incluem respostas de referência. Os prompts têm no máximo 100 caracteres e as respostas, entre 20 e 90 caracteres. Esses limites contam caracteres Unicode, não bytes ou tokens. Perguntas e respostas foram verificadas para evitar repetições após normalizar maiúsculas, espaços e pontuação entre os três conjuntos.

Um leitor pode separar cada linha uma única vez no caractere de tabulação. O formato de contexto esperado é:

```text
Jogador: <texto do primeiro campo>
Luma: <texto do segundo campo>
```

O contexto termina com uma quebra de linha. O formato do arquivo não inclui literalmente os rótulos `Jogador:` e `Luma:`; o programa de treino os acrescenta.

`evaluation-prompts.tsv` usa também dois campos, mas o primeiro é um identificador estável do cenário, e o segundo é uma pergunta sem resposta fornecida. Ele serve para registrar gerações na ordem original, sem escolher apenas os melhores resultados. Não deve entrar no treino ou na seleção de exemplos do treino.

## Separação dos exemplos

O treino cobre começo do jogo, abrigo, construção simples, ferramentas, mineração inicial, organização, cultivo, riscos comuns, convivência e limites do software. A validação usa esses assuntos em perguntas redigidas separadamente. É útil para acompanhar o experimento, mas compartilha conhecimento básico com o treino; um bom resultado nela pode refletir essa proximidade.

O teste reserva famílias de assuntos inteiras: Nether, End, redstone, oceano, vilas e trocas, encantamentos, poções, arqueologia e pesca. Esses assuntos não foram transformados em exemplos quase iguais no treino. O teste exige alguma transferência para temas ausentes e pode revelar a limitação do microcorpus. Nem o nome de um conjunto nem a ausência de frases repetidas garantem independência completa: todos os exemplos continuam no mesmo domínio e foram redigidos pelo mesmo sistema.

O teste deve ficar fora da escolha de parâmetros, do vocabulário aprendido apenas com treino e de qualquer seleção de checkpoints. Se alguém ajustar o sistema após ler os resultados do teste, deve registrar isso e criar um novo conjunto independente para a próxima avaliação.
