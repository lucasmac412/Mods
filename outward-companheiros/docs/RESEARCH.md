# Evidência e escolhas

- Referências oficiais da comunidade DE: `Outward.GameLibs 2022.5.18-r.0`,
  pacote de desenvolvimento público no catálogo `nuget.bepinex.dev`.
  São assemblies de referência, com implementações removidas (`throw null`),
  e não substituem o jogo. Usadas somente para compilar e ler metadados.
- [OutwardModTemplate](https://github.com/Mefino/OutwardModTemplate/tree/ed6dbcde266556a2a2930d74f223bab98129ec0b):
  combinação conhecida de net472, Unity 2020.3.26 e GameLibs DE.
- [More Map Details](https://github.com/Aestras/Outward-Mods/tree/1aea908927ee73c59ccd75830781cb736e2cadc7/MoreMapDetails/src):
  API `MapWorldMarker.CalculateMapPosition`, projeção regional e seis
  calibrações reutilizadas sob MIT. Licença integral preservada.
  Nenhuma DLL desse mod foi incluída. Retratos e overlay são originais.
- [Instalação Mono DE](https://github.com/cybensis/OutwardVR#installation):
  confirma `default-mono` e a pasta `Outward_Defed`. Não usamos o mod VR.
- `BepInExPack_Outward 5.4.19`, ZIP publicado pelo mantenedor BepInEx no
  Thunderstore, foi inspecionado: traz o `mono_fix` e configura o Doorstop
  com `dllSearchPathOverride=mono_fix`. O guia indica esse pacote específico,
  não o BepInEx genérico sem as bibliotecas de Reflection.Emit do Outward.
- Comparação local com uma assembly legada disponível publicamente em
  `lasyan3/OutwardMods` ajudou a entender a caravana, sem copiar código do jogo
  ou desse autor para o plugin. O legado não prova o comportamento DE atual.
  A assinatura equivalente foi conferida nos metadados DE.

## Integração

Os encaixes usados são `MapDisplay.Show(CharacterUI)`, `OnHide`,
`UpdateWorldMarkers`, `MerchantFastTravel.GetAvailableDestinationData` e
`TravelData.GetRealSilverCost`. Membros privados são consultados por reflexão
e validados no carregamento. Não acessamos diretamente campos publicizados
que seriam privados no jogo instalado.

Os marcadores não entram no conjunto limitado de exibições do mapa nativo:
probes desabilitados calculam as coordenadas e a UI própria desenha os PNGs.
Usamos os seis ajustes regionais conhecidos e a calibração nativa nos demais
mapas, sem alterar os assets de calibração compartilhados com outros mods.
Os nomes usam `supportRichText=false`, tamanho limitado e sem controles.
Players vêm de `Global.Lobby.PlayersInLobby`, nunca da lista de todos os NPCs.
Objetos de rede persistentes em DontDestroyOnLoad são aceitos; não exigimos
que a cena Unity do personagem seja igual à cena ativa.

A nova lista do diálogo contém objetos `TravelData` independentes; o conjunto
estático nativo não é alterado. Cada participante produz a lista local com
os eventos da campanha. A sincronização de um único destino da caravana
não é usada para distribuir as opções adicionais. O embarque permanece
com as verificações/RPCs originais. Não enviamos RPCs manualmente nem fazemos
débitos locais por participante. `GetRealSilverCost` só é ajustado para
objetos criados por este mod (ConditionalWeakTable), preservando outras viagens.

Ficam pendentes as validações da UI e das viagens dentro do executável Steam,
especialmente início pelo convidado, transição e atualização dos eventos.

## Busca 0.2.0

As assinaturas DE de `InventoryContentDisplay`, `StashPanel`,
`EnvironmentItemDisplay`, `ContainerDisplay` e `ItemListDisplay` foram
conferidas nas referências publicizadas. O fluxo legado ajudou a identificar
que `ItemListDisplay.MatchFilter(Item)` é consultado tanto nos itens novos
quanto nos displays reaproveitados. Nenhuma implementação do jogo foi copiada.

O postfix compõe o resultado nativo com a consulta local da lista. O mod não
remove UIDs de inventários nem substitui arrays de itens. A atualização usa
`ContainerDisplay.Refresh()` sobre o recipiente real, incluindo alterações
que chegam pelo cooperativo. A busca compõe filtros de categoria e mantém
pilhas, ordenação, peso, dinheiro e movimentação no código nativo.

Há uma barra para bolsos/mochila, outra para o baú de casa e outra para o
painel de recipientes/saque. As consultas são por instância de painel. As
listas/queries não são sincronizadas entre jogadores. Os nomes vêm de
`Item.Name`, no idioma instalado. Há debounce de 70 ms, limite de 64 caracteres
e cache limitado; a comparação é ordinal após remoção de acentos e caixa.

A UI reserva 57 unidades do topo do viewport e restaura o offset ao fechar.
Usa InputField real, a fonte do painel, fundo escuro e moldura dourada, com
limpar e contagem de ícones/grupos. O foco passa pelo CharacterUI do dono.

`Pegar tudo` nativo ignora filtros de apresentação, então essa ação é bloqueada
nas listas com consulta ativa com uma mensagem para limpar a busca. O mod
também preserva `EnvironmentItemDisplay.IsEmpty=false` se há itens reais
ocultos pela pesquisa, evitando interpretar zero resultados como recipiente
fisicamente vazio. Não reimplementa transferência ou RPCs.

O alinhamento real, o foco e o comportamento dos controles continuam pendentes
de validação dentro do executável Steam, conforme VALIDACAO.md.

## Correção 0.2.1 a partir do relato no jogo

Na 0.2.0 o usuário conseguiu clicar e escrever no inventário, mas não sair
do campo ou fechar o inventário normalmente. Trocar de aba o liberava, porém
a barra não reaparecia. Baús ainda não haviam sido testados por ele.

O código tinha dois problemas correspondentes: veto a seleção de itens e
getters forçados de captura de texto, além de Tick que só atualizava barras
já ativas, sem reexibir as ocultadas. A 0.2.1 remove esses patches. Não veta
CharacterUI.SetSelectedGameObject nem força IgnoreMenuInputs/IsInputFieldFocused.

A saída é explícita por Enter, Esc, clique fora, OK e ×. Cancela a ativação
pendente do InputField e limpa os flags nativos somente quando pertencem à
busca; outros campos de texto são preservados. Esses membros de UI são
consultados/escritos por reflexão, sem acesso direto a membros publicizados.
Há proteção contra reentrada de onEndEdit e liberação de foco ao ocultar.

OnEnable registra os painéis. Tick reconcilia a visibilidade dos donos,
inclusive quando a volta à aba não chama Show. Mostrar/ocultar é idempotente,
e um erro num painel tem nova tentativa, em vez de desabilitar toda a busca.
Refresh não roda no fechamento de painéis em transição; roda ao voltar com
listas inicializadas. SearchInteractionState é usado no runtime e nos testes
de saída/retorno e reserva/restauração da margem.

Os testes e inspeção de IL verificam esses caminhos e a remoção dos patches
anteriores. Não reproduzem uma sessão real de Unity/Outward neste ambiente.
