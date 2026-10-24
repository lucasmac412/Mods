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
