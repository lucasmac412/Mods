# RE4 Movimento Livre — teclado e mouse

Extensão experimental do **re4_tweaks** para o Resident Evil 4 **original Steam UHD, app 254700**. Não é para o remake, para o port de 2007 ou para Minecraft.

**Versão 0.1.4 experimental:** amplia o movimento e a câmera independente para Ashley controlável, Ada, HUNK, Krauser e Wesker, além de Leon. O controlador usa a rotina comum de cada personagem e conserva suas animações. Mira móvel fica disponível para personagens armados nos estados normais de mira/disparo; Ashley, recarga, arremessos, transformação e ações especiais continuam com suas rotinas nativas.

**[Baixar o pacote simplificado 0.1.4](https://raw.githubusercontent.com/lucasmac412/Mods/re4-controles-v0.1.4-experimental/downloads/RE4-Movimento-Livre-v0.1.4-Simples.zip)**. Inclui os três arquivos de instalação em `Bin32`, um `README.txt` em inglês e um `LICENSES.txt` com os avisos preservados. [Compilação Windows aprovada](https://github.com/lucasmac412/Mods/actions/runs/39940503753): 10.187 verificações do controlador, quatro testes de preparação e regressões nativas x86 passaram. A origem e os checksums estão em `reports/windows-build.json`. É necessário conferir cada personagem e modo dentro do jogo; testes automáticos não garantem funcionamento visual ou de todas as interações.

| Personagem | Modos contemplados pelo controlador |
| --- | --- |
| Leon | Campanha e The Mercenaries |
| Ashley | Trecho da campanha em que você a controla |
| Ada | Separate Ways, Assignment Ada e The Mercenaries |
| HUNK, Krauser e Wesker | The Mercenaries |

O guia em inglês está em [INSTALL-EN.txt](INSTALL-EN.txt); a implementação e a lista de testes de gameplay estão em [CHARACTERS-0.1.4.md](docs/CHARACTERS-0.1.4.md).

## Controles implementados no código

| Entrada | Comportamento pretendido |
| --- | --- |
| W/A/S/D | Andar na direção da câmera; A/D deslocam em vez de girar parado |
| Diagonais, como W+D | Direção diagonal sem velocidade extra |
| Mouse | Câmera livre; mira com a arma levantada |
| Shift + direção | Corrida pela rotina original do jogo |
| Botão direito + WASD | Deslocamento lento durante a mira |
| Botão esquerdo durante a mira | Disparo original, preservando munição e cadência |

Teclado oferece oito direções por WASD; o mouse permite orientar essas direções em qualquer ângulo no mundo. Não há emulação de controle ou programa de macro. A mira e o disparo continuam obedecendo às teclas configuradas **no próprio jogo**. Para essa disposição, configure a mira no botão direito, disparo no esquerdo e movimento em WASD. O mod lê WASD independentemente do mouse: movimentar a mira sozinho não deve fazer o personagem andar.

## Limites desta primeira implementação

- A integração se destina aos seis personagens jogáveis nas versões internacionais **1.1.0 e 1.0.6** do UHD. Não há promessa de compatibilidade com todas as versões. Versões regionais e de depuração conservam o controle original.
- Movimento livre usa as animações nativas de caminhada e corrida, orientando o corpo na direção escolhida.
- Movimento durante a mira ainda **não inclui uma nova mistura de animações de pernas e tronco**. Pode parecer que o personagem desliza enquanto aponta a arma; essa parte exige trabalho e testes adicionais.
- Lunetas e binóculos conservam o controle original nesta versão. Ashley usa câmera de ombro ao jogar com este controlador de teclado/mouse. A Ashley que acompanha Leon continua sendo controlada pelo jogo.
- Menus, inventário, console, perda de foco, morte, dano e rotinas especiais devolvem o controle ao jogo. Escadas, golpes corpo a corpo, agarrões, portas e QTEs devem ser verificados no jogo.
- O controlador injeta deslocamento na rotina da arma, antes da etapa de colisão esperada do jogador. A ordem foi estudada com referências públicas; precisa ser confirmada na versão PC real. Não há atualização de posição em uma sobreposição gráfica, nem desativação de colisões.
- As assinaturas e a tabela de rotinas são verificadas antes de instalar os hooks. Se não coincidirem, a extensão se desativa e registra o motivo em `dinput8.log`; os demais ajustes do re4_tweaks podem continuar ativos.
- Não foi feito teste em uma instalação real de RE4/Steam ou Windows com o jogo. Compilar uma DLL não confirma que os hooks serão encontrados ou que o jogo será jogável.

## Instalação de uma compilação experimental

Use estes passos **somente com um ZIP contendo `Bin32/dinput8.dll` compilado com sucesso**, não com o ZIP de código-fonte do GitHub. A publicação de um pacote deve informar o resultado da compilação e as limitações acima.

1. Feche o RE4.
2. Na Steam, clique com o botão direito em **Resident Evil 4 (2005)** → **Gerenciar** → **Explorar arquivos locais**.
3. Abra a pasta **Bin32**. Dentro deve existir **bio4.exe**. Não substitua esse executável.
4. Se já houver `dinput8.dll`, `dinput8.ini` ou a pasta `re4_tweaks`, faça uma cópia deles em outro local antes de continuar. Esta instalação já inclui uma versão modificada do re4_tweaks; não instale outra por cima.
5. Extraia o ZIP para uma pasta. Abra a pasta `Bin32` que veio no ZIP e copie seu **conteúdo** para a pasta `Bin32` do jogo.
6. Abra o jogo normalmente pela Steam. Em opções de controle, use teclado/mouse com **mira Modern/Moderna**, WASD para movimento, botão direito para mirar e botão esquerdo para disparar.
7. Comece testando num local aberto, sem inimigos. Confira W, A, S, D, diagonais, Shift, movimento da câmera e, depois, movimento com o botão de mira pressionado. Teste uma parede, um desnível e um tiro enquanto se move.
8. Se o personagem não andar, houver travamento ou algum comportamento errado, feche o jogo e preserve o arquivo **`Bin32/dinput8.log`**. Ele mostra se o controlador foi ativado ou recusou as assinaturas. Não há necessidade de enviar sua conta Steam, senha, save ou chave de produto.

Ao atualizar de qualquer versão anterior, substitua `dinput8.dll`, `dinput8.ini` e `re4-modern-controls.ini` pelos arquivos novos do ZIP. Confira no `dinput8.log` a linha `EXPERIMENTAL 0.1.4`. A correção da câmera faz parte da DLL; mudar apenas o INI antigo não aplica a correção.

Na 0.1.4, o diagnóstico adicional fica desligado por padrão. Se for necessário investigar um novo fechamento, troque `Diagnostics = 0` por `Diagnostics = 1` em `re4-modern-controls.ini`, salve e reinicie o jogo. Depois de reproduzir o problema, preserve **`Bin32/dinput8.log`** e **`Bin32/re4-modern-controls-diagnostico.log` antes de abrir o jogo novamente**. O segundo arquivo registra localmente algumas chamadas e exceções; não envia dados. Um arquivo dessa versão anterior pode permanecer na pasta: só considere o log adicional novo depois de habilitar o diagnóstico. Exceções registradas podem ser tratadas pelo próprio jogo e não significam necessariamente um crash. Desative o diagnóstico depois da investigação.

Para desativar apenas os controles novos, abra `re4-modern-controls.ini` com o Bloco de Notas, troque `Enabled = 1` por `Enabled = 0`, salve e reinicie o jogo. F1 abre as opções do re4_tweaks; não habilite MouseTurning ou reset automático de câmera durante o teste deste controlador.

Para remover: com o jogo fechado, retire somente os arquivos que o pacote adicionou e restaure as cópias anteriores, se existirem. Não apague `bio4.exe`, a pasta BIO4 ou arquivos originais. Os saves não são alterados por esta extensão. Este fork também desativa atualização automática da DLL para que o updater não substitua os controles novos pela versão original do re4_tweaks.

## Desenvolvimento e verificação

`src/MovementCore.h` contém o controlador independente do jogo. `src/ModernControls.cpp` integra com o SDK e as rotinas nativas identificadas pelo re4_tweaks. A preparação aplica os arquivos à revisão exata em `upstream.json`; nenhum arquivo do jogo é incluído.

```bash
g++ -std=c++17 -Wall -Wextra -Werror -pedantic \
  -fsanitize=address,undefined -fno-omit-frame-pointer \
  -I re4-modern-controls/src re4-modern-controls/tests/movement_test.cpp \
  -o /tmp/re4-movement-test
/tmp/re4-movement-test
python3 -m unittest discover -s re4-modern-controls/tools -p 'test_*.py'
```

O teste do controlador verifica mudanças contínuas entre as oito direções, parada e movimento simultâneo do mouse em simulações a 30, 60 e 120 Hz. Confere que o corpo pode virar sem modificar a referência da câmera, que a suavização nativa não é reinicializada e que a direção é preservada na entrada da mira e na devolução ao controle nativo. Inclui mudança de sala, instância e interrupção do contador de quadros. Essa simulação não reproduz todo o motor do jogo.

O workflow `.github/workflows/re4-modern-controls.yml` obtém o upstream e seus submódulos na revisão fixada, aplica a extensão, executa o controlador com MSVC e compila uma DLL Win32. O teste `native_prologue_test.cpp` executa em x86 os callbacks antigos, extraídos do upstream apenas como fixtures, mede o EBP/ESP/argumento incorretos e verifica o prólogo nativo sem esses hooks. Também verifica que o diagnóstico observa exceções sem impedir o tratamento normal. `native_camera_test.cpp` verifica a preservação de x87, XMM0–7 e MXCSR no callback que a nova câmera adiciona ao hook existente. Não inicia o jogo nem afirma aprovação em testes de gameplay.


Para compilar manualmente no Windows, use Visual Studio 2026 com ferramentas C++ x86 (toolset v145), clone o re4_tweaks recursivamente na revisão indicada, execute `tools/prepare.py PASTA_DO_UPSTREAM` e compile a solução com `Configuration=Release`, `Platform=Win32` e `PostBuildEventUseInBuild=false`. Essas ferramentas são para desenvolvimento; o jogador só precisa extrair o pacote compilado.

## Créditos

Base: [nipkownix/re4_tweaks](https://github.com/nipkownix/re4_tweaks), licença Zlib. Esta é uma alteração independente, não uma publicação oficial dos autores do re4_tweaks. A licença original está em `LICENSE-re4_tweaks.txt`; as dependências preservam suas licenças. A fonte de ícones incorporada pelo upstream é substituída por **Font Awesome Free Solid 6.7.2**, sob SIL OFL 1.1. IBM Plex também usa OFL; FreeType usa a FreeType Project License. Os textos e a origem das fontes estão em `third-party/`. Alguns ícones exclusivos da edição Pro podem não aparecer no menu F1. O código novo desta pasta usa MIT, em `LICENSE.txt`. Resident Evil é da Capcom. Nenhum executável, asset ou código reconstruído do jogo faz parte desta extensão.

Para gerar novamente o pacote simplificado a partir do ZIP completo verificado: `python3 re4-modern-controls/tools/simplify_package.py downloads/RE4-Movimento-Livre-v0.1.4-EXPERIMENTAL.zip downloads`. A ferramenta confere os hashes do relatório Windows antes de reutilizar a DLL; não recompila nem altera o controlador.
