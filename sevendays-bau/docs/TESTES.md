# Validação — 0.1.0 experimental

Realizada em 27/10/2026. Alvo: **7 Days to Die V3.4.0 (b6), Compatibility V3.4.0**. Versão confirmada na notícia oficial da Steam, nas constantes do assembly e no servidor Linux em execução.

## Resultados

| Camada | Resultado | O que realmente foi executado |
| --- | --- | --- |
| Build .NET Framework 4.8 | 0 erros / 0 avisos | Referências do servidor oficial 3.4.0, não stubs |
| ServerAdapter | 1.681 verificações aprovadas | Código de produção `Server.cs`, `Network.cs`, `WithdrawalPlan.cs` com substitutos controlados do mundo/API |
| RuntimeProbe | 134 verificações aprovadas | DLL final e Harmony dentro do servidor Linux oficial 3.4.0 (b6), sem interface gráfica |
| ZIP | Estrutura e CRC verificados | Somente DLL própria, configuração, manifesto, guia e licença |
| Clientes Steam em cooperativo | **Não realizado** | Não havia clientes/jogadores disponíveis neste ambiente |

O teste automatizado de concorrência não é uma partida multiplayer. O teste nativo confirma métodos do jogo e formato dos pacotes, mas não exercita a transmissão entre dois clientes, a interface gráfica completa, a fila com um personagem real nem a fundição ao longo do tempo.

## ServerAdapter

- Retirada exata, ingredientes de mais de um baú, prioridade de proximidade.
- Falta de um ingrediente impede a alteração dos demais.
- 100 rodadas com solicitações concorrentes pelo último material: um único vencedor.
- Repetição do mesmo recibo, confirmação e tentativa de replay sem novo débito.
- Alcance de 50 m inclusivo, distância vertical e valores inválidos.
- Exclusão de baús abertos/ocupados, privados sem acesso, slots bloqueados, saque do mundo e equipamentos modificados.
- Permissão do proprietário e rejeição de pacote com identidade de outro jogador.
- Reversão quando a notificação do armazenamento falha, inclusive com falha persistente.
- 1.000 inventários aleatórios comparados com um cálculo independente de disponibilidade.
- Codificação/decodificação da classe de pacote com codec de ItemStack substituído nessa camada.

## RuntimeProbe

Iniciou o servidor oficial no mundo `Empty`, sem jogadores, com listagem pública, dashboard, telnet e EAC desativados, senha aleatória e saves isolados. A sonda de testes **não integra o ZIP entregue**.

- Todos os patches Harmony instalaram sem erro, inclusive a inserção no cálculo de quantidade máxima.
- O registro nativo encontrou `NetPackageCraftingDosBaus` e atribuiu um ID.
- 20 combinações de saldo local/remoto executaram os métodos reais `XUiM_PlayerInventory.HasItems` e `RemoveItems`: contagem sem interferência fora do crafting, débito completo de mochila e saldo remoto e lista real de materiais para cancelamento.
- Métodos reais `XUiC_WorkstationInputGrid.HasItems/DecItem/RemoveItems`, com apenas a camada visual/backend substituída, consumiram exatamente a soma autorizada.
- Planejamento da forja usou IDs, categorias e pesos reais de ferro: quantidade correta de matéria-prima, sem modificar o snapshot, sem pedir minério já carregado e sem retirar quando os slots estavam cheios.
- Pacote serializado com `PooledBinaryWriter`, `PooledBinaryReader` e `ItemStack` reais: ID, operação, destinatário, recibo e materiais preservados.

[Saída filtrada dos testes nativos](runtime-probe.txt). Somente mensagens da sonda e versão; sem logs de conta, senha, IDs de plataforma ou arquivos proprietários.

## Reprodução

```sh
dotnet run --project sevendays-bau/tests/ServerAdapter/ServerAdapter.csproj -c Release
dotnet build sevendays-bau/tests/RuntimeProbe/RuntimeProbe.csproj -c Release -p:GameRoot="/caminho/servidor-oficial-3.4"
```

Para a sonda, use uma instalação **descartável e isolada** do servidor oficial. Instale a DLL principal com seu ModInfo/config em `Mods/CraftingDosBaus`; coloque apenas `CraftingDosBaus.RuntimeProbe.dll` em `Mods/ZZ_BausRuntimeProbe` com um `ModInfo.xml` válido. A sonda executa em `GameStartDone` e desliga o servidor ao terminar. Configure mundo `Empty`, nome/save próprios, `EACEnabled=false`, `ServerVisibility=0`, senha, telnet/dashboard desativados e UserDataFolder isolado. A sonda não deve ser usada em partidas ou servidores de usuários.

## Limites conhecidos

Ainda é necessária validação visual no cliente Steam e cooperativo real, incluindo fila cheia, cancelamento, saída de alcance, latência, troca de janela, sessões longas e interação com outros mods. Recibos e saldos são de sessão, não persistentes: quedas ou desconexões durante a transferência não têm recuperação garantida. Não há promessa de compatibilidade com futuras versões, consoles, EAC ativado ou mods de overhaul.

O jogo 3.4 também anuncia um problema próprio com bloqueios de slots sendo redefinidos ao fechar o inventário. O mod respeita o estado atual que o servidor expõe; ele não corrige esse problema do jogo.
