# Guia para integrar uma nova loja

Este guia documenta o passo a passo para conectar uma loja Nuvemshop ao backend da LLFashion e ao WhatsApp/Meta usando a versao publicada no Render.

URL atual de producao:

```text
https://whatsapp-checkout-nuvemshop.onrender.com
```

Web App:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/storefront/
```

## 1. Como a integracao funciona

O fluxo principal usa estas partes:

1. Nuvemshop OAuth autoriza a loja e gera `access_token`.
2. O backend salva `store_id` e `access_token` no PostgreSQL do Render.
3. O backend sincroniza produtos/variacoes da Nuvemshop para a tabela local.
4. A Meta chama o webhook quando a cliente manda mensagem no WhatsApp.
5. O backend responde com link do Web App React.
6. A cliente monta o carrinho no Web App.
7. Antes de criar o pedido, o backend valida estoque novamente.
8. O backend cria Draft Order na Nuvemshop.
9. O Web App mostra checkout/Pix e acompanhamento do pedido.

Importante: hoje a aplicacao considera uma instalacao ativa principal. Para trocar de loja, instale/autorize o app na nova loja e sincronize os produtos dela. Para operar varias lojas ao mesmo tempo, sera necessario evoluir o roteamento por loja/telefone.

## 2. Variaveis no Render

No Render, abra:

```text
whatsapp-checkout-nuvemshop > Environment
```

Confira ou preencha:

```text
NUVEMSHOP_APP_ID=32468
NUVEMSHOP_CLIENT_SECRET=client_secret_do_app_nuvemshop
WHATSAPP_VERIFY_TOKEN=llfashion_whatsapp_webhook_2026
WHATSAPP_ACCESS_TOKEN=token_da_meta
WHATSAPP_PHONE_NUMBER_ID=id_do_numero_whatsapp_da_loja
WHATSAPP_CATALOG_ID=id_do_catalogo_meta
WHATSAPP_API_BASE_URL=https://graph.facebook.com/v25.0
FRONTEND_BASE_URL=https://whatsapp-checkout-nuvemshop.onrender.com/storefront/
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
ORDER_STATUS_SYNC_ENABLED=true
ORDER_STATUS_SYNC_INTERVAL_MINUTES=5
ORDER_STATUS_SYNC_LOOKBACK_DAYS=30
```

Se usar WhatsApp Flow:

```text
WHATSAPP_FLOWS_ENABLED=true
WHATSAPP_FLOW_ID=id_do_flow_publicado
WHATSAPP_FLOW_CTA=Comprar agora
WHATSAPP_FLOW_MODE=published
WHATSAPP_FLOW_PRIVATE_KEY=chave_privada_pkcs8
```

Ou, em vez de `WHATSAPP_FLOW_PRIVATE_KEY`, use secret file no Render:

```text
WHATSAPP_FLOW_PRIVATE_KEY_PATH=/etc/secrets/flow-private-key.pem
```

Depois de mudar qualquer variavel, clique em `Save Changes` e espere o redeploy.

Nunca coloque secrets no GitHub:

- `NUVEMSHOP_CLIENT_SECRET`
- `WHATSAPP_ACCESS_TOKEN`
- chave privada do Flow
- tokens temporarios da Meta

## 3. Configurar app na Nuvemshop Partners

No painel Nuvemshop Partners, abra o app:

```text
WhatsApp Checkout
```

Na aba `Configuracao`, configure:

```text
Site do aplicativo:
https://whatsapp-checkout-nuvemshop.onrender.com
```

```text
URL de redirecionamento apos instalacao:
https://whatsapp-checkout-nuvemshop.onrender.com/api/nuvemshop/oauth/callback
```

Atencao: a URL precisa ter `/api`. Esta errada se estiver assim:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/nuvemshop/oauth/callback
```

Permissoes minimas:

```text
Products / read
Orders / read
Orders / write
```

Se aparecer permissao relacionada a Draft Orders, pedidos manuais ou checkout, habilite tambem.

## 4. Webhooks LGPD da Nuvemshop

Ainda na Nuvemshop Partners, configure:

```text
URL webhook store redact:
https://whatsapp-checkout-nuvemshop.onrender.com/api/webhooks/nuvemshop/store-redact
```

```text
URL webhook customers redact:
https://whatsapp-checkout-nuvemshop.onrender.com/api/webhooks/nuvemshop/customers-redact
```

```text
URL webhook customers data request:
https://whatsapp-checkout-nuvemshop.onrender.com/api/webhooks/nuvemshop/customers-data-request
```

## 5. Instalar o app na loja

Abra:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/api/nuvemshop/oauth/install-url
```

A resposta sera parecida com:

```json
{
  "installUrl": "https://www.nuvemshop.com.br/apps/32468/authorize"
}
```

Abra a `installUrl`, escolha a loja e autorize.

Sucesso esperado:

```text
Integracao com a Nuvemshop realizada com sucesso. Store ID: ...
```

Observacoes:

- O `code` da Nuvemshop expira em cerca de 5 minutos.
- O `code` so pode ser usado uma vez.
- Se falhar, autorize novamente para gerar outro `code`.
- Depois de trocar a URL de callback, autorize novamente o app.

## 6. Sincronizar produtos

Depois do OAuth, chame no Postman:

```http
POST https://whatsapp-checkout-nuvemshop.onrender.com/api/admin/nuvemshop/products/sync
```

Sucesso esperado:

```json
{
  "totalProductsRead": 79,
  "totalVariantsSynced": 479,
  "message": "Sincronizacao concluida com sucesso"
}
```

Listar produtos sincronizados:

```http
GET https://whatsapp-checkout-nuvemshop.onrender.com/api/admin/products/mappings
```

Se precisar vincular manualmente o ID do catalogo Meta:

```http
PUT https://whatsapp-checkout-nuvemshop.onrender.com/api/admin/products/mappings/{id}/meta-retailer-id
Content-Type: application/json
```

```json
{
  "metaProductRetailerId": "ID_DO_PRODUTO_NO_CATALOGO_META"
}
```

Se o catalogo do WhatsApp veio da Nuvemshop, normalmente os IDs/retailer IDs devem bater, mas sempre teste com um produto real.

## 7. Configurar webhook da Meta

Abra a tela do app na Meta:

```text
https://developers.facebook.com/apps/1367752418725363/webhooks/
```

Na tela de Webhooks, selecione o produto:

```text
Whatsapp Business Account
```

Nao use `User` para mensagens do WhatsApp.

Configure:

```text
URL de callback:
https://whatsapp-checkout-nuvemshop.onrender.com/api/webhooks/whatsapp
```

```text
Verificar token:
llfashion_whatsapp_webhook_2026
```

Clique em `Verificar e salvar`.

Depois assine o campo:

```text
messages
```

Sem assinar `messages`, a Meta verifica o webhook, mas nao envia as mensagens para o backend.

## 8. Token da Meta

Se o WhatsApp nao responder e o Render mostrar erro parecido com:

```text
Authentication Error
code: 190
status: 401
```

O `WHATSAPP_ACCESS_TOKEN` expirou ou esta invalido.

Para teste, gere outro token no Graph API Explorer:

```text
https://developers.facebook.com/tools/explorer/
```

Permissoes necessarias:

```text
whatsapp_business_messaging
whatsapp_business_management
```

Cole o token novo em:

```text
WHATSAPP_ACCESS_TOKEN
```

No Render, salve e espere o redeploy.

Para producao, prefira token permanente via System User no Business Manager. Token do Explorer e temporario e vai expirar.

## 9. Configurar WhatsApp Flow, se usar

No editor do Flow da Meta, configure o Data Channel / endpoint:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/api/whatsapp/flows/data-exchange
```

Depois rode:

```text
Verificacao de integridade
```

O Flow precisa da chave publica cadastrada na Meta e da chave privada correspondente configurada no Render.

## 10. Testes principais

Health check:

```http
GET https://whatsapp-checkout-nuvemshop.onrender.com/api/health
```

Web App:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/storefront/
```

Webhook da Meta:

```http
GET https://whatsapp-checkout-nuvemshop.onrender.com/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=llfashion_whatsapp_webhook_2026&hub.challenge=123456
```

Resposta esperada:

```text
123456
```

Teste no WhatsApp:

```text
oi
```

Resposta esperada:

```text
link do Web App no Render
```

Depois:

1. Abra o link.
2. Escolha produtos.
3. Confira estoque e pedido minimo.
4. Preencha dados.
5. Gere checkout/Pix.
6. Consulte `Acompanhar Pedido`.

## 11. URLs que substituem ngrok

Sempre que mudar de ambiente, troque todas as URLs publicas.

Render atual:

```text
https://whatsapp-checkout-nuvemshop.onrender.com
```

Meta Webhook:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/api/webhooks/whatsapp
```

WhatsApp Flow:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/api/whatsapp/flows/data-exchange
```

Nuvemshop OAuth:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/api/nuvemshop/oauth/callback
```

Web App:

```text
https://whatsapp-checkout-nuvemshop.onrender.com/storefront/
```

## 12. Problemas comuns

### Webhook validou, mas nao chega mensagem

Verifique:

- produto selecionado na Meta precisa ser `Whatsapp Business Account`;
- campo `messages` precisa estar assinado;
- URL precisa ser `/api/webhooks/whatsapp`;
- token de verificacao precisa bater com `WHATSAPP_VERIFY_TOKEN`.

### Mensagem chega, mas backend nao responde

Verifique logs do Render.

Se aparecer:

```text
Authentication Error code 190
```

Atualize `WHATSAPP_ACCESS_TOKEN`.

### OAuth da Nuvemshop falha

Verifique:

- `NUVEMSHOP_CLIENT_SECRET` no Render;
- callback com `/api/nuvemshop/oauth/callback`;
- `code` ainda valido e nao usado;
- app salvo na Nuvemshop Partners depois de alterar a URL.

### Produtos nao aparecem no Web App

Rode:

```http
POST /api/admin/nuvemshop/products/sync
```

Depois confira:

```http
GET /api/admin/products/mappings
```

### Render demora para responder

No plano gratuito, o servico pode hibernar. A primeira chamada depois de inatividade pode demorar.

### Banco gratuito do Render

O PostgreSQL gratuito do Render expira em 30 dias. Para uso real, migre para plano pago antes de depender dele.

## 13. Checklist rapido para uma nova loja

1. Confirmar variaveis no Render.
2. Configurar callback no app Nuvemshop.
3. Configurar webhooks LGPD.
4. Instalar app na loja pela `installUrl`.
5. Confirmar callback OAuth com sucesso.
6. Sincronizar produtos.
7. Configurar Meta Webhook em `Whatsapp Business Account`.
8. Assinar `messages`.
9. Atualizar token da Meta, se necessario.
10. Mandar `oi` no WhatsApp.
11. Abrir link do Web App.
12. Criar pedido teste.
13. Conferir pedido na Nuvemshop.
14. Conferir acompanhamento no Web App.
