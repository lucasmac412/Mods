# Origem do corpus base da segunda experiência

Este arquivo descreve `base.tsv`, escrito por Codex em 20 de outubro de 2026 para melhorar o experimento de linguagem da Luma. Os pares foram redigidos individualmente nesta sessão, sem baixar textos, conjuntos de dados ou pesos de modelos e sem criar combinações automáticas de perguntas por produto cartesiano. São exemplos sintéticos, não mensagens humanas nem conversas privadas do jogador.

O arquivo é UTF-8, sem cabeçalho. Cada linha tem dois campos separados por uma tabulação: pergunta do jogador e resposta textual da Luma. As respostas são sugestões ou descrições das capacidades reais, não evidência de que uma ação foi executada. O formato não acrescenta acesso ao mundo nem permite que o texto gerado execute comandos.

São 300 pares, com 300 perguntas distintas após normalizar caixa, acentos e pontuação, e 277 respostas distintas. As respostas têm de 49 a 101 caracteres Unicode. Algumas respostas foram reutilizadas para pedidos semanticamente equivalentes, de forma deliberada para manter consistência de vocabulário e dos limites do NPC. Essas reformulações não representam fatos ou conhecimento novos. Não há expansão automática de prefixos neste arquivo.

Os assuntos incluem conversa social, personagem e limitações do software, uso dos comandos reais, planejamento em dupla, organização, construção, mineração limitada, exploração e combate. O vocabulário sobre o jogo evita prometer fabricação de receitas, uso de arcos/escudos/armaduras, projetos arbitrários de construção ou execução autônoma de uma campanha. A geração de texto pode continuar errada mesmo após treinar com estes exemplos.

O autor desta parte consultou somente o conjunto de treino antigo para evitar repetir perguntas. Não consultou nem preparou os conjuntos novos de validação e teste. A equipe deve verificar separação e semelhanças entre todos os conjuntos antes de usá-los, sem escolher ou reescrever o teste conforme as gerações do modelo.

“Próprio” significa aqui código e pesos treinados pelo projeto; não significa invenção da arquitetura Transformer ou autoria humana dos exemplos. O uso destes dados não demonstra qualidade comparável a modelos grandes. Este material acompanha a licença MIT do repositório em [LICENSE](../../LICENSE).
