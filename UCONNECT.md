# Uconnect / Stellantis — estudo de viabilidade (walk-away auto-lock)

Objetivo: travar o carro automaticamente ao se afastar (walk-away), usando o GPS do
celular e a localização do veículo, via a **API de nuvem do Uconnect/Stellantis** (a
mesma usada pelo app oficial). Isto é independente do lado OBD/ELM327 (que é só leitura);
aqui é uma função de **controle via nuvem**.

## Estado da arte (não precisamos reverter do zero)

A API já foi reverse-engineered por projetos open source que fazem lock/unlock e
localização:
- `hass-uconnect` + biblioteca `py-uconnect` (Python)
- `FiatChamp` (add-on Home Assistant)
- `Blueion76/FCAUconnect-HA`

## Como a API funciona (lido da py-uconnect)

Backend: **FCA Global Connected Vehicle** (`*.fcagcv.com`). Login via **Gigya/SAP CDC**
e credenciais temporárias via **AWS Cognito**, com requisições assinadas em **AWS SigV4**.

Fluxo de autenticação:
1. `accounts.webSdkBootstrap` (com a *login API key* da marca) inicia a sessão.
2. `accounts.login` com e-mail + senha → *login token*.
3. `accounts.getJWT` → JWT.
4. Troca do JWT em `token_url` da marca → token Cognito + IdentityId.
5. `cognito.get_credentials_for_identity()` → credenciais AWS temporárias.
6. Chamadas à API assinadas com AWS SigV4 (serviço `execute-api`).

Cabeçalhos típicos: `x-clientapp-name: CWP`, `x-clientapp-version`, `clientrequestid`
(hex 16), `x-api-key` (chave da marca), `locale`, `x-originator-type: web`.

Comandos (travar/destravar, etc.):
- Autenticação por PIN: `POST /v1/accounts/{uid}/ignite/pin/authenticate` → devolve
  `token` usado como `pinAuth`.
- Envio do comando: `POST /{api}/accounts/{uid}/vehicles/{vin}/{cmd.url}` com corpo
  `{"command": <nome>, "pinAuth": <token>}`. Resposta pode vir `pending` com
  `correlationId` para polling. (Lock/unlock são comandos definidos na lib; confirmar
  os identificadores exatos — no padrão FCA costumam ser RDL/RDU.)

Localização:
- Última conhecida: `GET /v1/accounts/{uid}/vehicles/{vin}/location/lastknown`.
- Forçar atualização: `POST /v1/accounts/{uid}/vehicles/{vin}/location/` com
  `{"command": "VF", "pinAuth": <token>}` → `correlationId`.

Autenticação do app: **e-mail + senha + PIN** (PIN exigido para comandos).

## Marcas/regiões na py-uconnect (branch master)

| Marca | Região (AWS) | Login | API | Locale |
|---|---|---|---|---|
| FIAT_EU | eu-west-1 | loginmyuconnect.fiat.com | sdpr-01.fcagcv.com | de_de |
| FIAT_US | us-east-1 | login-us.fiat.com | sdpr-02.fcagcv.com | en_us |
| FIAT_CANADA | us-east-1 | login-stage-us.fiat.com | sdpr-02.fcagcv.com | en_us |
| FIAT_ASIA | eu-west-1 | login-iap.fiat.com | sdpr-01.fcagcv.com | de_de |
| JEEP / ALFA / MASERATI / CHRYSLER / DODGE / RAM | EU/US/Ásia | variantes | sdpr-01/02 | variantes |

São 17 marcas, todas **EU, EUA/Canadá ou Ásia**.

## Brasil / Pulse — situação

**Não há marca Brasil/LATAM na py-uconnect.** O backend é o FCA global (`fcagcv.com`),
então o Pulse é **provavelmente alcançável**, mas **não confirmado**: falta a
configuração regional do Brasil, que não é pública:
- URL de login (Gigya) do app brasileiro;
- URL/host da API (ex.: `sdpr-0X.fcagcv.com` ou gateway LATAM);
- `x-api-key` e `login API key` da marca no Brasil;
- região AWS/Cognito (provável `sa-east-1`) e `token_url`;
- `locale` (ex.: `pt_br`).

Como obter esses parâmetros (do SEU lado, no seu PC — não deste contêiner):
1. Decompilar o APK do app Fiat brasileiro (ex.: `jadx`) e procurar as constantes de
   endpoint/chaves; ou
2. Capturar o tráfego de login do app (mitmproxy + CA próprio) — provavelmente barrado
   por *certificate pinning*, o que pode exigir Frida/app repack.
Com esses valores, dá para registrar uma "marca FIAT_BR" e testar login/lock/location.

## Restrições e riscos

- **Termos de uso:** a Stellantis proíbe clientes não oficiais / engenharia reversa —
  risco de bloqueio da conta. Decisão do dono do veículo.
- **Credenciais:** e-mail/senha/PIN ficam **apenas no dispositivo** (Android Keystore),
  usados em tempo de execução pelo app. Nunca em repositório, logs ou chat.
- **Ação de controle:** travar/destravar mexe no carro (diferente do OBD read-only).
- **Latência:** comando vai celular → nuvem Stellantis → carro (celular/TCU), segundos.
- **Localização defasada:** `lastknown` pode estar velha; `VF` força atualização mas
  também leva segundos.
- **GPS em segundo plano:** exige serviço em primeiro plano + permissão de localização
  em background; custo de bateria.

## Arquitetura do walk-away (proposta)

1. Módulo "Uconnect" no app (rede), isolado do lado OBD; em branch própria.
2. Cliente: login (Gigya→JWT→Cognito→SigV4), lista de veículos/VIN, `lock`/`unlock`,
   `location/lastknown` + `VF`.
3. Serviço em primeiro plano monitora o GPS do celular; ao **sair de um raio** em torno
   da última localização do carro e se afastando, dispara **travar**.
4. Guarda-corpos: raio e atraso configuráveis; **só travar (nunca destravar sozinho)**;
   notificação/confirmação antes de travar (ao menos no início); liga/desliga.

## Teste de login (confirmar o Brasil)

`tools/uconnect_login_test.py` tenta logar a sua conta em cada região Fiat conhecida
(EU, US, Ásia, Canadá — e BR/LATAM se a lib vier a ter) e lista os veículos/VIN de
quem logar. Rodar no PC do usuário; as credenciais ficam só na máquina dele.

```
pip install py-uconnect
python tools/uconnect_login_test.py
```

Interpretação:
- Alguma região imprime SUCESSO com o seu VIN → o Pulse é alcançável por aquela região.
- Todas falham na autenticação → o Brasil usa backend próprio não incluso na lib;
  seguir para a extração dos endpoints/chaves do app brasileiro.

## Conclusão

- A função (lock + localização + walk-away) é tecnicamente madura para EU/EUA/Ásia.
- Para o **Brasil (Pulse)** é **provável, porém não confirmada**: depende de obter os
  parâmetros regionais do app brasileiro (passo de RE no PC do usuário).
- Próximo passo recomendado: confirmar se a conta brasileira loga no backend FCA e
  descobrir os endpoints/chaves do Brasil antes de construir o walk-away.
