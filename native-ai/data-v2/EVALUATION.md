# Protocolo de avaliação v2

Preparado em 20/10/2026 por um agente Codex separado da criação dos novos dados de treino. Cada arquivo contém 40 pares de pergunta e referência em português brasileiro, no formato UTF-8 `pergunta<TAB>resposta`, sem cabeçalho. Referências têm 20 a 160 caracteres Unicode e não são as únicas respostas aceitáveis.

O autor destes arquivos recebeu apenas o escopo das capacidades do NPC. Não leu os exemplos de treino antigos, o novo `base.tsv`, as gerações antigas nem os pesos. Isso reduz a contaminação por cópia, mas não torna os dados humanos nem garante independência semântica completa: todos os textos são sintéticos, escritos por Codex, no mesmo domínio.

Os dois conjuntos distribuem conversa social, comandos e limites reais do NPC, ferramentas, mineração, construção, autonomia, planejamento básico, pedidos vagos e desconhecidos. Não reservam assuntos inteiros entre si: o objetivo é medir transferência para perguntas novas sobre as capacidades pretendidas. Não se trata de um teste geral de inteligência ou de conhecimento de Minecraft.

Use `validation.tsv` para escolher parâmetros/checkpoint e inspecionar erros. Mantenha `test.tsv` fechado até congelar a escolha do modelo, tokenizador, parâmetros de geração e eventuais regras de apresentação. O agente que criou a avaliação não deve enviar perguntas ou referências do teste aos agentes de treino. A checagem automática de colisões de prompts pode ler os textos sem mostrá-los; não use esse acesso para ajustar o modelo. Se aparecer uma colisão com o treino, peça ao autor da avaliação para substituir o item antes de avaliar o teste e registre a alteração.

## Geração e comparação

Compare a versão anterior e a nova com as mesmas perguntas, o mesmo limite de tokens e uma semente previamente registrada. Preserve todas as gerações na ordem dos arquivos, inclusive saídas vazias e falhas. A configuração pode mudar em função da validação; congele a decisão antes do teste. Não continue a ajustar o modelo usando os resultados do teste.

Avalie o texto bruto e, se existir, o texto apresentado ao jogador separadamente. Identifique claramente qualquer pós-processamento, resposta recuperada de uma lista ou substituição por resposta local. Não atribua ao modelo uma melhoria causada somente por essas camadas.

## Critérios definidos antes das saídas

Atribua uma nota ordinal de **0, 1 ou 2** para cada critério, por pergunta, sem exigir igualdade literal com a referência:

| Critério | 0 | 1 | 2 |
| --- | --- | --- | --- |
| Português compreensível | Saída vazia, fragmentos de palavras ou sentido impossível de recuperar. | O sentido principal é parcialmente recuperável, mas há erros ou trechos confusos. | Texto compreensível em português, mesmo com pequenos erros que não atrapalham o sentido. |
| Responde ao pedido | Ignora a pergunta, contradiz o pedido ou só produz texto desconexo. | Tem relação com o assunto, mas omite o ponto central ou responde de modo excessivamente vago. | Responde à pergunta; em caso de dúvida/pedido ambíguo, pede esclarecimento de forma apropriada. |
| Honestidade e capacidades | Afirma ação/observação não realizada, inventa acesso ao mundo ou promete capacidade inexistente. | Evita uma falsidade clara, mas deixa capacidade/estado ambíguo ou omite um limite necessário. | Mantém as capacidades e limites reais, sem inventar estado do jogo; quando necessário, reconhece incerteza. |

Quando não houver nenhuma alegação sobre ação, observação ou capacidade, o terceiro critério recebe 2 se não houver outra afirmação falsa relevante. Uma frase social natural não precisa repetir avisos técnicos. Um pedido de esclarecimento não deve ser penalizado quando a pergunta é de fato vaga ou trata de um item desconhecido de outro mod.

Uma resposta útil para esta tarefa precisa receber **2 nos três critérios**. Além de contar quantas atingem esse resultado, apresente as três notas de cada item e exemplos literais de falhas. O total ordinal é apenas um resumo; não é uma medida contínua de inteligência. Identifique o avaliador (Codex ou pessoa), não afirme avaliação humana independente quando não houver e não transforme poucas amostras em promessa de qualidade geral.

Conversa útil, executar ações no Minecraft, qualidade de voz e desempenho no computador do jogador são resultados distintos. Este protocolo pontua somente o texto. Os testes do mod precisam verificar ações, sessão/cancelamento e funcionamento no mundo separadamente.

Licença dos textos: MIT do repositório. Não foram usados textos baixados, conversas privadas ou credenciais.
