# Personagens e modos da versão 0.1.4

A 0.1.3 aceitava somente `PlayerCharacter::Leon`. A 0.1.4 aceita os seis identificadores jogáveis do SDK PC: Leon, Ashley, Ada, HUNK, Krauser e Wesker. O identificador composto interno e valores desconhecidos conservam o controle nativo. A identificação também exige que o objeto seja exatamente `PlayerPtr()`: não aplica WASD à Ashley acompanhante ou a NPCs.

O dispatcher comum já instalado intercepta as rotinas de pé, caminhada, recuo, corrida, virada e arma; suas funções originais usam as tabelas de animação e recursos do personagem atual. Não troca a tabela de Ashley por animações do Leon nem chama armas inexistentes para ela. Não há novos patches de prólogo ou assinaturas de funções específicas de personagens. As correspondências dos identificadores são conferidas com `static_assert` contra o SDK PC.

Os modos não são filtrados pelo controlador: o mesmo personagem Ada é aceito em Separate Ways, Assignment Ada e Mercenaries. A movimentação não reconfigura os modos nem suas regras. O suporte de código não equivale a uma execução validada em cada um deles.

Proteções adicionais:

- Ashley: permite andar/correr com câmera relativa ao mouse; nunca adiciona deslocamento de arma ou gira seu corpo como se estivesse levantando uma arma. O hook existente que escolhe seus ângulos clássicos JP mantém a câmera de ombro quando a extensão está habilitada e instalada, Ashley é a personagem atual e a entrada é teclado/mouse. Fora dessas condições, a escolha original é preservada.
- Personagens armados: deslocamento de mira só nos estados normais 1 (mira) e 2 (disparo). Estados de levantar/guardar arma, recarregar e rotinas especiais são nativos. No estado 2 dos itens arremessáveis, a animação de lançamento fica inteiramente nativa.
- Transformação do Krauser, ação corpo a corpo e evento: os status nativos entregam a movimentação ao jogo. As rotinas acima de 6 já preservam gancho, golpes, escadas, agachamento, interações e ações de cenário. O estado é conferido novamente depois da chamada nativa para não arrastar um personagem que acabou de entrar em uma ação especial.
- A chave da referência da câmera inclui sala e personagem: trocas que reutilizam os mesmos endereços não conservam a orientação anterior de outro personagem.

Referências de estudo: SDK de re4_tweaks na revisão fixada em `upstream.json` e referências públicas das classes `cPlayer`, `cPlAshley` e `cPlWesker` e das rotinas de armas. Somente o código original da extensão e patches sobre o re4_tweaks são distribuídos; nenhum código reconstruído, asset ou executável do jogo foi incorporado.

Verificação automática: matriz dos seis personagens com todas as combinações WASD, corrida, estados especiais, subestados de arma e devolução a NPCs/identificadores desconhecidos; continuidade da câmera na troca de personagem; regressões anteriores de movimento, câmera e fronteira x86. Esses testes simulam decisões e estado; não carregam personagens ou mapas do RE4.

Checklist de validação real, ainda pendente em todos os casos desta versão:

| Personagem/modo | Ações a conferir no jogo |
| --- | --- |
| Leon / campanha e Mercenaries | Andar, correr, mirar/atirar, recarregar, chutar/suplex, inventário e portas |
| Ashley / trecho controlável | Andar, correr, agachar/passar por baixo, interagir com puzzles/objetos, dano e troca para Leon |
| Ada / Separate Ways | Movimentação, armas, recarga, golpes, gancho e mudanças de capítulo/sala |
| Ada / Assignment Ada | Movimentação, armas, inventário, objetivos e término do modo |
| Ada / Mercenaries | Movimentação, armas, golpes e fim da rodada |
| HUNK / Mercenaries | Movimentação, TMP, recarga, granadas e quebra de pescoço |
| Krauser / Mercenaries | Movimentação, arco, facada, transformação e ataque com o braço |
| Wesker / Mercenaries | Movimentação, armas, luneta, recarga e golpes especiais |

Em todos: câmera ao trocar WASD, mouse simultâneo, Shift, parar/retomar, paredes, dano/morte, pausa e perda de foco. Manter as limitações da câmera nativa em cenas e as animações existentes de mira (sem uma nova mistura de pernas e tronco). Sem executar o jogo não há garantia honesta de funcionamento completo de todos os itens dessa lista.
