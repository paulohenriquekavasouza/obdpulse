# OBD Pulse

App Android (Kotlin nativo) que se conecta a um leitor **ELM327 Bluetooth** e mostra os dados OBD-II do carro no celular e no **Android Auto**. Feito para o Fiat Pulse Abarth, mas funciona em qualquer veículo com OBD-II/OBDBr-2 (CAN).

O app é **somente leitura**: não apaga falhas nem grava nada nas centrais.

## O que ele lê

| Grupo | Dados | Serviço OBD |
|---|---|---|
| Tempo real | rotação, velocidade, pressão no coletor (MAP), temperaturas (motor, óleo, ar admitido, ar no intercooler, ambiente, catalisadores), % de etanol, nível de combustível, carga do motor, borboleta, pedal do acelerador, avanço de ignição, ponto de injeção, MAF, consumo instantâneo, vazão de combustível e de gases de escape, torque, lambda e sondas, correções de combustível, estado da malha de combustível, pressão do rail, sistema do cânister, pressão barométrica, tensão da bateria, hodômetro, contadores, tipo de combustível e norma OBD | Modo 01 |
| Calculado | pressão do turbo = MAP − pressão barométrica (em bar); consumo em km/L = velocidade ÷ consumo instantâneo | — |
| Falhas | códigos armazenados, pendentes e permanentes; estado da luz de injeção (MIL) | Modos 03, 07, 0A e PID 01 |
| Identificação | chassi (VIN) e nome de cada central | Modo 09 |
| Leitor | tensão da bateria no conector OBD | `ATRV` |

Na conexão, o app pergunta a cada central quais parâmetros ela suporta e só consulta esses. A lista exata do seu carro aparece depois da primeira conexão. Parâmetros rápidos (rotação, pressão, pedal) são lidos em todo ciclo; os lentos (temperaturas, combustível), a cada 10 ciclos.

O botão **Copiar diagnóstico** copia um relatório com o leitor, o protocolo, os parâmetros suportados por central, os valores brutos dos que o app ainda não decodifica e os valores atuais. Esse relatório serve para acrescentar novos parâmetros ao catálogo.

Funciona com CAN de 11 e de 29 bits (o Pulse usa 29 bits). O protocolo detectado fica salvo, e as conexões seguintes começam por ele.

Detalhes de leitura:
- **Escala do MAP:** quando a central informa o PID 4F, o MAP usa a faixa máxima declarada. O Pulse declara 400 kPa, em vez dos 255 kPa padrão, conforme a SAE J1979. A pressão do turbo é calculada sobre esse valor.
- **Tensão da bateria:** quando a central informa o PID 42, o app usa esse valor e não o do leitor (a medição de clones do ELM327 costuma ser imprecisa).
- **Respostas longas em CAN 29 bits:** chassi (VIN), nome das centrais e alguns PIDs ocupam mais de um quadro. O app configura o flow control manualmente (`ATFCSH`/`ATFCSD`/`ATFCSM1`) porque leitores clones costumam falhar no modo automático.

**Não disponível:** dados proprietários da Stellantis, como temperatura do câmbio, marcha engatada, pressão dos pneus e vida útil do óleo. Eles exigem identificadores do fabricante que não são públicos.

## Por que Kotlin nativo

- No Android Auto o app aparece pela **interface de mídia** (MediaBrowserService), que é nativa do Android. Flutter ou React Native precisariam dessa mesma camada nativa por baixo.
- Sem Compose e sem AppCompat: a interface do celular usa Views do próprio Android, e as dependências se resumem a RecyclerView, lifecycle e coroutines. APK pequeno e inicialização rápida.

## Estrutura

```
app/src/main/java/br/com/obdpulse/
├── obd/        protocolo (Kotlin puro, testável): ELM327, ISO-TP, PIDs, falhas, motor de leitura
├── bt/         conexão Bluetooth SPP com o leitor
├── service/    serviço em primeiro plano que mantém a conexão ativa
├── ui/         tela do celular
├── media/      interface de mídia do Android Auto (MediaBrowserService)
├── ObdManager.kt
└── Prefs.kt
app/src/test/   testes do protocolo (simulador de ELM327 com duas centrais, motor e câmbio)
                e da interface de mídia do Android Auto (Robolectric)
```

## Como compilar

Requisitos: Android Studio recente (ou JDK 17+ com Android SDK 36).

- **Android Studio:** abra a pasta do projeto, aguarde a sincronização do Gradle e clique em *Run*.
- **Linha de comando:**
  ```
  ./gradlew assembleDebug      # gera app/build/outputs/apk/debug/app-debug.apk
  ./gradlew test               # roda os testes do protocolo e da interface de mídia
  ```

Versões: AGP 8.13.2, Kotlin 2.2.21, Gradle 8.14.5, compileSdk/targetSdk 36 e minSdk 26.

## Primeiro uso

1. Com a ignição ligada, encaixe o ELM327 na tomada OBD, embaixo do painel, do lado do motorista.
2. Pareie o leitor nas configurações de Bluetooth do Android (PIN `1234` ou `0000`). Leitores só BLE (Bluetooth 4.0) não aparecem entre os pareados e não são suportados.
3. Abra o **OBD Pulse**, conceda as permissões, escolha o leitor e toque em **Conectar**.
4. Toque nos parâmetros para marcar com ★ os que devem aparecer no Android Auto.

Com **Conectar automaticamente** marcado (padrão), o app conecta sozinho ao leitor salvo ao abrir a tela do celular ou ao entrar no Android Auto, sem precisar tocar em Conectar/Play.

Três telas adicionais no celular:
- **Recordes:** melhores marcas da sessão atual e do histórico (melhor 0–100, velocidade, rotação e turbo máximos), com botão para zerar o histórico.
- **Computador de bordo:** distância, combustível usado, custo estimado (pelo preço do litro), consumo médio, autonomia estimada (pelo nível do tanque) e nota de condução. O preço do combustível e o volume do tanque são configuráveis.
- **Gráfico ao vivo:** curva em tempo real de qualquer parâmetro lido, selecionável no topo.

## Android Auto

No carro o app aparece como um **app de mídia** (player): um navegador com as abas Painel, Desempenho, Turbo e Falhas, e os valores em destaque na tela "tocando agora". Essa é a única interface — não há a tela de painel da Car App Library, porque a intenção é rodar o app instalado direto por APK, sem publicar no Google Play.

A opção "Fontes desconhecidas" do Android Auto vale para apps de mídia, então esta interface funciona com o APK:

1. Abra as configurações do Android Auto no celular e toque 10 vezes em **Versão** para ativar o modo desenvolvedor.
2. No menu ⋮, abra **Configurações do desenvolvedor** e ative **Fontes desconhecidas**.
3. Escolha o leitor no app do celular uma vez.
4. Conecte o celular ao carro e abra o **OBD Pulse** na lista de apps de mídia.

No carro há quatro abas: **Painel**, **Desempenho**, **Turbo** e **Falhas**.
- **Play** conecta ao leitor, e **Pause** desconecta. A linha **Status** da aba Painel também alterna a conexão. A barra de transporte tem só play/pause, sem outros botões.
- **Painel:** sempre traz o **Consumo em km/L** como primeira informação, seguido do Status, dos favoritos (★) e de um atalho para **Todos os parâmetros**.
- **Desempenho:** cronômetro de **0–100 km/h** (melhor e última), velocidade/rotação/turbo/temperatura máximas da viagem e média de km/L. Zera a cada nova conexão.
- **Turbo:** boost atual e máximo, pressão no coletor (MAP), pressão barométrica e temperatura do intercooler.
- **Falhas:** lê e lista os códigos ao abrir a aba.
- **Painel** é a lista curada e ordenável: **km/L (fixo no topo)**, **"Piscar no cluster"** logo abaixo e, em seguida, só os itens marcados com ★, na ordem definida por você. No app do celular, cada linha tem **★** (aparece no Painel) e o ícone **≡** para arrastar e ordenar. A ordem do Painel segue essa ordenação (não a ordem em que você marcou), então (des)favoritar mantém cada item no seu lugar. Reordenar ou marcar no celular atualiza o Painel do Android Auto na hora.
- **Todos** lista todos os parâmetros do carro (referência), sem personalização.
- **Painel no player ("tocando agora"):** a capa é uma imagem desenhada ao vivo, com fundo em gradiente — arco de **velocidade (0–240 km/h)** com cor por faixa e, abaixo, km/L (em destaque), duas células que fazem **rodízio** entre rotação, motor, intercooler, admissão, torque e MAP (troca a cada 3 s, só entre os parâmetros presentes) e o **último 0–100 abaixo de 10s**. O **subtítulo do player** também faz rodízio, alternando entre motor/rotação, turbo/intercooler e consumo/velocidade. Acima de **80 km/h** a imagem ganha uma borda e um brilho vermelhos (que o Android Auto reflete no fundo), com intensidade crescente até 120 km/h. Atualiza a cada 0,2 s.
- **Piscar no cluster:** o painel do carro espelha o título do "tocando agora". Por padrão o título fica fixo ("OBD Pulse"). Você pode escolher uma informação para piscar no cluster — no app do celular (lista "Piscar no cluster") ou no Android Auto (Painel → "Piscar no cluster"); a escolha fica sincronizada entre os dois. **Cada 0–100 km/h abaixo de 10s pisca o tempo obtido** por alguns segundos — mas só quando **não** há uma informação fixa selecionada (a fixa tem prioridade e não é interrompida).

Limitações: a interface é de player (sem áudio), e o Android Auto trata o OBD Pulse como a fonte de mídia atual enquanto ele está aberto. Dependendo da versão do Android Auto, isso pode interferir no app de música.

Sobre o pop-up "tocando agora" no cluster: o painel do carro mostra um aviso sempre que o título dos metadados de mídia muda. Nesta branch, os metadados ficam fixos ("OBD Pulse" e o status da conexão) e só mudam ao conectar ou desconectar, então o pop-up deixa de aparecer a cada leitura. Em troca, a linha "tocando agora" não mostra mais os valores ao vivo nem o valor em destaque ao tocar num item; os valores ao vivo continuam nas abas Painel e Todos, que atualizam sem gerar o pop-up. Não há como manter os valores ao vivo no "tocando agora" sem reativar o pop-up, porque ele é uma reação do próprio cluster à troca de metadados.

A partir de 30/09/2026, o Android passa a exigir, no Brasil, desenvolvedor verificado para instalar APKs fora das lojas. Para um APK de desenvolvedor não registrado, é preciso usar o `adb` ou o fluxo avançado do sistema.

## Limitações

- O Bluetooth do celular fica dividido entre o leitor e o Android Auto sem fio. Em alguns aparelhos isso pode deixar a conexão instável.
- A versão *release* é assinada com a chave de debug, o que basta para instalação pessoal via APK. O app não é feito para publicação no Google Play.
