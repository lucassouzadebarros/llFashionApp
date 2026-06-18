# whatsapp-checkout-nuvemshop

Backend Java 17 com Spring Boot para integrar uma loja Nuvemshop com um fluxo de vendas pelo WhatsApp.

Nesta primeira etapa, a API cobre OAuth da Nuvemshop, sincronização de produtos/variações, vínculo com `metaProductRetailerId`, criação de Draft Order e persistência local do pedido.

## Stack

- Java 17
- Spring Boot
- Spring Web
- Spring WebFlux com WebClient
- Spring Data JPA
- PostgreSQL
- Flyway
- Bean Validation
- Maven
- Docker Compose

## Documentacao de testes da API

Veja o passo a passo completo em [docs/API_TESTES.md](docs/API_TESTES.md).

Para o checkout visual mobile em React, veja também [docs/REACT_WEBAPP_CHECKOUT.md](docs/REACT_WEBAPP_CHECKOUT.md). O WhatsApp Flow ficou como fallback opcional.

Para publicar no Render e trocar as URLs do ngrok pela URL fixa do deploy, veja [docs/RENDER_DEPLOY.md](docs/RENDER_DEPLOY.md).

## Subir o banco

```bash
docker compose up -d
```

O banco fica disponível em:

```text
jdbc:postgresql://localhost:5432/whatsapp_checkout
```

Usuário e senha padrão:

```text
postgres / postgres
```

## Variáveis de ambiente

Crie as variáveis localmente com base no arquivo `.env.example`.

```bash
NUVEMSHOP_APP_ID=32468
NUVEMSHOP_CLIENT_SECRET=coloque_o_client_secret_aqui
DATABASE_URL=jdbc:postgresql://localhost:5432/whatsapp_checkout
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
FRONTEND_BASE_URL=http://localhost:8080/storefront/
CART_EXPIRATION_MINUTES=120
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
```

O `NUVEMSHOP_CLIENT_SECRET` nunca deve ser versionado nem colocado fixo no código.

## Rodar a aplicação

Confirme que o Maven está usando Java 17:

```bash
mvn -version
```

Se aparecer Java 11 ou inferior, ajuste o `JAVA_HOME` para uma JDK 17 antes de rodar. No Windows, por exemplo:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

```bash
mvn spring-boot:run
```

A API sobe em:

```text
http://localhost:8080
```

## Health check

```http
GET http://localhost:8080/api/health
```

Resposta:

```json
{
  "status": "UP",
  "application": "whatsapp-checkout-nuvemshop"
}
```

## Expor local com ngrok

```bash
ngrok http 8080
```

Configure no painel da Nuvemshop a URL de callback usando o domínio gerado:

```text
https://SEU-NGROK.ngrok-free.dev/api/nuvemshop/oauth/callback
```

Para desenvolvimento local, a URL esperada é:

```text
http://localhost:8080/api/nuvemshop/oauth/callback
```

## OAuth Nuvemshop

Obter URL de instalação:

```http
GET http://localhost:8080/api/nuvemshop/oauth/install-url
```

Resposta:

```json
{
  "installUrl": "https://www.nuvemshop.com.br/apps/32468/authorize"
}
```

Abra a `installUrl` no navegador e autorize o app na loja. A Nuvemshop chamará:

```http
GET /api/nuvemshop/oauth/callback?code=...
```

A API troca o `code` por `access_token`, salva o `access_token` e o `user_id` como `store_id` na tabela `nuvemshop_installation`.

Observacoes importantes sobre o OAuth:

- O `code` da Nuvemshop expira em 5 minutos.
- O `code` so deve ser usado uma vez.
- Se a troca do token falhar, reinstale ou autorize novamente o aplicativo para gerar um novo `code`.
- O Client Secret deve estar configurado na variavel de ambiente `NUVEMSHOP_CLIENT_SECRET`.
- A URL de callback no painel da Nuvemshop deve ser `https://SEU-NGROK.ngrok-free.dev/api/nuvemshop/oauth/callback`.
- Se a Nuvemshop retornar HTML no endpoint de token, confira `nuvemshop.token-url`, `NUVEMSHOP_CLIENT_SECRET`, se o `code` expirou ou ja foi usado, e se a URL de callback esta igual a configurada no painel.
- Se o endpoint de token retornar `Content-Type: text/html` com body JSON valido, a API aceita a resposta e registra um aviso no log.

## Sincronizar produtos

```http
POST http://localhost:8080/api/admin/nuvemshop/products/sync
```

Resposta:

```json
{
  "totalProductsRead": 10,
  "totalVariantsSynced": 25,
  "message": "Sincronização concluída com sucesso"
}
```

## Listar produtos mapeados

```http
GET http://localhost:8080/api/admin/products/mappings
```

Filtros opcionais:

```http
GET http://localhost:8080/api/admin/products/mappings?active=true
GET http://localhost:8080/api/admin/products/mappings?productName=Saia
GET http://localhost:8080/api/admin/products/mappings?sku=SAIA-PRETO-M
GET http://localhost:8080/api/admin/products/mappings?metaProductRetailerId=1522832641
```

## Vincular produto ao catálogo Meta/WhatsApp

```http
PUT http://localhost:8080/api/admin/products/mappings/{id}/meta-retailer-id
Content-Type: application/json
```

Body:

```json
{
  "metaProductRetailerId": "1522832641"
}
```

## Criar Draft Order

Usando `nuvemshopVariantId`:

```http
POST http://localhost:8080/api/orders/draft
Content-Type: application/json
```

```json
{
  "customerName": "Maria",
  "customerLastname": "Cliente",
  "customerEmail": "maria@email.com",
  "customerPhone": "5521999999999",
  "items": [
    {
      "nuvemshopVariantId": 987654321,
      "quantity": 1
    }
  ]
}
```

Usando `metaProductRetailerId`:

```json
{
  "customerName": "Maria",
  "customerLastname": "Cliente",
  "customerEmail": "",
  "customerPhone": "5521999999999",
  "items": [
    {
      "metaProductRetailerId": "1522832641",
      "quantity": 1
    }
  ]
}
```

Resposta:

```json
{
  "localOrderId": "00000000-0000-0000-0000-000000000000",
  "nuvemshopDraftOrderId": 39138283,
  "status": "AGUARDANDO_PAGAMENTO",
  "checkoutUrl": "https://...",
  "abandonedCheckoutUrl": "https://...",
  "message": "Pedido criado com sucesso. Envie o checkoutUrl para o cliente finalizar o pagamento."
}
```

## Consultar Draft Order

```http
GET http://localhost:8080/api/orders/draft/{draftOrderId}
```

## Consultar pedido local

```http
GET http://localhost:8080/api/orders/{localOrderId}
```

## Simular carrinho do WhatsApp

```http
POST http://localhost:8080/api/mock/whatsapp/cart
Content-Type: application/json
```

```json
{
  "customerName": "Maria",
  "customerPhone": "5521999999999",
  "items": [
    {
      "productRetailerId": "1522832641",
      "quantity": 1
    }
  ]
}
```

Esse endpoint converte `productRetailerId` para `metaProductRetailerId` e reaproveita o fluxo de criação de Draft Order.

## Checkout Web App React

O fluxo principal de compra agora é um mini app mobile-first em React, inspirado nos wireframes da LLFashion. O WhatsApp envia o link, e a cliente monta o carrinho visual com fotos, estoque, pedido mínimo, endereço, frete e checkout Nuvemshop.

URL local:

```text
http://localhost:8080/storefront/
```

Com telefone na sessão:

```text
http://localhost:8080/storefront/?phone=5521999999999
```

Para usar pelo WhatsApp/ngrok, configure:

```env
FRONTEND_BASE_URL=https://SEU-NGROK.ngrok-free.dev/storefront/
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
CART_EXPIRATION_MINUTES=120
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
```

Endpoints principais do Web App:

```http
GET    /api/storefront/session/start?phone={phone}
GET    /api/storefront/categories
GET    /api/storefront/products?categoryId=saias
GET    /api/storefront/products/{productId}
GET    /api/storefront/cart/{cartToken}
POST   /api/storefront/cart/{cartToken}/items
PUT    /api/storefront/cart/{cartToken}/items/{itemId}
DELETE /api/storefront/cart/{cartToken}/items/{itemId}
POST   /api/storefront/checkout/{cartToken}/customer
GET    /api/storefront/address/cep/{cep}
POST   /api/storefront/checkout/{cartToken}/address
POST   /api/storefront/checkout/{cartToken}/shipping-options
POST   /api/storefront/checkout/{cartToken}/select-shipping
POST   /api/storefront/checkout/{cartToken}/create-payment-link
GET    /api/orders/status/{statusPublicToken}
GET    /api/orders/status/customer?phone={phone}
POST   /api/whatsapp/send-start-link
```

Fluxo esperado:

1. Cliente manda `oi`, `comprar`, `catalogo`, `novidades`, `promocoes` ou `carrinho` no WhatsApp.
2. O backend responde com o link do Web App.
3. A cliente escolhe categoria e produtos com fotos.
4. O backend permite apenas variantes ativas e com estoque.
5. A quantidade fica limitada ao estoque.
6. O carrinho mostra quanto falta para o pedido mínimo de R$ 200,00.
7. O checkout só libera depois do mínimo.
8. Cliente informa nome, CPF/CNPJ, e-mail, telefone e endereço.
9. Cliente escolhe frete.
10. Ao gerar pagamento, o backend valida estoque novamente e cria o Draft Order na Nuvemshop.

No acompanhamento por telefone, se existir apenas um pedido, o Web App abre direto o detalhe. Se houver mais de um pedido, ele mostra uma lista com numero, data, total, pagamento, envio e botao `Ver detalhes`.

O botao de pagamento so aparece para pedidos ainda pagaveis. Pedidos pagos, cancelados, estornados, enviados ou entregues exibem apenas acompanhamento. Se a API/Nuvemshop retornar `pix_copy_paste`, a tela prioriza o Pix copia e cola; se nao vier Pix direto, usa `checkoutUrl` como fallback.

Para atualizar o frontend depois de editar `frontend/src`, rode:

```bash
cd frontend
npm install
npm run build
```

O build é publicado em `src/main/resources/static/storefront`, servido pelo Spring Boot.

## Webhook real do WhatsApp

Verificacao da Meta:

```http
GET http://localhost:8080/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=SEU_VERIFY_TOKEN&hub.challenge=123456
```

Recebimento de mensagens/carrinho:

```http
POST http://localhost:8080/api/webhooks/whatsapp
Content-Type: application/json
```

Configure `WHATSAPP_VERIFY_TOKEN` no ambiente e use a URL publica do ngrok no painel da Meta:

```text
https://SEU-NGROK.ngrok-free.dev/api/webhooks/whatsapp
```

Para enviar automaticamente o link do Web App e, depois, mensagens de pagamento pelo WhatsApp, configure também:

```env
WHATSAPP_ACCESS_TOKEN=token_da_meta_com_whatsapp_business_messaging
WHATSAPP_PHONE_NUMBER_ID=id_do_numero_da_loja
WHATSAPP_API_BASE_URL=https://graph.facebook.com/v25.0
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
FRONTEND_BASE_URL=https://SEU-NGROK.ngrok-free.dev/storefront/
```

### WhatsApp Flows

O WhatsApp Flow não é mais o fluxo principal desta versão. Ele pode continuar configurado como fallback/teste, mas a experiência recomendada é o Web App React.

Se ainda quiser manter o Flow ativo:

```env
WHATSAPP_FLOWS_ENABLED=true
WHATSAPP_FLOW_ID=id_do_flow_publicado_na_meta
WHATSAPP_FLOW_CTA=Comprar agora
WHATSAPP_FLOW_MODE=published
WHATSAPP_FLOW_PRIVATE_KEY_PATH=C:\caminho\para\flow-private-key.pem
```

Configure no Flow da Meta o Data Channel URI:

```text
https://SEU-NGROK.ngrok-free.dev/api/whatsapp/flows/data-exchange
```

Na estratégia atual:

1. Mensagens de texto comuns enviam o link do Web App React.
2. O Flow permanece disponível para cenários antigos ou testes.
3. Se ainda chegar `type = order` do catálogo antigo, o backend continua tratando como fallback.

O JSON para colar no editor da Meta fica em:

```text
docs/whatsapp-flow-checkout.json
```

O passo a passo completo esta em [docs/API_TESTES.md](docs/API_TESTES.md).

## Webhook Nuvemshop

```http
POST http://localhost:8080/api/webhooks/nuvemshop/orders
Content-Type: application/json
```

O payload completo é salvo em `webhook_event_log` com `source = NUVEMSHOP` e `processed = false`.

Eventos de produto/estoque podem apontar para:

```text
https://SEU-NGROK.ngrok-free.dev/api/webhooks/nuvemshop/products
https://SEU-NGROK.ngrok-free.dev/api/webhooks/nuvemshop/product
https://SEU-NGROK.ngrok-free.dev/api/webhooks/nuvemshop/stock
```

Quando o payload tiver `product_id`, `product` ou `id`, a aplicacao sincroniza novamente aquele produto na Nuvemshop e atualiza estoque, preco e imagem local.

Como protecao adicional, existe sincronizacao periodica configuravel:

```env
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
```

### Webhooks LGPD Nuvemshop

Use estas URLs nos campos LGPD do painel de parceiros da Nuvemshop:

```text
URL webhook store redact:
https://SEU-NGROK.ngrok-free.dev/api/webhooks/nuvemshop/store-redact

URL webhook customers redact:
https://SEU-NGROK.ngrok-free.dev/api/webhooks/nuvemshop/customers-redact

URL webhook customers data request:
https://SEU-NGROK.ngrok-free.dev/api/webhooks/nuvemshop/customers-data-request
```

Com o ngrok atual:

```text
URL webhook store redact:
https://cross-penalize-staring.ngrok-free.dev/api/webhooks/nuvemshop/store-redact

URL webhook customers redact:
https://cross-penalize-staring.ngrok-free.dev/api/webhooks/nuvemshop/customers-redact

URL webhook customers data request:
https://cross-penalize-staring.ngrok-free.dev/api/webhooks/nuvemshop/customers-data-request
```

Esses endpoints recebem qualquer JSON, salvam em `webhook_event_log` com `source = NUVEMSHOP` e retornam `200 OK`.

## Testar no Postman

1. Suba o PostgreSQL com `docker compose up -d`.
2. Configure `NUVEMSHOP_CLIENT_SECRET`.
3. Rode `mvn spring-boot:run`.
4. Chame `GET /api/health`.
5. Chame `GET /api/nuvemshop/oauth/install-url`.
6. Abra a URL de instalação no navegador e autorize o app.
7. Depois do callback OAuth, chame `POST /api/admin/nuvemshop/products/sync`.
8. Liste os mapeamentos com `GET /api/admin/products/mappings`.
9. Vincule o ID Meta com `PUT /api/admin/products/mappings/{id}/meta-retailer-id`.
10. Crie o Draft Order com `POST /api/orders/draft` ou simule o carrinho com `POST /api/mock/whatsapp/cart`.
11. Configure o webhook real do WhatsApp com `GET/POST /api/webhooks/whatsapp`.

## Segurança

- Nunca versione o Client Secret.
- Nunca logue o `access_token` completo.
- Use `NUVEMSHOP_CLIENT_SECRET` como variável de ambiente.
- O `access_token` é salvo no banco para as chamadas futuras.
- Todas as chamadas para a API da Nuvemshop enviam `User-Agent`.
- O header de autenticação enviado para a Nuvemshop é `Authentication: bearer {accessToken}`.
- Não substitua `Authentication` por `Authorization`.
