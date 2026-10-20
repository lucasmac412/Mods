# Dependências de reconhecimento de voz

O código Java deste complemento está sob a licença MIT do repositório. As seguintes bibliotecas e modelos são de terceiros e mantêm suas próprias licenças:

| Componente | Versão / origem | Licença |
| --- | --- | --- |
| Vosk API Java e bibliotecas nativas | `com.alphacephei:vosk:0.3.45`, [projeto oficial](https://github.com/alphacep/vosk-api) e Maven Central | Apache License 2.0 |
| JNA | `net.java.dev.jna:jna:5.17.0`, [projeto oficial](https://github.com/java-native-access/jna) e Maven Central | LGPL 2.1 ou posterior / Apache License 2.0, conforme opção da distribuição |
| Modelo português Vosk | `vosk-model-small-pt-0.3`, [catálogo oficial](https://alphacephei.com/vosk/models) | Apache License 2.0, conforme catálogo oficial |

O Vosk JAR inclui código nativo para Windows x64, Linux x64 e macOS Intel/ARM64. JNA inclui seus avisos e licenças no próprio JAR. O pacote de instalação preserva o material do modelo e inclui os avisos/licenças necessários à redistribuição.

Vosk JAR SHA-256: `9e38e96e4448a41d889bb254b2f8424945554945c6e77e1cd97a3d8633fac2ba`.

ZIP oficial do modelo português SHA-256: `6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7`.

O mecanismo de síntese de fala já acompanha Minecraft e é usado pela API `com.mojang.text2speech.Narrator`. Não é redistribuído como código próprio, não treina uma voz e não oferece seleção de idioma/timbre neste complemento. Minecraft e Fabric são dependências adicionais com suas próprias licenças.
