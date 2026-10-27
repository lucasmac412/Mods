# Crafting dos Baús — 7 Days to Die 3.4

Ingredientes de baús até **50 metros do jogador** para fabricação pessoal e estações, com retirada validada no servidor. Todos os participantes e o anfitrião/servidor precisam instalar. Requer EAC desativado; usa o Harmony distribuído pelo próprio jogo.

[Guia de instalação e uso](LEIA-ME.txt) · [Testes e limites](docs/TESTES.md)

Pacote inicial **0.1.0 experimental**, compilado contra **V3.4.0 (b6)** oficial. Não foi testado com clientes Steam reais. Não há garantia de recuperação de transferências interrompidas por queda ou desconexão.

## Comportamento

- Busca somente recipientes de jogadores, carregados, fechados e acessíveis; respeita bloqueios dos slots e baús privados.
- Usa os mais próximos primeiro; distância 3D inclusiva de 50 m, configurável no servidor.
- Confere todos os ingredientes antes da retirada, serializa solicitações concorrentes e usa recibos por jogador/pedido para evitar débito repetido.
- A contagem remota só participa de consultas de fabricação. O snapshot da interface não autoriza consumo; a fila nativa é acionada depois da resposta do servidor.
- Mantém valores reais dos itens consumidos para cancelamento. Itens com modificações instaladas são excluídos.
- Na forja, carrega matéria-prima nos três slots de entrada. Fundição e combustível continuam necessários; o jogador fabrica novamente após fundir.
- Não altera reparos, compras, upgrades ou recargas. As regras nativas de receita, custo, ferramenta e duração continuam em vigor.

## Compilar

Obtenha legalmente o servidor dedicado oficial 3.4 pelo Steam/SteamCMD (app 294420). Suas DLLs são referências locais e **não fazem parte deste repositório/pacote**. Com SDK .NET 8 instalado:

```sh
dotnet build sevendays-bau/src/CraftingDosBaus.csproj -c Release -p:GameRoot="/caminho/servidor"
dotnet run --project sevendays-bau/tests/ServerAdapter/ServerAdapter.csproj -c Release
python3 sevendays-bau/tools/package.py
```

`GameRoot` deve conter `7DaysToDieServer_Data/Managed` e `Mods/0_TFP_Harmony/0Harmony.dll`. O alvo do mod é .NET Framework 4.8; as referências de compilação são restauradas via NuGet. Para a sonda nativa de testes, veja o relatório. Nunca instale a DLL da sonda na partida normal.

## Limites de consistência

Recibos são mantidos em memória durante a sessão, com limite de 20.000 pedidos. Uma repetição pendente recebe o mesmo resultado; um recibo confirmado não debita de novo. Não há transação distribuída durável entre o save do baú e o save do personagem: uma queda/desconexão durante a confirmação pode perder ingredientes. O cache de interface expira em três segundos. O servidor revalida acesso, alcance e quantidade no momento da retirada.
