# Punhos da Lua Azul — Combate Lunar

Mod independente de artes marciais para Minecraft Java **26.2**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.2** e Java **25**. A instalação e os controles estão em [COMECE-AQUI.txt](COMECE-AQUI.txt).

As luvas compactas ativam três posturas, nove golpes leves encadeáveis, três golpes pesados, onda de choque, oito Cortes do Vazio, esquiva com rolamento, guarda, aparo com contra-ataque fortalecido, stamina, foco, postura dos inimigos e fixação de alvo. A câmera sobre o ombro mantém colisão com blocos; F5 continua disponível. Animações próprias usam interpolação por quadro para o modelo humanoide e para as luvas, em primeira e terceira pessoa. Os efeitos combinam geometria luminosa e partículas limitadas.

As decisões de combate, custos, recargas, alvos, linha de visão e dano são calculados pelo servidor. Um golpe não ganha dano extra com FPS alto ou repetição de pacotes. Os oito cortes usam um tipo de dano próprio que ignora apenas a janela de imunidade entre impactos; armadura continua funcionando. Nenhum golpe quebra blocos.

Inclui uma aba Criativa, receitas e o **Discípulo Lunar**, um adversário opcional com barra de chefe e a mesma família de movimentos. Não substitui a IA/animação de todas as criaturas do jogo. Jogadores aliados e animais domesticados são protegidos; PvP das luvas está desativado nesta versão. Com outro item, mão secundária ocupada, menus abertos ou montaria, os controles especiais não capturam as ações normais.

O código, as poses e as texturas são originais, sob MIT. Os efeitos sonoros referenciam eventos do próprio Minecraft, sem redistribuir gravações do jogo. Não há dependência de biblioteca de animação, serviço pago, IA, Nioh ou outro mod de combate.

## Desenvolvimento

Na raiz do repositório, com JDK 25:

```sh
./gradlew --no-daemon -p moonfist-mod build
./gradlew --no-daemon -p moonfist-mod runClientGameTest
python3 scripts/package-moonfist.py
```

O primeiro comando executa testes JUnit e testes reais do servidor. O segundo exige OpenGL/tela e executa um cliente Minecraft real; os testes não são distribuídos dentro do JAR instalável. `tools/generate_assets.py` regenera a arte e os dados com Pillow, usado somente no desenvolvimento.

Primeira versão experimental: o acabamento e o balanceamento ainda precisam de partidas humanas. Não foi validada com todos os mods, shaders, RTX 4060/Windows ou entre duas máquinas. Não misture com sistemas que substituem os mesmos controles/animações sem teste específico.
