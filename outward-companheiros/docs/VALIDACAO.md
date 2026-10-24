# Estado e teste real pendente

O binário é um plugin BepInEx, não um vídeo ou protótipo de navegador.
Não foi executado dentro de Outward neste ambiente. Precisamos validar o
comportamento da interface e do cooperativo na versão Steam instalada.

Em uma cópia do mundo/personagens, com o mesmo plugin nos dois PCs:

1. Solo em Chersonese: M mostra o personagem na posição correta; andar,
   girar, ampliar e arrastar o mapa mantêm o retrato alinhado.
2. Verificar as seis regiões exteriores, cidade com mapa, interior sem mapa,
   caído/revivido, mochila/nome com acentos e fechamento/reabertura do mapa.
3. Convidado entrando/saindo: os dois veem a mesma lista e os pontos dos dois,
   inclusive após uma mudança de cena. O nome VOCÊ corresponde ao observador.
4. Solo: conversar com caravana em cidade, listar destinos, recusar e tentar
   sem dinheiro/rações. Nenhuma cobrança em recusa/falta de recursos.
5. Solo: contratar uma rota inédita com 200 pratas e 3 rações; verificar o
   débito exato e a chegada. Repetir outra rota de volta. Os prazos avançam 3 dias.
6. Juntos: ambos perto do NPC e prontos. Testar uma contratação pelo anfitrião
   e outra pelo convidado; só o contratante paga, ambos chegam ao mesmo destino.
7. Um participante longe, carregando, sem condições de viajar ou em combate:
   as restrições nativas devem impedir a partida, sem débito.
8. Cierzo destruída/Vendavel fracassado, expulsão de Berg, golpe de Levant e
   Sirocco antes/depois da liberação: nenhuma rota para destino bloqueado.
9. Repetir com desconto de facção: as novas rotas continuam custando 200.
10. Desligar as duas opções separadamente e remover o plugin com o jogo
    fechado; verificar que os saves e demais mods continuam carregando.

Registrar versão do jogo, papel (anfitrião/convidado), cidade, reprodução e
`BepInEx/LogOutput.log` se algo falhar. Nenhum desses itens está marcado como
aprovado por compilação ou pelos testes de substitutos.
