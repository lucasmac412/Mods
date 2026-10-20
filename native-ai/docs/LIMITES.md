# O que este experimento pode demonstrar

A proposta é um modelo generativo pequeno, treinado do zero para produzir texto de conversa. “Generativo” quer dizer que ele prevê e gera sequências; não significa que tenha a capacidade de um assistente geral. Usar a arquitetura Transformer, conseguir reduzir o erro de treino ou gerar algumas frases legíveis não torna o resultado comparável a Llama, ChatGPT ou aos grandes modelos executados no Ollama.

Ollama é um programa para executar modelos; ele não é o modelo em si. Este experimento busca um caminho próprio e pequeno de treino e execução. Isso não elimina as dependências básicas do computador, de Java, Minecraft e Fabric, nem cria sozinho reconhecimento de voz ou uma voz sintetizada original.

Com algumas centenas de falas curtas, o resultado pode repetir trechos, misturar palavras, produzir texto incompleto e ignorar uma pergunta. O domínio limitado não oferece conhecimento amplo de português ou de Minecraft. Não há garantia de conversa coerente mesmo dentro do domínio. Também não há garantia de desempenho no computador de cada jogador.

Uma resposta não observa automaticamente o mundo, não confirma inventário e não executa uma tarefa. Ações devem continuar passando pelas capacidades reais e pelas verificações do mod. O modelo não deve receber acesso para executar comandos do sistema, apagar arquivos ou transformar uma frase em código. Os exemplos sobre privacidade e limites são material de treino, não um mecanismo de segurança confiável.

## Como avaliar sem exagerar o resultado

Registre a configuração do modelo, a versão dos arquivos, a semente aleatória, os passos de treino, o tempo gasto e os recursos usados. Separe o tempo de treino do tempo de geração. Não invente resultados para preencher campos ainda não medidos.

É possível acompanhar a entropia cruzada e a perplexidade nos conjuntos de treino e validação. Registre também a unidade usada, como bytes ou tokens, e a tokenização. Valores só são comparáveis quando a forma de medir é a mesma. Perplexidade baixa neste corpus pequeno não prova capacidade geral, entendimento, segurança ou boa execução no jogo.

Escolha o checkpoint e os parâmetros com treino e validação. Depois rode o teste uma vez e registre todas as gerações, inclusive as ruins, na ordem dos arquivos. Os dez cenários de `evaluation-prompts.tsv` podem formar um relatório legível de amostras independentes do treino. Registre o tamanho máximo de saída e qualquer temperatura ou amostragem usada. Não descarte saídas só porque ficaram ruins. Se uma geração for interrompida por erro, registre o erro.

Para cada saída, alguém pode avaliar se está em português compreensível, se responde à pergunta, se contradiz o próprio texto e se inventa observações do mundo. Marque falhas de conhecimento e respostas que apenas repetem o treino. Uma nota manual deve identificar quem avaliou e quais critérios usou. Não transforme poucas amostras em uma porcentagem de qualidade geral.

Ainda seria preciso testar a integração real no Minecraft, a duração das respostas, o consumo de memória e o comportamento quando o texto é inadequado ou incompleto. A aprovação da compilação não comprova essas propriedades. Esta documentação não declara resultados de treino, testes, benchmarks ou testes de voz.

## O que seria necessário para ampliar o projeto

Uma IA de conversa com capacidade muito maior exige outro nível de dados e de trabalho: textos próprios com direitos e origem documentados, diversidade de português, revisão, remoção de duplicatas e dados privados, e avaliações realmente independentes. Escrever muitas variações da mesma frase não substitui diversidade.

Também seriam necessários planejamento de arquitetura, treinamento em GPUs apropriadas, armazenamento, checkpoints, monitoramento e tempo para repetir experimentos. A escala depende do tamanho do modelo, da quantidade de dados e do objetivo de qualidade; esta documentação não presume hardware disponível nem estima um custo sem medições.

Um projeto maior deveria começar por critérios claros de qualidade e por uma auditoria das restrições de autoria e dependências. Se nenhum modelo pronto ou serviço de terceiros puder ser usado, é preciso produzir ou licenciar adequadamente os dados e treinar os pesos. Mesmo com esses recursos, alcançar qualidade comparável à de modelos conhecidos não é um resultado garantido.
