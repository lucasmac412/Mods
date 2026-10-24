# Verificação ampliada das recompensas — 0.3.0

A versão distribuída permanece a **0.3.0**. Esta rodada acrescenta testes;
não altera a DLL do mod, o carregador ou o ZIP entregue aos jogadores.

## O que foi testado

| Camada | O que a verificação demonstra | O que não demonstra |
| --- | --- | --- |
| Regras com substitutos controlados | Seleção de anfitrião, instigador ou todos; quantias preservadas; configuração e estado restaurados | Missões reais ou inventário do Outward |
| HarmonyX real no Windows/.NET Framework | Prefixo e finalizer do código de produção funcionam sob cancelamento, exceções e outros patches | Compatibilidade com todo mod de terceiros ou com Unity/Mono |
| DLL exata do ZIP + Harmony incluído no ZIP | O binário publicado instala o patch, muda/restaura o destinatário e desativa/recarrega com as referências DE | Inicialização do Doorstop/BepInEx, entrega real de prêmios ou tráfego Photon |
| Referências DE incompatíveis, em cópias temporárias | Mudança do valor de `Everyone` ou perda de acesso público ao seletor são recusadas antes de instalar o patch | Toda alteração possível em versões futuras do jogo |
| Inspeção de metadados/IL | Assinaturas usadas existem e o plugin não entrega recompensas adicionais por inventário/RPC próprio | Implementação atual de todas as missões do executável Steam |

O teste do binário não recompila `RewardSharing.cs`: ele extrai e carrega
`Outward.Companheiros.dll` e Harmony/MonoMod diretamente do ZIP público.
Confere SHA-256 antes de carregar, usa a classe `GiveReward` das referências DE
fixadas e observa o seletor durante uma chamada interceptada de verdade.

Essas referências possuem corpos removidos (`ldnull; throw`). O teste confere
isso explicitamente e verifica tanto exceção quanto cancelamento do original;
não apresenta esse método vazio como uma execução real de recompensa.
Os testes de conteúdo usam um executor substituto separado e identificado.

## Casos adicionais

- Uma recompensa original é executada uma vez, inclusive quando já era coletiva.
- Solo, dois e quatro participantes; conversa representada como iniciada pelo
  anfitrião ou pelo convidado; quantias diferentes de prata e itens.
- Habilidade já presente, lista vazia, saída e retorno de um participante
  no executor simulado, sem prêmio retroativo fabricado pelo patch.
- Erro antes da entrega e erro no meio da entrega: exceção preservada,
  seletor restaurado e nenhuma repetição automática da recompensa.
- Outro patch cancela a ação, lança erro antes/depois do nosso prefixo ou
  lança erro depois da entrega: estado restaurado sem replay.
- Configuração desligada/religada, erro ao consultá-la, ações reutilizadas,
  chamadas aninhadas e desinstalação/reinstalação do patch.

**Limite observado:** a entrega não se torna uma transação entre os dois PCs.
No cenário simulado em que o anfitrião recebe e a entrega ao convidado falha,
o anfitrião mantém o prêmio e o convidado não recebe naquela execução. O mod
não repete a operação nem compensa falhas de conexão. Essa constatação evita
uma alegação incorreta de recuperação automática; não é a reprodução de uma
falha observada numa sessão real de Photon.

## Resultado da rodada ampliada

[A execução Windows 40137508098](https://github.com/lucasmac412/Mods/actions/runs/40137508098)
terminou com sucesso e compilação sem avisos/erros. Passaram 1.876 verificações
dos callbacks, 1.887 com HarmonyX real, 73 do binário exato publicado e 8 dos
cenários de referência incompatível, além das verificações existentes de
busca/caravana e contratos DE. Essas contagens incluem variações da mesma regra;
não representam uma quantidade de missões reais testadas.

Não foi necessário corrigir o código de produção ou trocar o pacote 0.3.0.
Na preparação dos testes, o gerador de cópias de referência precisou receber
o diretório das dependências Unity; a rodada completa passou após corrigir
esse utilitário de testes. A DLL distribuída permaneceu intacta.

## Evidência e reprodução

Resultados e ligação para a execução Windows estão em
[rewards-v030-validation.json](rewards-v030-validation.json).
O fluxo reproduzível está em [outward.yml](../../.github/workflows/outward.yml).
As cópias de referências incompatíveis são geradas somente na pasta temporária
do executor de testes. Nenhuma biblioteca do jogo vai no pacote distribuído.

ZIP completo 0.3.0:
`655a505ca2ba0e2120d71b7872b86bc42ff12b1a1b668538b9ddc2ac845f8f3b`

DLL publicada:
`ffe6d706ab1b7674f881e9d327d23c0a7a9dea92d6692e56f38025693757d5ae`

## Confirmação que continua faltando

Não há instalação completa do Outward, executável e dados Unity neste ambiente.
Não foi aberta uma partida solo/cooperativa, nem houve entrega de uma missão
real, teste de rede ou salvamento/carregamento de personagens reais.
Os testes acima aumentam a confiança no patch; não garantem todas as missões.

O próximo teste decisivo é os dois jogadores, com a versão 0.3.0 instalada e
personagens carregados, anotarem prata/itens, entregarem uma missão nova e
compararem o acréscimo. Para investigar uma diferença, guardar o nome da missão,
quem iniciou o diálogo e `BepInEx/LogOutput.log` dos dois PCs. A lista completa
de verificações em jogo permanece em [VALIDACAO.md](VALIDACAO.md).
