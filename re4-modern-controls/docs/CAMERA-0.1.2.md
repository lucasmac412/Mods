# Câmera ao trocar de direção no WASD

O jogador confirmou que a 0.1.1 funciona, mas relatou travadas ao trocar a direção no WASD. O vídeo enviado ultrapassou o limite de transferência de 32 MiB deste ambiente e não foi assistido. O diagnóstico abaixo vem da inspeção do controlador e de referências públicas sobre a câmera, não de observação do vídeo ou execução do RE4.

O controlador gira Leon instantaneamente e compensa o ângulo relativo da câmera para preservar sua orientação no mundo. A câmera nativa também conserva um histórico de interpolação lateral no espaço do personagem. Na troca de direção, misturar o deslocamento anterior com o novo, em referências diferentes, pode deslocar a câmera mesmo que a compensação angular esteja correta. A matriz que a câmera guardou também pode refletir a orientação anterior nesse quadro.

A 0.1.2 informa a orientação atual usando o campo nativo de direção de uso único e solicita reinicialização do histórico da câmera somente na entrada ou troca da direção de locomoção, quando o corpo realmente gira. Segurar a mesma direção e mover o mouse não solicita reinicializações contínuas. A operação respeita câmeras bloqueadas, estados especiais e uma direção já programada pelo jogo; não muda FOV, inclinação ou posição do jogador, nem desliga a colisão da câmera. Não instala outro hook em prólogos nativos.

O diagnóstico de movimento agora exige `Diagnostics = 1`. Mesmo habilitado, chamadas comuns não forçam mais `FlushFileBuffers`; a descarga forçada fica restrita ao registro de exceções. Isso remove outra possível fonte de pausas no começo do movimento.

O teste independente reproduz um salto ao misturar o histórico lateral anterior com a nova referência em oito mudanças de direção, e verifica a continuidade de posição com a correção. Inclui direção mantida, retomada após controle nativo, troca de instância, direção de cena e valores não finitos. Os testes de matemática, preparação e prólogos nativos não substituem o teste dentro do jogo: ainda é preciso conferir trocas rápidas de WASD com e sem Shift, mouse durante a caminhada, obstáculos, mirar e voltar a andar.
