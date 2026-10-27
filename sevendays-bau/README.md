# Crafting dos Baús e Guardar Semelhantes — 7 Days to Die 3.4

Versão **0.3.0 experimental**, para **V3.4.0 (b6)**. Fabricação do personagem e bancadas usam ingredientes dos baús até **50 metros**, **exceto forja e betoneira**, que mantêm contagem e consumo originais. Bancada, fogueira e estação de química são permitidas; combustível, ferramentas e tempos continuam nativos.

**A versão 0.2.0 foi retirada após um alerta do Defender.** Consulte o [relatório de segurança](docs/SEGURANCA.md). Não ignore alertas nem crie exclusões.

O novo botão **Guardar nos baús**, no cabeçalho do inventário, guarda os itens da mochila **e da barra rápida** em baús próximos que já contenham aquele tipo. Respeita espaço, bloqueios, permissões e atributos dos itens. Não envia itens para baús vazios ou de outras categorias.

[Instalação e uso](LEIA-ME.txt) · [Relatório de testes](docs/TESTES.md)

Todos os participantes e o anfitrião/servidor devem atualizar a pasta completa do mod para 0.3.0, incluindo `Config`. Requer EAC desativado e usa o Harmony fornecido pelo jogo. Não foi validado com clientes Steam reais; não há recuperação persistente de transferências interrompidas por queda/desconexão.

## Funcionamento

- Baús de jogadores, carregados, fechados e acessíveis, dentro de uma esfera de 50 m do jogador. Alcance configurável no servidor.
- Fabricação remota habilitada por contexto de janela. `CraftingPolicy` exclui IDs de forja e betoneira; janelas com grade de fundição também são excluídas. Hooks compartilhados de inventário verificam o contexto antes de incluir ou consumir saldo remoto. Não há abastecimento da forja.
- Depósito preenche pilhas compatíveis antes de ocupar espaços livres nos baús correspondentes, priorizando proximidade. Só usa tipos presentes antes do pedido; não espalha categorias novas para outros baús.
- Slots bloqueados são ignorados na origem e destino. Armaduras equipadas e itens no cursor ficam de fora. Armas da barra rápida podem ser guardadas se houver correspondência; use slots bloqueados da mochila para protegê-las.
- Qualidade, desgaste, modificações e outros atributos são preservados. Pilhas só são combinadas se os valores completos dos itens forem iguais e houver capacidade.
- Retirada e depósito compartilham a mesma exclusão mútua no servidor. Solicitações usam IDs por jogador e recibos para impedir aplicação repetida.
- Antes de depositar, o cliente consulta os tipos disponíveis, reserva os slots exatos uma vez e reutiliza essa reserva nas tentativas. A resposta devolve apenas o excedente, preferindo os slots originais. Se eles forem ocupados durante a espera, tenta o inventário e então o chão com aviso.
- Uma transferência por cliente de cada vez; até 128 pilhas por clique. Filtros visuais não limitam a seleção de slots.

## Compilar e testar

Obtenha legalmente o servidor dedicado oficial 3.4 pelo Steam/SteamCMD (app 294420). DLLs do jogo são referências locais e **não são distribuídas** aqui. Com SDK .NET 8:

```sh
dotnet build sevendays-bau/src/CraftingDosBaus.csproj -c Release -p:GameRoot="/caminho/servidor"
dotnet run --project sevendays-bau/tests/ServerAdapter/ServerAdapter.csproj -c Release
dotnet run --project sevendays-bau/tests/ClientAdapter/ClientAdapter.csproj -c Release
python3 sevendays-bau/tools/package.py
```

`GameRoot` contém `7DaysToDieServer_Data/Managed` e `Mods/0_TFP_Harmony/0Harmony.dll`. Alvo do plugin: .NET Framework 4.8. Para executar a sonda no servidor oficial, veja o relatório. A DLL da sonda não deve ser instalada em partidas normais.

## Limites de consistência

Recibos ficam em memória durante a sessão, limitados a 20.000 por operação. A confirmação gera um tombstone que impede reaplicação. O cliente valida que qualquer devolução é subconjunto exato dos itens reservados. Não há transação distribuída durável entre os saves do baú e personagem: quedas/desconexões durante uma transferência podem perder itens. Aguarde a conclusão antes de sair. O servidor revalida acesso, distância, espaço e conteúdo em cada novo pedido.
