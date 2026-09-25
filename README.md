# OBD Pulse

App Android (Kotlin nativo) que se conecta a um leitor **ELM327 Bluetooth** e mostra os dados OBD-II do carro no celular e no **Android Auto**. Feito para o Fiat Pulse Abarth, mas funciona em qualquer veículo com OBD-II/OBDBr-2 (CAN).

O app é **somente leitura**: não apaga falhas nem grava nada nas centrais.

## O que ele lê

| Grupo | Dados | Serviço OBD |
|---|---|---|
| Tempo real | rotação, velocidade, pressão no coletor (MAP), temperaturas (motor, óleo, ar admitido, ambiente, catalisador), % de etanol, nível de combustível, carga do motor, borboleta, pedal do acelerador, avanço de ignição, MAF, consumo instantâneo, torque, lambda, correções de combustível, pressão do rail, pressão barométrica, tensão da central, hodômetro e contadores | Modo 01 |
| Calculado | pressão do turbo = MAP − pressão barométrica (em bar) | — |
| Falhas | códigos armazenados, pendentes e permanentes; estado da luz de injeção (MIL) | Modos 03, 07, 0A e PID 01 |
| Identificação | chassi (VIN) e nome de cada central | Modo 09 |
| Leitor | tensão da bateria no conector OBD | `ATRV` |

Na conexão, o app pergunta a cada central quais parâmetros ela suporta e só consulta esses. A lista exata do seu carro aparece depois da primeira conexão. Parâmetros rápidos (rotação, pressão, pedal) são lidos em todo ciclo; os lentos (temperaturas, combustível), a cada 10 ciclos.

**Não disponível:** dados proprietários da Stellantis, como temperatura do câmbio, marcha engatada, pressão dos pneus e vida útil do óleo. Eles exigem identificadores do fabricante que não são públicos.

## Por que Kotlin nativo

- O Android Auto só aceita apps feitos com a **Car App Library**, que é nativa. Flutter ou React Native precisariam dessa mesma camada nativa por baixo.
- Sem Compose e sem AppCompat: a interface do celular usa Views do próprio Android, e as dependências se resumem a Car App Library, lifecycle e coroutines. APK pequeno e inicialização rápida.

## Estrutura

```
app/src/main/java/br/com/obdpulse/
├── obd/        protocolo (Kotlin puro, testável): ELM327, ISO-TP, PIDs, falhas, motor de leitura
├── bt/         conexão Bluetooth SPP com o leitor
├── service/    serviço em primeiro plano que mantém a conexão ativa
├── ui/         tela do celular
├── car/        telas do Android Auto
├── ObdManager.kt
└── Prefs.kt
app/src/test/   testes do protocolo (simulador de ELM327 com duas centrais, motor e câmbio)
                e das telas do Android Auto (Robolectric + app-testing)
```

## Como compilar

Requisitos: Android Studio recente (ou JDK 17+ com Android SDK 36).

- **Android Studio:** abra a pasta do projeto, aguarde a sincronização do Gradle e clique em *Run*.
- **Linha de comando:**
  ```
  ./gradlew assembleDebug      # gera app/build/outputs/apk/debug/app-debug.apk
  ./gradlew test               # roda os testes do protocolo e das telas do Android Auto
  ```

Versões: AGP 8.13.2, Kotlin 2.2.21, Car App Library 1.7.0, Gradle 8.14.5, compileSdk/targetSdk 36 e minSdk 26.

## Primeiro uso

1. Com a ignição ligada, encaixe o ELM327 na tomada OBD, embaixo do painel, do lado do motorista.
2. Pareie o leitor nas configurações de Bluetooth do Android (PIN `1234` ou `0000`). Leitores só BLE (Bluetooth 4.0) não aparecem entre os pareados e não são suportados.
3. Abra o **OBD Pulse**, conceda as permissões, escolha o leitor e toque em **Conectar**.
4. Toque nos parâmetros para marcar com ★ os que devem aparecer no Android Auto.

## Android Auto

Como o app não vem da Play Store, é preciso liberar fontes desconhecidas no Android Auto (só uma vez):

1. Abra as configurações do Android Auto no celular e toque 10 vezes em **Versão** para ativar o modo desenvolvedor.
2. No menu ⋮, abra **Configurações do desenvolvedor** e ative **Fontes desconhecidas**.
3. Conecte o celular ao carro: o **OBD Pulse** aparece na lista de apps da central.

Na tela do carro:
- A primeira linha mostra o status. Toque nela para conectar ou desconectar, usando o leitor escolhido no celular.
- As linhas seguintes mostram os parâmetros marcados com ★, na ordem em que foram marcados.
- O botão **Falhas** lê e lista os códigos de falha.
- A tela atualiza no máximo uma vez por segundo, e o número de linhas é limitado pelo Android Auto (normalmente 6).

O app usa a categoria IoT do Android Auto. É um app para uso pessoal, instalado fora da Play Store. Em hosts com Car API 7 ou superior o cabeçalho usa o componente `Header`; em hosts mais antigos, o formato anterior.

## Limitações

- Adaptadores "ELM327 v2.1" são clones, porque essa versão nunca foi lançada oficialmente. Podem ser lentos (poucas leituras por segundo) ou instáveis. Se a conexão cair com frequência, um leitor de melhor qualidade resolve.
- O Bluetooth do celular fica dividido entre o leitor e o Android Auto sem fio. Em alguns aparelhos isso pode deixar a conexão instável.
- A versão *release* é assinada com a chave de debug, adequada só para instalação pessoal.
