# Bibliotecas de renderização — etapa de desenvolvimento

**O Create completo continua sem compilar e não está jogável. Não há pacote de instalação.** Os JARs desta etapa são dependências locais usadas nos testes, não um Create alternativo. Minecraft Java 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2 e JDK 25 são os alvos mantidos.

## Implementação e evidências

Flywheel foi adaptado do commit `a6f934dc348c69d084ced62d634b843650e2702c`, do repositório Engine-Room/Flywheel. Ponder/Catnip vieram do commit `48800ff32435cad03e5f563f17c0b21d2e313d20`, de Creators-of-Create/Ponder. Ambos os fontes são para 26.1.2 e têm licença MIT. [library-sources.json](library-sources.json) fixa os arquivos, checksums e arquivos modificados; [patches](patches) preserva as alterações de código e shaders.

- Flywheel: formato de vértices do 26.2, framebuffer, mistura por destino, upload RGBA do ruído, profundidade invertida, redução/oclusão por profundidade e conversão de profundidade da transparência. Extração do mundo/reconstrução do terreno passaram para `LevelExtractor`. O desenho acontece na execução do passe principal, depois da limpeza da imagem. Desenhar na preparação das entidades fazia o objeto desaparecer, apesar de continuar animando.
- Catnip/Ponder: buffers imediatos usam a GPU do 26.2; prévias de blocos, fluidos e entidades usam a API de submissão. Renderizações imediatas de itens/partículas têm um despachante separado, evitando iniciar outro quadro no despachante ocupado do mundo. As partículas são desenhadas antes de limpar o estado mutável e remover a transformação da cena.
- Configurações: o registro Fabric original lançava `NYI`. A implementação usa Forge Config API Port v5, valida os valores e mantém leitura/gravação TOML e eventos de carregamento. A inicialização dos valores foi corrigida para não exigir que já estivessem carregados.
- Comandos: os três tipos de argumentos agora têm registro de rede. Configuração e caminho possuem análise real, com rejeição de entradas inválidas. Antes, entrar no mundo desconectava o jogador por tipo de argumento desconhecido.
- Entidades virtuais: recebem IDs próprios, sem entrar no mundo real. Foi corrigida também a restauração que acrescentava cópias à lista de backup durante sua própria iteração. Mixins do cliente permanecem exclusivos do cliente.

Capturas reais: [Flywheel indirect](reports/flywheel-indirect-26.2.png), [Flywheel instancing](reports/flywheel-instancing-26.2.png), [item sobreposto ao mundo](reports/catnip-item-overlay.png), [coordenadas](reports/ponder-scene-1.png), [fluidos](reports/ponder-scene-3.png), [partículas](reports/ponder-scene-5.png) e [papagaio](reports/ponder-scene-7.png). Os cubos flutuantes e a espada são objetos do teste, não máquinas do Create. As cenas internas de depuração podem exibir chaves de tradução.

O teste gráfico exige que uma instância seja criada, animada, reconstruída ao mudar de backend e removida; não aceita backend desligado ou fallback. Uma verificação independente das imagens exige o cubo dourado nos dois backends e partículas de lava na cena correspondente. As imagens também foram inspecionadas. O servidor testa serialização dos argumentos, rejeição de entradas inválidas e isolamento/identificação das entidades virtuais, além dos testes anteriores de leite e configuração.

Isso não valida todas as funções dessas bibliotecas, integração com shaders/Sodium/Iris, desempenho no Windows/RTX 4060 nem máquinas, esforço, logística, estruturas móveis ou trens do Create.

## Reproduzir na nuvem

Comandos de desenvolvimento, não de instalação pelo jogador. Ative o JDK 25 e `GRADLE_USER_HOME` do ambiente. O preparador verifica os hashes, rejeita caminhos inseguros e preserva diretórios existentes com alterações manuais. Use outro `--output` ao atualizar um patch. Os diretórios abaixo contêm a reconstrução final testada.

```bash
python3 -m unittest discover -s create-port/tools -p 'test_*.py'
python3 create-port/tools/fetch_libraries.py
python3 create-port/tools/prepare_render_libraries.py flywheel \
  --cache /workspace/.cache/create-port --output /workspace/.cache/create-port/flywheel-26.2-verified
python3 create-port/tools/prepare_render_libraries.py ponder \
  --cache /workspace/.cache/create-port --output /workspace/.cache/create-port/ponder-26.2-verified-v2
./gradlew --no-daemon -p /workspace/.cache/create-port/flywheel-26.2-verified jar
./gradlew --no-daemon -p /workspace/.cache/create-port/ponder-26.2-verified-v2 :catnip:jar :ponder:jar

./gradlew --no-daemon -p create-port/integration-probe \
  -PcreateMilkJar=/workspace/.cache/create-port/milk-patched/build/libs/milk-lib-1.2.1+createport.1.jar \
  -PcreatePonderDir=/workspace/.cache/create-port/ponder-26.2-verified-v2 runGameTest

./gradlew --no-daemon -p create-port/render-integration-probe \
  -PcreateFlywheelJar=/workspace/.cache/create-port/flywheel-26.2-verified/build/libs/flywheel-fabric-1.0.6+mc26.2.createport.1.jar \
  -PcreatePonderDir=/workspace/.cache/create-port/ponder-26.2-verified-v2 runClientGameTest
python3 create-port/tools/verify_render_captures.py create-port/render-integration-probe/build/reports
```

O teste gráfico precisa de tela OpenGL. Foi usado Xorg `:99` com `/workspace/.cache/celestial-dragon/display/xorg.conf`, `DISPLAY=:99`, `LIBGL_ALWAYS_SOFTWARE=1` e `MESA_SHADER_CACHE_DIR=/workspace/.cache/celestial-dragon/mesa`. `ALSOFT_DRIVERS=null` é permitido neste cenário sem verificação de áudio; não use isso para os testes sonoros do dragão. Nenhum processo precisa ficar ligado depois dos testes.

A preparação anterior da Milk é descrita no [README](README.md). `prepare.py` aceita `--library-jar` repetido para incluir os três JARs de renderização na migração de nomes. O projeto `probe` recebe `-PcreateFlywheelJar`, `-PcreateCatnipJar` e `-PcreatePonderJar` para compilar contra eles. Esse comando ainda **falha**; consulte [o diagnóstico final](reports/render-create-compilation.json). Nenhuma tarefa de empacotamento do Create foi habilitada.
