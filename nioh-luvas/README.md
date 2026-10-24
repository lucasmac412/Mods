# Punhos da Lua Azul — Nioh 1 Steam

**Protótipo de autoria e ferramenta de identificação. Ainda não é uma categoria de arma instalada no Nioh.** O objetivo autorizado é acrescentar luvas compactas, golpes novos nas três posturas, ondas de choque e efeitos luminosos, com um especial de cortes espaciais no Caps Lock. As armas existentes não serão usadas como uma substituição disfarçada de categoria nova.

O desenvolvimento já tem uma simulação de combate, uma prévia interativa independente do jogo, um modelo/rig original com sete clips de animação de autoria e um inspetor Windows que lê informações da instalação. **Não existem ainda IDs nativos de categoria, animações ou efeitos validados.** O inspetor não torna a integração pronta: identifica a versão e os arquivos relevantes para a próxima investigação.

[Download do protótipo e identificador Windows](https://raw.githubusercontent.com/lucasmac412/Mods/nioh-luvas-prototipo-v0.1.0/downloads/Nioh-Luvas-Prototipo-v0.1.0.zip) · [Prévia HTML](preview/Previa.html) · [Instruções passo a passo](LEIA-ME.txt)

| Parte | Estado real |
| --- | --- |
| Luvas compactas e rig original | Asset de autoria em glTF/GLB; não convertido para os formatos/esqueleto do jogo |
| Três posturas, sete golpes/clips, ondas e brilho | Simulação e prévia próprias; não são animações do personagem dentro do Nioh |
| Caps Lock: Cortes do Vazio | Oito impactos simulados, formação de energia e cortes luminosos na prévia |
| Ki, alcance, cooldown e interrupções | Lógica de simulação testável; sem acesso aos atributos nativos do jogo |
| Inspetor Windows | Somente leitura de versão, hashes, metadados e lista de arquivos/módulos; grava um relatório escolhido pelo usuário |
| Categoria adicional em inventário/equipamento | Investigação pendente |
| Animação/colisão/dano/efeitos/Ki no Nioh | Integração nativa pendente |
| Testes dentro do Nioh | Não executados neste ambiente |

## Como experimentar e continuar

Extraia o ZIP e abra `Previa.html` no Chrome. Use os botões, as teclas 1/2/3 e Caps Lock. Os atalhos só atuam nessa página. A aura tem ajuste de intensidade; o alvo e o Ki são simulados. Nada é instalado no jogo.

Para identificar a instalação, abra o Nioh e deixe no menu inicial. Execute `Nioh-Inspecionar.exe`, escolha o `nioh.exe` indicado pela Steam em **Gerenciar → Explorar arquivos locais**, e salve `Nioh-Diagnostico.json`. Envie esse arquivo na conversa. Os passos completos estão em [LEIA-ME.txt](LEIA-ME.txt).

O relatório não contém cópia do executável ou da memória, conteúdo de saves, caminhos absolutos nem dados da conta Steam. Lê hashes SHA-256 do executável e, quando disponível, da seção de código carregada, para diferenciar o arquivo em disco da imagem em execução. O acesso ao processo usa somente `PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ`; não instala hooks nem altera bytes. Não inclui bibliotecas de rede.

## Desenvolvimento e verificação

`assets/weapon.json` é a fonte única dos tempos, custos e impactos. O gerador produz o header C++ e a configuração embutida na prévia, que funciona por `file://` sem bibliotecas ou conexão externa. O modelo GLB é um rig original simplificado, sem assets ou esqueleto do Nioh. Os movimentos servem de material de autoria e precisam de adaptação caso se confirme uma forma de registrar animações novas.

```bash
python3 nioh-luvas/tools/generate.py
python3 nioh-luvas/tools/model.py
python3 nioh-luvas/tools/verify.py
g++ -std=c++17 -Wall -Wextra -Werror -pedantic -fsanitize=address,undefined \
  -fno-omit-frame-pointer -I nioh-luvas/src nioh-luvas/tests/combat_test.cpp -o /tmp/nioh-combat-test
/tmp/nioh-combat-test
g++ -std=c++17 -Wall -Wextra -Werror -pedantic -fsanitize=address,undefined \
  -fno-omit-frame-pointer -I nioh-luvas/src nioh-luvas/tests/pe_test.cpp -o /tmp/nioh-pe-test
/tmp/nioh-pe-test
```

O workflow `.github/workflows/nioh-luvas.yml` verifica a geração, executa os testes em MSVC x64, compila o inspetor com runtime estático, faz uma autoinspeção nativa e compara o hash com o cálculo independente em Python. Chrome ou Edge executa o especial da prévia e produz uma captura. Nenhuma dessas verificações carrega o Nioh.

Resultados locais iniciais: 4.138 verificações da simulação e 445 do parser PE passaram com ASan/UBSan. [Compilação Windows aprovada](https://github.com/lucasmac412/Mods/actions/runs/40051862492): os mesmos testes passaram em MSVC x64, o inspetor passou na autoinspeção nativa e a prévia foi executada em Chrome/Edge headless. Escopo e hashes em `reports/build.json`. Esses números não representam centenas de funcionalidades jogáveis nem confirmação de compatibilidade com o jogo.

Código original sob MIT. Referências técnicas e limitações em [RESEARCH.md](docs/RESEARCH.md). Não inclui código ou assets dos jogos/animes usados como inspiração.

[Captura da prévia no navegador](docs/caps-preview.png) — representação independente, não é uma captura do Nioh.
