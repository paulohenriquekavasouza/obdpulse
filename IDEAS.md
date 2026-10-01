# Ideias — OBD Pulse

Ideias para evoluir o app usando **apenas os dados que já lemos** (somente leitura via ELM327).
Marcação de onde se aplica (app / Android Auto) e a viabilidade.

## No app (celular)

1. **Resumo de viagem ao desconectar** — ao encerrar a sessão, mostrar um card/tela com distância, consumo médio, combustível usado, custo, melhor 0–100, velocidade/boost máximos e nota de condução. Salvar histórico de viagens (lista + detalhe). *Viável, alto valor.*
2. **Histórico persistente de viagens com gráfico** — guardar cada viagem (data, km, km/L, custo) e mostrar a evolução do consumo ao longo do tempo. *Viável.*
3. **Alertas configuráveis** — avisos (notificação/som/visual) para: temperatura do motor alta, boost acima de X, RPM acima de Y, tensão de bateria baixa, nível de combustível baixo. Limiares editáveis. *Viável, muito útil no dia a dia.*
4. **Dashboard de "gauges" no celular** — tela com mostradores gráficos (arco de boost, RPM, temperatura), parecida com o velocímetro do Auto, para usar o celular no suporte. *Viável.*
5. **Multi-gráfico / comparar parâmetros** — permitir 2–3 parâmetros sobrepostos no gráfico (ex.: boost × RPM × pedal) para ver a resposta do turbo. *Viável.*
6. **Modo "dragstrip" / medições** — além do 0–100: 0–200, 100–200, 400 m (quarto de milha) e 80–120 (retomada), com salvamento dos melhores tempos e tela dedicada. *Viável.*
7. **Exportar dados** — exportar viagem/log em CSV ou compartilhar o gráfico como imagem. *Viável.*
8. **Saúde do carro** — painel com tensão da bateria, % de etanol no tanque (flex), temperatura do óleo e histórico de falhas, organizados. *Viável (limpar falhas exigiria sair do modo somente-leitura — a decidir).*
9. **Tela de "economia"** — foco em eficiência: km/L instantâneo e médio, dica em tempo real (ex.: "tire o pé", "marcha alta"), score por trecho. *Viável.*
10. **Estimativa de potência/torque** — aproximar cavalos/torque a partir de MAF/carga/RPM e aceleração (estimativa, não dinamômetro). *Viável com ressalvas de precisão.*

## No Android Auto (depois)

11. **Temas/skins do velocímetro** — variações de layout/cor do mostrador (ex.: modo "pista" âmbar, modo "eco" verde), selecionável no celular.
12. **Modo foco por contexto** — trocar automaticamente o que o velocímetro mostra conforme a direção (cidade = consumo/velocidade; esportivo = boost/RPM/torque).
13. **Flash no cluster para alertas** — usar o "piscar no cluster" para avisar temperatura/boost alto, além do 0–100.
14. **Aba "Viagem" ao vivo** — espelhar distância/consumo/custo/autonomia da viagem atual nas listas do Auto.

## Transversais (infra de dados)

15. **Log/gravação de sessão** — gravar tudo em arquivo para análise posterior (e reaproveitar nos gráficos/histórico).
16. **Catálogo de PIDs proprietários** — tentar mapear PIDs da Stellantis ainda não decodificados (temperatura do câmbio, marcha) via engenharia dos valores brutos que o "Copiar diagnóstico" já coleta. *Exploratório, sem garantia.*

## Prioridades sugeridas

Maior impacto e 100% viáveis com o que já lemos: **#1 (resumo + histórico de viagem)**, **#3 (alertas)** e **#6 (medições de arrancada)**.
