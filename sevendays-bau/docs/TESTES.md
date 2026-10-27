# Validação — 0.3.0 experimental

Realizada em 27/10/2026 contra **7 Days to Die V3.4.0 (b6)** oficial.

## Resultados

| Camada | Resultado | Cobertura real |
| --- | --- | --- |
| Build .NET Framework 4.8 | 0 erros / 0 avisos | Referências oficiais da versão 3.4.0 |
| ServerAdapter, retirada | 1.681 verificações | Código de produção com mundo/API controlados |
| ServerAdapter, depósito | 64.009 verificações | Código de produção, incluindo 1.000 inventários aleatórios e 100 disputas concorrentes por espaço |
| ClientAdapter | 117 verificações | Código real de reserva/devolução, com UI/inventário/rede substituídos |
| RuntimeProbe | 203 verificações | DLL dentro do servidor Linux oficial, métodos e tipos reais do jogo |
| ZIP | Estrutura, CRC e igualdade da DLL conferidos | Inclui XML do botão e não inclui DLLs proprietárias/testes |
| Cooperativo com clientes Steam e UI gráfica | **Não realizado** | Não havia clientes/jogadores disponíveis |

Os números incluem várias asserções por cenário/iteração, não milhares de partidas. Testes de concorrência controlada não substituem uma partida multiplayer.

## Regressão da fabricação e isolamento das estações

Mantidos os cenários de retirada mais próxima, múltiplos ingredientes, falta parcial sem débito, permissões, slots bloqueados, raio/altura, replay, rollback e 100 disputas pelo último material. No servidor real, 20 combinações executaram `XUiM_PlayerInventory.HasItems/RemoveItems`, conferindo mochila, saldo remoto e materiais de cancelamento.

A nova sonda executa os métodos reais da grade de entrada em contextos de personagem, forja, forja sem fundição, betoneira, bancada, fogueira, estação de química e bancada personalizada. Confere contagem, disponibilidade, consumo e materiais para cancelamento: bancadas permitidas usam saldo remoto; forja e betoneira o ignoram. Apenas o backend visual da estação é substituído. A política também verifica os IDs completos de janelas e diferenças de maiúsculas/minúsculas.

## Depósito no servidor

- Preencher pilhas em vários baús antes de ocupar espaços livres; respeitar proximidade.
- Nunca iniciar uma categoria em um baú que não a possuía, mesmo vazio.
- Devolver exatamente itens sem destino ou sem capacidade.
- Baús fora do alcance, na vertical, abertos, ocupados, privados, de saque e slots bloqueados excluídos.
- Acesso do proprietário, fronteira inclusiva de 50 m e slots vazios bloqueados.
- Metadados diferentes preservados em slots separados.
- Falha na notificação de um entre vários baús: reversão de todos e devolução integral, inclusive falha persistente.
- Repetição de pedido/ack sem reaplicação e rejeição de identidade falsa.
- 100 pares de depósitos concorrentes disputando o último espaço conservam o total.
- 1.000 conjuntos aleatórios conferem conservação por tipo, capacidade de cada slot, bloqueios e categorias originais.

## Reserva e devolução no cliente

Executado `QuickStore.cs` de produção com substitutos controlados: consulta não remove itens; reserva exata uma vez; clique/offer duplicados ignorados; retry usa o mesmo ID/saldo; devolução parcial aos slots originais; replay não duplica; slots bloqueados; consulta expirada; resposta inválida rejeitada; retorno à barra rápida; espaços ocupados durante espera; inventário cheio com queda exata no chão e aviso; UI temporariamente indisponível; fabricação concorrente bloqueada. Mais 100 quantidades passaram por vários retries/replays.

## Integração com o servidor oficial

Sonda executada no mundo `Empty`, saves isolados, listagem pública/EAC/telnet/dashboard desativados e senha aleatória. Confirmou:

- Carregamento da DLL, instalação dos hooks e registro dos dois protocolos de rede.
- Comportamento nativo de inventário e isolamento das estações descritos acima.
- Plano de depósito com capacidade real de pilhas, aplicação no `ItemStackGrid` nativo, baú vazio excluído e seleção de mochila/barra respeitando bloqueios reais.
- Validação de devoluções usando `ItemValue` real.
- Serialização com `PooledBinaryWriter/Reader` e `ItemStack` reais nos dois protocolos.
- O carregador/patcher XML real aplicou `Config/XUi_InGame/windows.xml` e inseriu exatamente um botão no cabeçalho da mochila.

[Saída filtrada da sonda](runtime-probe.txt). O teste XML não renderiza a interface nem simula clicar no botão dentro do cliente Steam.

## Reprodução

```sh
dotnet run --project sevendays-bau/tests/ServerAdapter/ServerAdapter.csproj -c Release
dotnet run --project sevendays-bau/tests/ClientAdapter/ClientAdapter.csproj -c Release
dotnet build sevendays-bau/tests/RuntimeProbe/RuntimeProbe.csproj -c Release -p:GameRoot="/caminho/servidor-oficial-3.4"
```

Use instalação descartável do servidor oficial. Instale a pasta completa do mod, incluindo `Config`, em `Mods/CraftingDosBaus`; coloque apenas a DLL da sonda em `Mods/ZZ_BausRuntimeProbe` com ModInfo válido. Configure `Empty`, save e UserDataFolder próprios, EAC desativado, `ServerVisibility=0`, senha e telnet/dashboard desativados. A sonda executa em `GameStartDone` e desliga o servidor. Nunca a instale em uma partida normal.

## Limites

Faltam UI visual, transporte real entre clientes Steam, latência real, sessões longas e compatibilidade com overhauls. Recibos/reservas são de sessão: quedas ou desconexões durante uma transferência não têm recuperação garantida. A versão 3.4 anuncia um problema próprio com bloqueios redefinidos ao fechar inventários; o mod respeita o estado atual exposto pelo jogo e não corrige esse problema.

## Antivírus

Os testes acima verificam funcionamento, não segurança antivírus. A retirada do pacote anterior e os resultados do Defender estão registrados em [SEGURANCA.md](SEGURANCA.md).
