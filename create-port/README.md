# Create no Fabric 26.2 — migração em desenvolvimento

**Ainda não há Create instalável ou jogável nesta pasta.** O alvo pedido é Create 6.0.10, Minecraft Java 26.2 e Fabric Loader 0.19.5. Os projetos de diagnóstico desabilitam a geração de JARs para impedir que sejam confundidos com o mod pronto.

O arquivo `create-1.21.1-6.0.10.jar` enviado pelo jogador é a distribuição **NeoForge para Minecraft 1.21.1**. Seu SHA-512 coincide com o arquivo publicado oficialmente no Modrinth. Não contém `fabric.mod.json`; exige NeoForge 21.1.219 ou superior. Os metadados e as origens estão em [PROVENANCE.json](PROVENANCE.json).

O jogador confirmou que quer **somente Fabric 26.2**. Não foi preparada uma instalação em outra versão do jogo ou outro loader.

## O que avançou

- Conferência do JAR, das licenças, dos repositórios oficiais e das dependências.
- Ferramenta reproduzível para obter a referência Fabric com commit e checksum fixos, extrair apenas Java e preparar uma compilação contra o Minecraft 26.2 real.
- Primeira migração de nomes e pacotes em **381 arquivos Java**, incluindo `ResourceLocation` → `Identifier`, `Util` e tipos movidos de entidades/renderização. Isso é uma etapa mecânica; **não significa que 381 funcionalidades foram portadas**. Contratos de métodos e comportamento continuam sem validação no Create completo.
- Adaptador dos quatro usos antigos da API de leite do Create para os objetos reais de Milk Lib 26.2.
- Correção de um crash real da Milk Lib 26.2 em servidor: fábricas de partículas e o callback de tooltip foram separados do código comum. Os registros e identificadores das partículas continuam comuns a cliente e servidor.
- Testes reais das duas dependências disponíveis: fluidos, tags e transações de leite, configuração de rotação e fábricas de partículas no cliente.
- **Ports locais de Flywheel e Ponder/Catnip para Fabric 26.2**, a partir dos fontes Fabric 26.1.2, com patches reproduzíveis. O cliente testou instâncias animadas sólidas e transparentes nos backends `indirect` e `instancing`, remoção das instâncias, um item sobreposto ao mundo e quatro cenas do Ponder. Isso ainda não é o Create funcionando.
- Correções reais nas bibliotecas: ordem de renderização/profundidade invertida do 26.2, upload da textura de ruído, armazenamento das configurações, sincronização dos argumentos de comandos, identificação das entidades virtuais e execução das partículas antes da limpeza dos dados. Uma checagem das capturas impede considerar um teste de animação suficiente quando o objeto desaparece da imagem.

A referência Fabric encontrada é um desenvolvimento de **Create 6.0.0 para 1.21.1**, não uma conversão já pronta do 6.0.10 enviado. Ainda será necessário integrar as mudanças do 6.0.10, além de portar as dependências e as APIs do jogo.

## Dependências verificadas em 21/10/2026

| Componente | Situação para Fabric 26.2 nas fontes consultadas |
| --- | --- |
| Fabric Loader / Fabric API | 0.19.5 / 0.161.0+26.2, usados nos testes. |
| Forge Config API Port | 26.2.1: configuração carregada e validação de faixa testadas no servidor. O registro usa a API v5. |
| Milk Lib | 1.2.1+26.2 disponível, mas com API/ID diferentes e crash de servidor. A correção local `1.2.1+createport.1` e o adaptador passaram nos testes abaixo. |
| Flywheel | Sem publicação oficial 26.2 nas fontes consultadas. Port local de desenvolvimento compila e passou no cenário gráfico dos dois backends. |
| Ponder / Catnip | Sem publicação oficial 26.2 nas fontes consultadas. Ports locais compilam; cenas, configuração e integração cliente/servidor foram testadas. Ainda não integrados ao Create completo. |
| Registrate Fabric | O Maven consultado lista versões até Minecraft 1.21.1; nenhuma para 26.2. |
| Porting Lib | Nenhuma versão 26.2 encontrada no histórico do Modrinth consultado. A referência Fabric ainda importa esses módulos. |

Não encontrar uma publicação nessas fontes não prova que inexiste um fork privado ou um projeto com outro nome. Os resultados das consultas estão em [reports/dependency-availability.json](reports/dependency-availability.json) e [reports/extra-dependencies.json](reports/extra-dependencies.json).

## Resultado dos testes

**Create completo: não compila e não foi iniciado no jogo.** Foram feitas compilações de diagnóstico antes e depois da migração de nomes, e outra incluindo as duas bibliotecas 26.2, o adaptador e as dependências de configuração. Continuam faltando bibliotecas essenciais e há APIs removidas ou alteradas. O limite de 10.000 diagnósticos do compilador foi atingido; esse número **não é uma contagem de defeitos independentes**, porque dependências ausentes provocam erros em cascata. Veja [reports/compilation.json](reports/compilation.json).

**Pré-requisitos isolados: passaram.** Não são testes de máquinas, engrenagens, esteiras, fábricas ou trens do Create:

- Três testes Python de segurança da transformação do código, preservando comentários, literais e tipos aninhados.
- Três GameTests em um servidor real Minecraft 26.2, mais um controle interno do Fabric: registro/tag dos fluidos, inserção/extração com commit e rollback e carregamento/correção da configuração de rotação.
- Um cenário em cliente real Minecraft 26.2: mundo com leite renderizado e criação de partículas pelas seis fábricas de cliente, sem remover os registros comuns. Renderização por software na nuvem; não é teste de FPS no computador do jogador.

**Nova etapa de renderização:** cinco testes Python; seis GameTests de servidor (cinco nossos e um controle do Fabric); um cenário gráfico com dois backends do Flywheel, sobreposição de item e quatro cenas reais do Ponder. As capturas e verificações de pixels mostram geometria e partículas presentes, além de verificar a execução do código. Os fontes foram reconstruídos em diretórios novos usando os patches salvos. Detalhes, limitações e reprodução em [RENDERIZACAO.md](RENDERIZACAO.md) e [reports/render-ports.json](reports/render-ports.json).

Incluindo esses ports e a migração dos pacotes das bibliotecas em 1.083 arquivos Java do Create, **a compilação do Create continua falhando no limite de 10.000 diagnósticos**. Não houve inicialização do Create completo nem teste de suas máquinas. O limite continua sem representar uma quantidade de defeitos independentes. Veja [reports/render-create-compilation.json](reports/render-create-compilation.json).

Antes da correção, a Milk publicada encerrava o servidor com `Cannot load class net.minecraft.client.particle.SpriteSet in environment type SERVER`. A correção mantém as lambdas que recebem `SpriteSet` numa classe exclusiva do cliente; anotar só o método original não removia os métodos sintéticos gerados por Java.

Relatórios: [correção da Milk](reports/milk-server-fix.json), [integração](reports/integration.json) e [captura real do pré-requisito](reports/milk-26.2-client.png). A captura mostra Milk Lib, **não o Create funcionando**.

## O que falta para haver um mod instalável

É necessário portar Registrate e os módulos usados de Porting Lib; integrar os ports de renderização ao Create e as mudanças do Create 6.0.10; adaptar registro de blocos/itens, dados salvos, receitas, rede, mixins, permissões de acesso e renderização das próprias máquinas ao 26.2. Depois disso, testar máquinas, redes de rotação e esforço, contraptions, fluidos, logística e trens, além de persistência e cliente/servidor.

Os patches atuais não resolvem essas etapas. Não há pacote `Create-Fabric-26.2.zip`, instalador nem JAR do Create para copiar à pasta `mods`.

## Reproduzir o trabalho de desenvolvimento

Estes comandos são para desenvolvimento, não para instalação pelo jogador. Use JDK 25, o wrapper da raiz e o ambiente de desenvolvimento já configurado. Os downloads têm versões e checksums fixados. Não há serviço pago.

```bash
python3 -m unittest discover -s create-port/tools -p 'test_*.py'
python3 create-port/tools/fetch_libraries.py
python3 create-port/tools/prepare_milk.py --cache /workspace/.cache/create-port
./gradlew --no-daemon -p /workspace/.cache/create-port/milk-patched jar

./gradlew --no-daemon -p create-port/integration-probe \
  -PcreateMilkJar=/workspace/.cache/create-port/milk-patched/build/libs/milk-lib-1.2.1+createport.1.jar runGameTest
```

Para repetir o cenário gráfico, use o mesmo parâmetro `createMilkJar` com `runClientGameTest`. Ele exige tela OpenGL; a nuvem utilizou Xorg `:99` com o arquivo de configuração retido do dragão e renderização por software. As capturas ficam em `create-port/integration-probe/build/reports/client-milk/`.

Para preparar o código Java do Create e observar os bloqueios restantes:

```bash
python3 create-port/tools/prepare.py --cache /workspace/.cache/create-port \
  --minecraft-jar /workspace/.cache/gradle/caches/fabric-loom/26.2/minecraft-merged.jar
./gradlew --no-daemon -p /workspace/.cache/create-port/adapted-fabric-reference \
  -PcreateMilkJar=/workspace/.cache/create-port/milk-patched/build/libs/milk-lib-1.2.1+createport.1.jar compileJava
```

O último comando atualmente **falha**. Sua falha deve continuar visível, não ser convertida em um falso resultado de sucesso. As áreas em cache são geradas pelas ferramentas; mantenha alterações manuais e patches fora delas antes de preparar novamente.

## Origem e licenças

O código da referência Fabric é MIT, com o aviso preservado em [LICENSE-UPSTREAM.txt](LICENSE-UPSTREAM.txt). Os avisos da Milk, Flywheel e Ponder/Catnip estão em [licenses](licenses). As bibliotecas baixadas e os JARs locais de desenvolvimento permanecem no cache; versões, origens e checksums constam em [target-libraries.json](target-libraries.json) e [library-sources.json](library-sources.json).

O Create 6.0.10 enviado licencia seu código como MIT e seus recursos em `assets/` como **All Rights Reserved**. O JAR enviado, seus modelos, texturas e sons não foram republicados. O patch de nomes contém apenas contexto de código Java MIT; a ferramenta extrai apenas Java da referência Fabric. O patch da Milk contém alterações no código e na configuração do build. Este trabalho não é uma distribuição oficial dos autores do Create.
