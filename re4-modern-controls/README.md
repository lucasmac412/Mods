# RE4 Movimento Livre — teclado e mouse

Extensão experimental do **re4_tweaks** para o Resident Evil 4 **original Steam UHD, app 254700**. Não é para o remake, para o port de 2007 ou para Minecraft.

**A versão 0.1.0 apresentou crash ao começar a andar no RE4 1.1.0. Não utilize esse pacote antigo.** A 0.1.1 remove dois hooks incorretos de início das funções de caminhada/corrida e mantém a pilha nativa intacta. A compilação Windows, os testes do controlador, os quatro testes de preparação e a regressão nativa x86 passaram. Os testes automáticos não equivalem a testes dentro do RE4; a correção do fechamento relatado, a jogabilidade e as animações ainda precisam de confirmação no jogo.

**[Baixar o ZIP experimental 0.1.1 para Windows](https://raw.githubusercontent.com/lucasmac412/Mods/re4-controles-v0.1.1-experimental/downloads/RE4-Movimento-Livre-v0.1.1-EXPERIMENTAL.zip)** · [Compilação e testes Windows](https://github.com/lucasmac412/Mods/actions/runs/39740706200). A origem e os checksums estão em `reports/windows-build.json`. Este download é independente dos mods de Minecraft.

## Controles implementados no código

| Entrada | Comportamento pretendido |
| --- | --- |
| W/A/S/D | Andar na direção da câmera; A/D deslocam em vez de girar parado |
| Diagonais, como W+D | Direção diagonal sem velocidade extra |
| Mouse | Câmera livre; mira com a arma levantada |
| Shift + direção | Corrida pela rotina original do jogo |
| Botão direito + WASD | Deslocamento lento durante a mira |
| Botão esquerdo durante a mira | Disparo original, preservando munição e cadência |

Teclado oferece oito direções por WASD; o mouse permite orientar essas direções em qualquer ângulo no mundo. Não há emulação de controle ou programa de macro. A mira e o disparo continuam obedecendo às teclas configuradas **no próprio jogo**. Para essa disposição, configure a mira no botão direito, disparo no esquerdo e movimento em WASD. O mod lê WASD independentemente do mouse: movimentar a mira sozinho não deve fazer Leon andar.

## Limites desta primeira implementação

- A integração se destina a Leon nas versões internacionais **1.1.0 e 1.0.6** do UHD. Não há promessa de compatibilidade com todas as versões. Versões regionais e de depuração conservam o controle original.
- Movimento livre usa as animações nativas de caminhada e corrida, orientando o corpo na direção escolhida.
- Movimento durante a mira ainda **não inclui uma nova mistura de animações de pernas e tronco**. Pode parecer que Leon desliza enquanto aponta a arma; essa parte exige trabalho e testes adicionais.
- Lunetas e binóculos conservam o controle original nesta versão. Ashley, Ada e Mercenaries não recebem a nova movimentação.
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
8. Se Leon não andar, houver travamento ou algum comportamento errado, feche o jogo e preserve o arquivo **`Bin32/dinput8.log`**. Ele mostra se o controlador foi ativado ou recusou as assinaturas. Não há necessidade de enviar sua conta Steam, senha, save ou chave de produto.

Ao atualizar da 0.1.0, substitua `dinput8.dll`, `dinput8.ini` e `re4-modern-controls.ini` pelos arquivos novos do ZIP. Confira no `dinput8.log` a linha `EXPERIMENTAL 0.1.1`. A correção dos hooks faz parte da DLL; mudar apenas o INI da 0.1.0 não corrige esse problema.

A 0.1.1 grava também **`Bin32/re4-modern-controls-diagnostico.log`**, com as primeiras chamadas nativas, seus retornos e os registradores de eventuais exceções. Esse arquivo é local, limitado e recomeça a cada inicialização; não envia dados. Exceções registradas podem ser tratadas pelo próprio jogo e não significam necessariamente um crash. Se o jogo voltar a fechar, preserve os dois logs **antes de abri-lo novamente**.

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

O workflow `.github/workflows/re4-modern-controls.yml` obtém o upstream e seus submódulos na revisão fixada, aplica a extensão, executa o controlador com MSVC e compila uma DLL Win32. O teste `native_prologue_test.cpp` executa em x86 os callbacks antigos, extraídos do upstream apenas como fixtures de teste, mede o EBP/ESP/argumento incorretos e verifica o prólogo nativo sem esses hooks. Também verifica que o diagnóstico observa exceções sem impedir o tratamento normal. Não inicia o jogo nem afirma aprovação em testes de gameplay.

Para compilar manualmente no Windows, use Visual Studio 2026 com ferramentas C++ x86 (toolset v145), clone o re4_tweaks recursivamente na revisão indicada, execute `tools/prepare.py PASTA_DO_UPSTREAM` e compile a solução com `Configuration=Release`, `Platform=Win32` e `PostBuildEventUseInBuild=false`. Essas ferramentas são para desenvolvimento; o jogador só precisa extrair o pacote compilado.

## Créditos

Base: [nipkownix/re4_tweaks](https://github.com/nipkownix/re4_tweaks), licença Zlib. Esta é uma alteração independente, não uma publicação oficial dos autores do re4_tweaks. A licença original está em `LICENSE-re4_tweaks.txt`; as dependências preservam suas licenças. A fonte de ícones incorporada pelo upstream é substituída por **Font Awesome Free Solid 6.7.2**, sob SIL OFL 1.1. IBM Plex também usa OFL; FreeType usa a FreeType Project License. Os textos e a origem das fontes estão em `third-party/`. Alguns ícones exclusivos da edição Pro podem não aparecer no menu F1. O código novo desta pasta usa MIT, em `LICENSE.txt`. Resident Evil é da Capcom. Nenhum executável, asset ou código reconstruído do jogo faz parte desta extensão.
