# Documentacao de testes da API

Este guia explica como testar a API `whatsapp-checkout-nuvemshop` ponta a ponta: OAuth da Nuvemshop, token, sincronizacao de produtos, criacao de Draft Order e simulacao do futuro fluxo WhatsApp.

## Visao geral do fluxo

1. Subir PostgreSQL.
2. Configurar `NUVEMSHOP_CLIENT_SECRET`.
3. Rodar a aplicacao.
4. Gerar URL de instalacao do app.
5. Autorizar o app na Nuvemshop.
6. Receber callback OAuth e salvar `access_token` no banco.
7. Sincronizar produtos/variacoes.
8. Listar `product_mapping`.
9. Criar pedido Draft Order usando `nuvemshopVariantId` ou `metaProductRetailerId`.
10. Pegar `checkoutUrl` e enviar ao cliente futuramente via WhatsApp.

## Como rodar o ambiente

Suba o banco:

```powershell
docker compose up -d
```

Configure as variaveis de ambiente no mesmo terminal em que voce vai rodar a aplicacao:

```powershell
$env:NUVEMSHOP_APP_ID="32468"
$env:NUVEMSHOP_CLIENT_SECRET="COLE_AQUI_O_CLIENT_SECRET"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/whatsapp_checkout"
$env:DATABASE_USERNAME="postgres"
$env:DATABASE_PASSWORD="postgres"
$env:WHATSAPP_VERIFY_TOKEN="llfashion_whatsapp_webhook_2026"
$env:CHECKOUT_MINIMUM_ORDER_TOTAL="200.00"
$env:STOCK_SYNC_ENABLED="true"
$env:STOCK_SYNC_INTERVAL_MINUTES="10"
```

Rode a aplicacao:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

Teste se subiu:

```http
GET http://localhost:8080/api/health
```

Resposta esperada:

```json
{
  "status": "UP",
  "application": "whatsapp-checkout-nuvemshop"
}
```

## Como funciona o token da Nuvemshop

O token usado pela API nao deve ser colocado no codigo.

O fluxo correto e:

1. O usuario instala/autoriza o app na Nuvemshop.
2. A Nuvemshop redireciona para:

```text
http://localhost:8080/api/nuvemshop/oauth/callback?code=...
```

ou, usando ngrok:

```text
https://SEU-NGROK.ngrok-free.dev/api/nuvemshop/oauth/callback
```

3. O backend troca esse `code` por `access_token`.
4. O backend salva o `access_token` na tabela `nuvemshop_installation`.
5. Todas as chamadas futuras para produtos e pedidos usam o token ativo mais recente salvo no banco.

Pontos importantes:

- O `code` expira em aproximadamente 5 minutos.
- O `code` so pode ser usado uma vez.
- Se a troca falhar, gere um novo `code` autorizando o app novamente.
- Se voce testar manualmente com curl usando um token diferente do token salvo no banco, a API Java pode falhar mesmo que o curl funcione.
- O backend usa o token salvo no banco, nao o token que voce colou em um curl manual.
- Nunca envie o token completo em chat, print ou commit.

Para ver qual token esta salvo, sem revelar o token completo:

```powershell
docker exec whatsapp-checkout-postgres psql -U postgres -d whatsapp_checkout -c "select store_id, active, token_type, scope, length(access_token) as token_length, left(access_token, 8) || '...' as token_preview, created_at, updated_at from nuvemshop_installation order by updated_at desc;"
```

Para testar se o token salvo no banco e aceito pela Nuvemshop:

```powershell
$token = (docker exec whatsapp-checkout-postgres psql -U postgres -d whatsapp_checkout -t -A -c "select access_token from nuvemshop_installation where active = true order by updated_at desc limit 1;").Trim()

curl.exe --silent --show-error --connect-timeout 10 --max-time 20 --output NUL --write-out "%{http_code}" "https://api.nuvemshop.com.br/2025-03/7740046/products?page=1&per_page=1" `
  -H "Authentication: bearer $token" `
  -H "User-Agent: WhatsApp Checkout (lucassouzadebarros@gmail.com)" `
  -H "Accept: application/json"
```

Resultado esperado:

```text
200
```

Se retornar `401`, o token salvo no banco esta invalido. Reautorize o app pela Nuvemshop para gerar e salvar um token novo.

## OAuth da Nuvemshop

### Gerar URL de instalacao

```http
GET http://localhost:8080/api/nuvemshop/oauth/install-url
```

Resposta:

```json
{
  "installUrl": "https://www.nuvemshop.com.br/apps/32468/authorize"
}
```

Abra a `installUrl` no navegador, autorize o app e aguarde o callback.

### Callback OAuth

Chamado pela Nuvemshop:

```http
GET http://localhost:8080/api/nuvemshop/oauth/callback?code=CODIGO_DA_NUVEMSHOP
```

Resposta de sucesso:

```text
Integracao com a Nuvemshop realizada com sucesso. Store ID: 7740046
```

O backend salva:

- `store_id`: vem do `user_id` da Nuvemshop.
- `access_token`: token usado nas chamadas futuras.
- `scope`: permissoes concedidas.
- `active`: `true`.

## Produtos

### Sincronizar produtos da Nuvemshop

```http
POST http://localhost:8080/api/admin/nuvemshop/products/sync
```

Resposta esperada:

```json
{
  "totalProductsRead": 1,
  "totalVariantsSynced": 1,
  "message": "Sincronizacao concluida com sucesso"
}
```

Observacoes:

- A API busca `/products?page=1&per_page=200`.
- Se a pagina voltar com menos de 200 produtos, a sincronizacao para.
- Produto sem variacao nao quebra o sync; apenas nao gera `product_mapping`.
- O nome do produto pode vir como objeto, por exemplo `"name": {"pt": "Teste produto lucas"}`. A API usa `name.pt` quando existir.

### Listar mapeamentos sincronizados

```http
GET http://localhost:8080/api/admin/products/mappings
```

Exemplo de resposta:

```json
[
  {
    "id": "372ae476-7c09-438d-be5b-5d50217950b7",
    "nuvemshopProductId": 346039071,
    "nuvemshopVariantId": 1529914260,
    "sku": null,
    "metaProductRetailerId": null,
    "productName": "Teste produto lucas",
    "variantName": null,
    "price": 10.00,
    "stock": null,
    "active": true
  }
]
```

Filtros opcionais:

```http
GET http://localhost:8080/api/admin/products/mappings?active=true
GET http://localhost:8080/api/admin/products/mappings?productName=Teste
GET http://localhost:8080/api/admin/products/mappings?sku=ABC
GET http://localhost:8080/api/admin/products/mappings?metaProductRetailerId=1522832641
```

### Vincular produto ao catalogo Meta/WhatsApp

Use este endpoint quando quiser criar pedido usando `metaProductRetailerId`, simulando o produto vindo do catalogo do WhatsApp.

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

Resposta:

```json
{
  "id": "372ae476-7c09-438d-be5b-5d50217950b7",
  "nuvemshopProductId": 346039071,
  "nuvemshopVariantId": 1529914260,
  "sku": null,
  "metaProductRetailerId": "1522832641",
  "productName": "Teste produto lucas",
  "variantName": null,
  "price": 10.00,
  "stock": null,
  "active": true
}
```

## Pedidos / Draft Order

### Criar pedido usando `nuvemshopVariantId`

```http
POST http://localhost:8080/api/orders/draft
Content-Type: application/json
```

Body:

```json
{
  "customerName": "Maria",
  "customerLastname": "Cliente",
  "customerEmail": "",
  "customerPhone": "5521999999999",
  "items": [
    {
      "nuvemshopVariantId": 1529914260,
      "quantity": 1
    }
  ]
}
```

Resposta de sucesso:

```json
{
  "localOrderId": "b0d753e9-6ea4-4731-a4b6-5cc8aec1a3da",
  "nuvemshopDraftOrderId": 1977487716,
  "status": "AGUARDANDO_PAGAMENTO",
  "checkoutUrl": "https://checkouttransparente.lojavirtualnuvem.com.br/checkout/v3/start/...",
  "abandonedCheckoutUrl": "https://checkouttransparente.lojavirtualnuvem.com.br/checkout/v3/proxy/...",
  "message": "Pedido criado com sucesso. Envie o checkoutUrl para o cliente finalizar o pagamento."
}
```

O backend salva:

- Pedido local em `whatsapp_order`.
- Itens em `whatsapp_order_item`.
- Status local como `AGUARDANDO_PAGAMENTO`.
- `checkout_url` retornado pela Nuvemshop.
- `abandoned_checkout_url`, se existir.
- Resposta bruta da Nuvemshop em `raw_nuvemshop_response`.

### Criar pedido usando `metaProductRetailerId`

Antes, vincule o `metaProductRetailerId` no endpoint de mapeamento.

Depois:

```http
POST http://localhost:8080/api/orders/draft
Content-Type: application/json
```

Body:

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

### Consultar Draft Order na Nuvemshop

```http
GET http://localhost:8080/api/orders/draft/{draftOrderId}
```

Exemplo:

```http
GET http://localhost:8080/api/orders/draft/1977487716
```

Resposta:

```json
{
  "id": 1977487716,
  "status": "open",
  "paymentStatus": "pending",
  "checkoutUrl": "https://...",
  "total": 10.00
}
```

### Consultar pedido local

```http
GET http://localhost:8080/api/orders/{localOrderId}
```

Exemplo:

```http
GET http://localhost:8080/api/orders/b0d753e9-6ea4-4731-a4b6-5cc8aec1a3da
```

## Mock do WhatsApp

Este endpoint simula o futuro carrinho vindo do catalogo do WhatsApp.

```http
POST http://localhost:8080/api/mock/whatsapp/cart
Content-Type: application/json
```

Body:

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

O backend converte:

```text
productRetailerId -> metaProductRetailerId
```

e reaproveita o mesmo fluxo de criacao de Draft Order.

## Webhook real do WhatsApp

O webhook real da Meta usa dois endpoints:

```http
GET /api/webhooks/whatsapp
POST /api/webhooks/whatsapp
```

### Configurar no painel da Meta

Exponha a aplicacao local com ngrok:

```powershell
ngrok http 8080
```

No painel da Meta:

```text
developers.facebook.com
→ seu app
→ WhatsApp
→ Configuration
→ Webhook
→ Edit
```

Configure:

```text
Callback URL:
https://SEU-NGROK.ngrok-free.dev/api/webhooks/whatsapp

Verify Token:
llfashion_whatsapp_webhook_2026
```

O `Verify Token` e um texto criado por voce. Ele precisa ser igual ao valor da variavel:

```powershell
$env:WHATSAPP_VERIFY_TOKEN="llfashion_whatsapp_webhook_2026"
```

Depois de verificar e salvar, assine o campo:

```text
messages
```

## WhatsApp Flows para compra com estoque controlado

O fluxo principal recomendado agora e usar WhatsApp Flows para evitar que o cliente selecione uma quantidade maior que o estoque.

Configure as variaveis:

```env
WHATSAPP_FLOWS_ENABLED=true
WHATSAPP_FLOW_ID=id_do_flow_publicado_na_meta
WHATSAPP_FLOW_CTA=Comprar agora
WHATSAPP_FLOW_MODE=published
WHATSAPP_FLOW_PRIVATE_KEY_PATH=C:\caminho\para\flow-private-key.pem
```

Endpoint para configurar no Data Channel URI do Flow:

```text
https://SEU-NGROK.ngrok-free.dev/api/whatsapp/flows/data-exchange
```

Com o ngrok atual:

```text
https://cross-penalize-staring.ngrok-free.dev/api/whatsapp/flows/data-exchange
```

Observacao importante: chamadas reais da Meta para o endpoint de WhatsApp Flows chegam criptografadas. A aplicacao aceita:

- Payload JSON aberto, para testes locais/Postman.
- Payload criptografado da Meta com `encrypted_flow_data`, `encrypted_aes_key` e `initial_vector`.

Para o modo criptografado funcionar, configure uma chave privada PKCS#8:

```text
-----BEGIN PRIVATE KEY-----
...
-----END PRIVATE KEY-----
```

Se a chave estiver em formato `-----BEGIN RSA PRIVATE KEY-----`, converta para PKCS#8 antes de usar.

### Como o backend usa o Flow

Quando chega um carrinho/pedido direto do catalogo e `WHATSAPP_FLOW_ID` esta configurado:

1. O backend identifica o primeiro produto do carrinho.
2. Cria uma sessao em `whatsapp_flow_session`.
3. Envia uma mensagem interativa do WhatsApp Flow para o cliente.
4. O Flow chama `/api/whatsapp/flows/data-exchange`.
5. O backend devolve apenas variacoes com estoque maior que zero.
6. Quando a variacao e escolhida, o backend devolve apenas as quantidades possiveis.
7. Na confirmacao final, o backend valida estoque novamente.
8. Se ainda houver estoque, cria o Draft Order na Nuvemshop.
9. O cliente recebe o link de pagamento pelo WhatsApp.

Se `WHATSAPP_FLOW_ID` nao estiver configurado, o backend mantem o fluxo antigo como fallback.

### Contrato esperado pelo Flow

O endpoint aceita requests com:

```json
{
  "version": "3.0",
  "action": "INIT",
  "flow_token": "token_da_sessao",
  "screen": "",
  "data": {}
}
```

Resposta inicial:

```json
{
  "version": "3.0",
  "screen": "CHOOSE_VARIANT",
  "data": {
    "flow_token": "token_da_sessao",
    "product_name": "Vestido Gota Curto Suplex",
    "variants": [
      {
        "id": "1514131727",
        "title": "Azul / Unico",
        "description": "Estoque: 3 | R$ 35,00"
      }
    ]
  }
}
```

Ao escolher a variacao, o Flow deve chamar o endpoint com:

```json
{
  "version": "3.0",
  "action": "data_exchange",
  "flow_token": "token_da_sessao",
  "screen": "CHOOSE_VARIANT",
  "data": {
    "variant_id": "1514131727"
  }
}
```

Resposta de quantidade:

```json
{
  "version": "3.0",
  "screen": "CHOOSE_QUANTITY",
  "data": {
    "flow_token": "token_da_sessao",
    "product_name": "Vestido Gota Curto Suplex",
    "variant_name": "Azul / Unico",
    "unit_price": "R$ 35,00",
    "quantities": [
      {
        "id": "1",
        "title": "1 unidade"
      },
      {
        "id": "2",
        "title": "2 unidades"
      },
      {
        "id": "3",
        "title": "3 unidades"
      }
    ]
  }
}
```

Na confirmacao final, envie os dados do cliente e endereco:

```json
{
  "version": "3.0",
  "action": "data_exchange",
  "flow_token": "token_da_sessao",
  "screen": "CONFIRM_ORDER",
  "data": {
    "quantity": "2",
    "full_name": "Lucas Souza",
    "cpf": "12345678901",
    "email": "lucas@email.com",
    "postal_code": "22041001",
    "street": "Rua Exemplo",
    "number": "123",
    "complement": "Apto 201",
    "neighborhood": "Centro",
    "city": "Rio de Janeiro",
    "state": "RJ",
    "confirm_order": true
  }
}
```

Resposta de sucesso:

```json
{
  "version": "3.0",
  "screen": "SUCCESS",
  "data": {
    "flow_token": "token_da_sessao",
    "local_order_id": "uuid",
    "checkout_url": "https://...",
    "message": "Pedido criado com sucesso. Enviamos o link de pagamento no WhatsApp."
  }
}
```

### Verificacao GET da Meta

Quando voce clicar em Verify and Save, a Meta chama:

```http
GET https://SEU-NGROK.ngrok-free.dev/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=llfashion_whatsapp_webhook_2026&hub.challenge=123456
```

O backend responde:

```text
123456
```

Teste local:

```powershell
curl.exe "http://localhost:8080/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=llfashion_whatsapp_webhook_2026&hub.challenge=123456"
```

### Recebimento POST de carrinho do catalogo

Quando o cliente envia o carrinho pelo catalogo do WhatsApp, a Meta manda um payload com `type = order`.

O fluxo atual nao cria o pedido na Nuvemshop imediatamente. A API faz assim:

1. Recebe o carrinho do catalogo.
2. Calcula o subtotal usando os precos salvos em `product_mapping`.
3. Se algum item pedir mais unidades do que o estoque sincronizado, envia uma mensagem pedindo para ajustar a quantidade.
4. Se o subtotal for menor que `CHECKOUT_MINIMUM_ORDER_TOTAL`, envia uma mensagem informando quanto falta.
5. Se o subtotal bater o minimo, cria uma sessao em `whatsapp_checkout_session` e pergunta o nome completo.
6. Depois pergunta CPF/CNPJ.
7. Depois pergunta e-mail.
8. Depois pergunta CEP.
9. A API consulta o CEP no ViaCEP para preencher rua, bairro, cidade e UF.
10. Depois pergunta o numero da casa/apartamento e complemento opcional.
11. Com esses dados completos, a API cria o Draft Order na Nuvemshop e envia o `checkoutUrl`.

O pedido minimo padrao e `R$ 200,00` e pode ser alterado por:

```env
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
```

Exemplo para simular localmente:

```http
POST http://localhost:8080/api/webhooks/whatsapp
Content-Type: application/json
```

Body:

```json
{
  "object": "whatsapp_business_account",
  "entry": [
    {
      "changes": [
        {
          "field": "messages",
          "value": {
            "contacts": [
              {
                "profile": {
                  "name": "Maria Cliente"
                },
                "wa_id": "5521999999999"
              }
            ],
            "messages": [
              {
                "from": "5521999999999",
                "id": "wamid.TESTE",
                "timestamp": "1779500000",
                "type": "order",
                "order": {
                  "catalog_id": "SEU_CATALOG_ID",
                  "product_items": [
                    {
                      "product_retailer_id": "1529914260",
                      "quantity": 1,
                      "item_price": 10,
                      "currency": "BRL"
                    }
                  ]
                }
              }
            ]
          }
        }
      ]
    }
  ]
}
```

Resposta esperada:

```json
{
  "ordersCreated": 0,
  "orders": [],
  "message": "Webhook recebido. Sessoes de carrinho iniciadas: 1. Resposta(s) enviada(s): 1. Pedido(s) criado(s): 0. Link(s) de pagamento enviado(s): 0"
}
```

Como o seu catalogo da Meta veio da Nuvemshop, o backend tenta tratar `product_retailer_id` numerico como `nuvemshopVariantId`.

Exemplo:

```text
product_retailer_id = 1529914260
↓
nuvemshopVariantId = 1529914260
↓
Draft Order na Nuvemshop
```

Se o `product_retailer_id` nao for numerico, o backend trata como `metaProductRetailerId` e busca o vinculo em `product_mapping`.

### Simular as respostas da conversa

Depois do carrinho, envie mensagens `type = text` com o mesmo telefone. A primeira resposta esperada agora e o nome completo:

```http
POST http://localhost:8080/api/webhooks/whatsapp
Content-Type: application/json
```

```json
{
  "object": "whatsapp_business_account",
  "entry": [
    {
      "changes": [
        {
          "field": "messages",
          "value": {
            "metadata": {
              "phone_number_id": "971723436031703"
            },
            "messages": [
              {
                "from": "5521999999999",
                "id": "wamid.TESTE_EMAIL",
                "timestamp": "1779500001",
                "type": "text",
                "text": {
                  "body": "Lucas Souza"
                }
              }
            ]
          }
        }
      ]
    }
  ]
}
```

Resposta esperada para nome, CPF, e-mail, CEP e numero:

```json
{
  "ordersCreated": 0,
  "orders": [],
  "message": "Webhook recebido. Sessoes de carrinho iniciadas: 0. Resposta(s) enviada(s): 1. Pedido(s) criado(s): 0. Link(s) de pagamento enviado(s): 0"
}
```

Repita o mesmo formato alterando apenas `id`, `timestamp` e `text.body`, nesta ordem:

```text
1. Nome completo: Lucas Souza
2. CPF: 12345678901
3. E-mail: cliente@email.com
4. CEP: 22041001
5. Numero: 123
6. Complemento: Apto 201
```

Se nao houver complemento, envie:

```text
sem complemento
```

Na resposta do complemento, o pedido ja deve ser criado:

```json
{
  "ordersCreated": 1,
  "orders": [
    {
      "localOrderId": "uuid",
      "nuvemshopDraftOrderId": 1977487716,
      "status": "AGUARDANDO_PAGAMENTO",
      "checkoutUrl": "https://...",
      "abandonedCheckoutUrl": "https://...",
      "total": 235.00,
      "message": "Pedido criado com sucesso. Envie o checkoutUrl para o cliente finalizar o pagamento."
    }
  ],
  "message": "Webhook recebido. Sessoes de carrinho iniciadas: 0. Resposta(s) enviada(s): 0. Pedido(s) criado(s): 1. Link(s) de pagamento enviado(s): 1"
}
```

### Conversa automatica ate o link de pagamento

Quando o webhook do WhatsApp vier com `type = order`, o backend primeiro valida o pedido minimo e coleta dados do cliente antes de criar o Draft Order.

Configure as variaveis:

```env
WHATSAPP_ACCESS_TOKEN=token_da_meta_com_whatsapp_business_messaging
WHATSAPP_PHONE_NUMBER_ID=971723436031703
WHATSAPP_API_BASE_URL=https://graph.facebook.com/v25.0
```

Se `WHATSAPP_PHONE_NUMBER_ID` estiver vazio, o backend tenta usar o `metadata.phone_number_id` que vem no webhook. O `WHATSAPP_ACCESS_TOKEN` precisa estar configurado para enviar a mensagem.

Em testes locais com telefone falso ou token ausente, a sessao e o pedido ainda podem ser criados, mas os contadores `Resposta(s) enviada(s)` e `Link(s) de pagamento enviado(s)` podem ficar `0`.

Se o carrinho estiver abaixo do minimo:

```text
Recebi seu carrinho, mas o pedido minimo da L&L Fashion e de R$ 200,00.

Subtotal do carrinho: R$ 35,00
Faltam: R$ 165,00

Adicione mais produtos ao carrinho e envie novamente para eu gerar o link de pagamento.
```

Se algum item nao tiver estoque suficiente:

```text
Recebi seu carrinho, mas nao consigo gerar o pedido porque a quantidade solicitada nao esta disponivel.

Vestido Gota Curto Suplex - Azul / Unico: solicitado 4, disponivel 2.

Ajuste a quantidade no carrinho e envie novamente para eu continuar.
```

Se o carrinho bater o minimo:

```text
Ola, Lucas! Recebi seu carrinho no valor de R$ 235,00.

Para gerar seu link de pagamento, me envie o nome completo do cliente.
```

Depois que o cliente envia o nome completo:

```text
Obrigado. Agora me envie o CPF do cliente, somente numeros.
```

Depois que o cliente envia CPF/CNPJ valido:

```text
Perfeito. Agora me envie o e-mail do cliente.
```

Depois que o cliente envia um e-mail valido:

```text
Agora me envie o CEP de entrega com 8 digitos.
```

Depois que o cliente envia um CEP valido:

```text
Encontrei o endereco: Rua Exemplo, Centro, Rio de Janeiro/RJ.

Agora me envie o numero da casa ou apartamento.
```

Depois que o cliente envia o numero:

```text
Tem complemento? Exemplo: bloco, casa, apto ou referencia.

Se nao tiver, responda: sem complemento
```

Depois que o cliente envia o complemento, ou responde `sem complemento`, o backend cria o Draft Order e envia:

```text
Ola, Lucas Souza! Seu pedido foi criado com sucesso.

Total: R$ 35,00
Link para pagamento:
https://llfashionmoda.com.br/checkout/v3/start/...

Assim que o pagamento for finalizado, acompanharemos por aqui.
```

### Idempotencia do carrinho WhatsApp

O backend salva o `messages[].id` do carrinho na sessao `whatsapp_checkout_session.whatsapp_message_id`.

Quando o cliente informa o CEP e o Draft Order e criado, esse mesmo `wamid` tambem fica salvo em `whatsapp_order.whatsapp_message_id`.

Se a Meta reenviar o mesmo webhook do carrinho, o backend encontra a sessao pelo `wamid` e nao duplica a conversa nem o Draft Order.

Para consultar as sessoes:

```powershell
docker exec whatsapp-checkout-postgres psql -U postgres -d whatsapp_checkout -c "select id, whatsapp_message_id, customer_name, customer_lastname, customer_document, customer_phone, status, subtotal, customer_email, postal_code, address_street, address_number, address_neighborhood, address_city, address_state, local_order_id, created_at, updated_at from whatsapp_checkout_session order by updated_at desc limit 10;"
```

### Dados enviados para a Nuvemshop

Ao criar o Draft Order, o payload passa a incluir:

```json
{
  "contact_name": "Lucas",
  "contact_lastname": "Souza",
  "contact_email": "lucas@email.com",
  "contact_phone": "5521999999999",
  "cpf_cnpj": "12345678901",
  "payment_status": "unpaid",
  "sale_channel": "WhatsApp",
  "products": [
    {
      "variant_id": 1514131727,
      "quantity": 1
    }
  ],
  "shipping": [
    {
      "cost": "0.00",
      "shipping_address": {
        "address": "Rua Exemplo",
        "number": "123",
        "floor": "Apto 201",
        "locality": "Centro",
        "city": "Rio de Janeiro",
        "province": "RJ",
        "zipcode": "22041001"
      }
    }
  ]
}
```

A documentacao oficial da Nuvemshop 2025-03 lista `cpf_cnpj` no `POST /draft_orders` e os campos de endereco dentro de `shipping.shipping_address`.

## Webhook da Nuvemshop

Endpoint para receber eventos da Nuvemshop:

```http
POST http://localhost:8080/api/webhooks/nuvemshop/orders
Content-Type: application/json
```

Body exemplo:

```json
{
  "event": "order/updated",
  "id": 1977487716,
  "payment_status": "paid"
}
```

O backend salva o payload em `webhook_event_log` com:

- `source = NUVEMSHOP`
- `processed = false`
- `payload = JSON completo`

Se conseguir identificar pedido pago/cancelado, tenta atualizar o status local.

Para atualizacao de produtos, imagens e estoque, configure tambem os webhooks:

```http
POST /api/webhooks/nuvemshop/products
POST /api/webhooks/nuvemshop/product
POST /api/webhooks/nuvemshop/stock
```

Quando o payload tiver `product_id`, `product` ou `id`, o backend busca o produto na Nuvemshop e atualiza as variacoes locais.

A aplicacao tambem possui sincronizacao periodica de estoque:

```env
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
```

### Webhooks LGPD da Nuvemshop

No painel de parceiros da Nuvemshop, use estas URLs nos campos de LGPD:

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

Teste local:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/webhooks/nuvemshop/store-redact" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"store_id":7740046,"reason":"local-test"}'
```

Os tres endpoints retornam `200 OK` rapidamente e salvam o payload em `webhook_event_log`.

## Ordem recomendada para testar no Postman

1. `GET /api/health`
2. `GET /api/nuvemshop/oauth/install-url`
3. Abrir a `installUrl` no navegador e autorizar o app.
4. Conferir no banco se o token foi salvo.
5. Testar o token salvo contra `/products` da Nuvemshop.
6. `POST /api/admin/nuvemshop/products/sync`
7. `GET /api/admin/products/mappings`
8. `POST /api/orders/draft` usando `nuvemshopVariantId`
9. Abrir o `checkoutUrl` retornado.
10. `GET /api/orders/{localOrderId}`
11. Opcional: vincular `metaProductRetailerId`.
12. Opcional: `POST /api/mock/whatsapp/cart`.

## WhatsApp Flow com carrinho

O JSON atual do Flow fica em:

```text
docs/whatsapp-flow-checkout.json
```

Para copiar e colar na Meta:

```powershell
Get-Content "C:\Users\lucas\Downloads\whatsapp-checkout-nuvemshop\whatsapp-checkout-nuvemshop\docs\whatsapp-flow-checkout.json" -Raw | Set-Clipboard
```

Fluxo implementado:

1. Cliente escolhe um produto no catalogo.
2. Backend abre o Flow daquele produto.
3. Flow mostra variacoes disponiveis.
4. Flow mostra somente quantidades permitidas pelo estoque restante.
5. Cliente escolhe `Adicionar mais produtos` ou `Finalizar pedido`.
6. Ao adicionar mais, o item fica salvo no carrinho local e o cliente pode voltar ao catalogo.
7. Ao finalizar, o Flow pede nome, CPF, e-mail, CEP e endereco.
8. Antes de criar o Draft Order, o backend valida o estoque de todos os itens novamente.
9. O backend cria um unico Draft Order com todos os itens do carrinho e envia o link de pagamento.

Enquanto o Flow nao puder ser publicado, use modo draft:

```env
WHATSAPP_FLOW_ID=960551789932506
WHATSAPP_FLOW_MODE=draft
```

Quando publicar o Flow, troque para:

```env
WHATSAPP_FLOW_MODE=published
```

## Troubleshooting

### `NUVEMSHOP_CLIENT_SECRET nao configurado`

A variavel de ambiente nao foi configurada no terminal/processo que roda a aplicacao.

Configure e reinicie:

```powershell
$env:NUVEMSHOP_CLIENT_SECRET="COLE_AQUI_O_CLIENT_SECRET"
mvn spring-boot:run
```

### `Invalid access token`

O token salvo no banco nao e valido para a Nuvemshop.

Possiveis causas:

- Voce testou no curl com outro token.
- O app foi reinstalado e o token antigo ficou salvo.
- O token foi copiado errado manualmente.
- A autorizacao OAuth nao foi refeita depois de mudar permissoes/scopes.

Solucao recomendada: gere um novo OAuth pelo endpoint de install URL e autorize o app de novo.

### O curl funciona, mas a API Java falha

Compare o token usado no curl com o token salvo no banco. A API Java usa apenas o token salvo em `nuvemshop_installation`.

Confira o preview:

```powershell
docker exec whatsapp-checkout-postgres psql -U postgres -d whatsapp_checkout -c "select left(access_token, 8) || '...' as token_preview, updated_at from nuvemshop_installation where active = true order by updated_at desc limit 1;"
```

### `Content-Type: text/html` com body JSON

A Nuvemshop pode retornar `Content-Type: text/html` mesmo com body JSON valido em alguns endpoints. A API aceita esse caso quando o body comeca com `{` ou `[`, registra aviso no log e converte manualmente.

### Produto nao encontrado para `nuvemshopVariantId`

O pedido usa a tabela local `product_mapping`. Rode:

```http
POST /api/admin/nuvemshop/products/sync
GET /api/admin/products/mappings
```

Use o `nuvemshopVariantId` retornado pela listagem.

### Produto nao encontrado para `metaProductRetailerId`

Voce precisa vincular primeiro:

```http
PUT /api/admin/products/mappings/{id}/meta-retailer-id
```

Depois use o mesmo valor no pedido ou no mock WhatsApp.

## Headers usados pelo backend na Nuvemshop

Para produtos e Draft Orders, o backend envia:

```http
Authentication: bearer {accessToken}
User-Agent: WhatsApp Checkout (lucassouzadebarros@gmail.com)
Accept: application/json
```

Quando envia body JSON, tambem envia:

```http
Content-Type: application/json
```

Importante: a Nuvemshop usa `Authentication`, nao `Authorization`.
