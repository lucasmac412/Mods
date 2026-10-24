# Integração com Nioh 1: evidências e lacunas

Escopo solicitado: Nioh: Complete Edition da Steam no Windows, teclado/mouse, categoria adicional de luvas compactas com movimentos originais, três posturas, ondas de choque, bastante brilho e especial no Caps Lock inspirado em cortes espaciais. O usuário informou que não usa outros mods e não sabe a versão instalada.

## Referências consultadas

- [Nioh-Modding-and-Research/Nioh-Mod-Loader](https://github.com/Nioh-Modding-and-Research/Nioh-Mod-Loader): o projeto declara substituição de arquivos soltos por hash para Nioh 1 e 2. Isso é evidência de acesso/substituição de recursos; não demonstra registro de uma categoria adicional nem importação de animações novas. O código não foi incorporado ao protótipo.
- [RobertTausig/Nioh1Fix](https://github.com/RobertTausig/Nioh1Fix): documenta Steam 1.24.8.0 e alterações de timing/animação. O próprio README registra limitações na versão Windows. Não foi instalado, redistribuído ou utilizado como prova de compatibilidade das luvas.
- [lylarcher/Nioh1PerfectGuard](https://github.com/lylarcher/Nioh1PerfectGuard): documenta combate e hooks para Steam 1.24.8 e que a imagem de código em disco pode diferir da imagem carregada. Isso motivou hashes separados no inspetor. Nenhum hook, endereço ou implementação desse projeto foi copiado. Sua licença própria não foi aplicada ao código original deste diretório.
- [OIRANGEISHA/Nioh-HinoEnma](https://github.com/OIRANGEISHA/Nioh-HinoEnma): o README declara uma personagem controlável em Steam 1.24.08 e lista várias limitações de ações/itens. Isso mostra trabalho público de modificação de comportamento, sem provar que o inventário suporta uma categoria adicional. Não foram redistribuídos nem adaptados seus fontes/CTs/patches ou recursos.

As referências são informação de terceiros, não resultados verificados neste ambiente. Não há uma cópia do Nioh disponível aqui, nem uma sessão do jogo para validar endereços, tipos, chamadas nativas e assets. O inspetor identifica a instalação do usuário; não usa endereços coletados de outra versão como se estivessem validados.

## O que ainda bloqueia uma categoria jogável

1. Identificar executável/versão e o arranjo dos arquivos da instalação real. O relatório pequeno evita pedir inicialmente uma cópia inteira do jogo ou um dump de memória.
2. Identificar os recursos de equipamento, a representação de categoria e seus limites no inventário/UI, sem reutilizar uma categoria existente como substituição.
3. Identificar formato, rig e carregamento de animações. glTF/GLB é apenas o formato de autoria do protótipo; não há conversor validado para a estrutura exata desse Nioh nesta entrega.
4. Identificar um ponto de atualização seguro do jogador e os contratos de entrada, dano/colisão, Ki, efeitos e cancelamentos. Eventos da simulação não chamam o motor do jogo.
5. Validar cada ligação na instalação correspondente, depois testar golpes e transições reais. Um hash de versão, parser aprovado ou executor de prévia não prova que essas ligações funcionam.

Não há ainda evidência suficiente para garantir que a categoria totalmente adicional seja possível nessa versão. As decisões de animação, custo, alcance e aparência do protótipo são revisáveis; seus valores não foram apresentados como IDs, unidades de dano ou distâncias do motor do Nioh.

## Limites do material de autoria

O GLB contém um manequim articulado simplificado e luvas em formato de punho fechado, com juntas luminosas. Não possui o esqueleto de William, pesos de pele nativos, colisores, grafo de animação, root motion compatível, dados de equipamento ou formato G1M/G1A validado. Sete clips originais foram produzidos para ensaiar gestos e tempos; não equivalem a sete animações já importadas no jogo. A prévia usa movimentos procedurais próprios baseados nos mesmos tempos de impacto, não é um vídeo capturado do Nioh.
