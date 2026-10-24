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

## Busca 0.2.0: validação em interface real pendente

- Abrir inventário, baú de casa e baú de saque: barra alinhada, sem cobrir
  títulos, dinheiro, itens ou botões; verificar também outras resoluções.
- Buscar `pocao`, `POÇÃO` e duas palavras; verificar categorias, bolsos e mochila.
- Digitar letras que seriam atalhos de inventário/menu: não devem ativar ações.
  Enter sai do campo; × limpa; clicar fora devolve o controle aos itens.
- Nenhum resultado deve manter o baú aberto e o botão × acessível.
- Mover pilhas/itens entre baú e inventário com busca ativa e observar a
  atualização dos resultados; × deve restaurar a lista completa da categoria.
- Pegar tudo com busca ativa deve exibir a mensagem sem mover itens.
  Com campo limpo, deve funcionar normalmente.
- Fechar/reabrir e alternar baús: limpar consulta e restaurar altura da lista,
  sem acumular barras ou diminuir o painel a cada abertura.
- Os dois jogadores buscam termos diferentes ao mesmo tempo. Verificar que
  os termos e resultados ficam locais, com as transferências nativas preservadas.
- Desativar BuscaNoInventarioEBaus: lista completa e layout restaurados.

## Regressão relatada na 0.2.0 / correção 0.2.1

Relato do usuário: campo aparece e recebe texto no inventário, mas prende o
foco; trocar de aba permite sair, porém voltar não restaura a barra.

Na 0.2.1, testar primeiro no inventário:

1. Digitar, sair por Enter, Esc e OK; clicar fora e num item; cada saída deve
   soltar o teclado sem reativar a edição no quadro seguinte.
2. Clicar no botão nativo de fechar o inventário enquanto edita. Reabrir.
3. Trocar de aba enquanto edita e voltar ao inventário diversas vezes:
   barra presente, sem edição automática, sem redução cumulativa da lista.
4. Repetir em baú de casa e recipiente de saque. Essa parte não foi confirmada
   no jogo na versão anterior.

Há testes executáveis do estado de interação e da reconciliação prevista no
binário, mas os passos acima ainda exigem teste real do usuário.

Registrar versão do jogo, papel (anfitrião/convidado), cidade, reprodução e
`BepInEx/LogOutput.log` se algo falhar. Nenhum desses itens está marcado como
aprovado por compilação ou pelos testes de substitutos.

## Recompensas 0.3.0: validação real ainda pendente

Testes automatizados: mesmas funções Before/Finish do plugin, incluindo entrega
simulada para 1, 2 e 4 destinatários, anfitrião/instigador/todos, valores desconhecidos,
configuração desligada, ações reutilizadas, execução aninhada e restauração após
exceção. O alvo net472 executa HarmonyX de verdade em um substituto controlado.
Não simula fielmente Unity, inventário real, persistência ou tráfego Photon.

Na primeira próxima missão, com cópia dos saves e mod 0.3.0 nos dois PCs:

1. Ambos conectados e carregados. Anotar a prata e os itens antes da entrega.
2. Anfitrião entrega: conferir o acréscimo integral nos dois inventários,
   sem duplicação no anfitrião. Conferir notificação nativa no convidado.
3. Em outra recompensa, convidado inicia a conversa; conferir os dois.
4. Missão que concede habilidade/passiva: conferir aprendizado nos dois,
   inclusive se um já conhecia a habilidade. Restrições nativas permanecem.
5. Recompensa já coletiva: apenas uma entrega para cada personagem.
6. Salvar, sair e carregar: prêmio permanece, sem nova entrega ao reconectar.
7. Solo: uma recompensa normal. Compras de loja, saque de baú e transferências
   entre players não geram cópias para outra pessoa.
8. Desligar RecompensasParaTodos nos dois PCs: comportamento original.

Nenhum desses passos de jogo está marcado como aprovado automaticamente.
Não reexecute repetidamente uma entrega que falhou no meio: o mod não tenta
compensar uma transação parcialmente realizada pelo jogo. Para diagnóstico,
informar nome da missão, quem falou com o NPC e enviar os logs dos dois PCs.
