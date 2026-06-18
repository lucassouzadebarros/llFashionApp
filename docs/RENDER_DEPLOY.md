# Deploy no Render

Este projeto pode subir no Render como um Web Service Docker. O Dockerfile compila o React, coloca o build em `src/main/resources/static/storefront` e depois empacota o Spring Boot.

## 1. Antes de subir

Confirme que o projeto esta em um repositorio GitHub.

Nao envie `.env`, tokens, client secret da Nuvemshop ou chave privada do WhatsApp Flow para o GitHub.

Se esta pasta ainda nao for um repositorio Git, use:

```powershell
git init
git add .
git commit -m "Preparar deploy no Render"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/whatsapp-checkout-nuvemshop.git
git push -u origin main
```

## 2. Criar pelo Blueprint

1. Acesse o Render Dashboard.
2. Clique em `New`.
3. Escolha `Blueprint`.
4. Selecione o repositorio deste projeto.
5. O Render vai ler o arquivo `render.yaml`.
6. Confirme a criacao do banco `whatsapp-checkout-db` e do servico `whatsapp-checkout-nuvemshop`.

Referencias oficiais:

- [Blueprint Spec](https://render.com/docs/blueprint-spec)
- [Docker on Render](https://render.com/docs/docker)
- [PostgreSQL on Render](https://render.com/docs/postgresql-creating-connecting)

## 3. Variaveis obrigatorias no Render

Preencha estas variaveis na tela do servico:

```text
NUVEMSHOP_CLIENT_SECRET
WHATSAPP_VERIFY_TOKEN
WHATSAPP_ACCESS_TOKEN
WHATSAPP_PHONE_NUMBER_ID
WHATSAPP_CATALOG_ID
WHATSAPP_FLOW_ID
```

Para o WhatsApp Flow, use uma destas opcoes:

```text
WHATSAPP_FLOW_PRIVATE_KEY
```

ou crie um Secret File no Render com o caminho:

```text
/etc/secrets/flow-private-key.pem
```

e mantenha:

```text
WHATSAPP_FLOW_PRIVATE_KEY_PATH=/etc/secrets/flow-private-key.pem
```

## 4. URL publica do Render

Se o servico ficar com o nome do `render.yaml`, a URL esperada sera:

```text
https://whatsapp-checkout-nuvemshop.onrender.com
```

Se o Render gerar outro nome, use a URL real exibida no dashboard.

Depois do primeiro deploy, ajuste no Render:

```text
FRONTEND_BASE_URL=https://SUA-URL-DO-RENDER.onrender.com/storefront/
```

## 5. URLs para trocar no lugar do ngrok

Substitua o dominio antigo do ngrok pela URL do Render nestes lugares.

### Meta Webhook

Produto `WhatsApp Business Account` > Webhooks:

```text
https://SUA-URL-DO-RENDER.onrender.com/api/webhooks/whatsapp
```

Use o mesmo valor de `WHATSAPP_VERIFY_TOKEN`.

### WhatsApp Flow Data Channel

No Flow publicado:

```text
https://SUA-URL-DO-RENDER.onrender.com/api/whatsapp/flows/data-exchange
```

Depois rode novamente a verificacao de integridade do Flow.

### Nuvemshop OAuth Callback

No app da Nuvemshop Partners:

```text
https://SUA-URL-DO-RENDER.onrender.com/api/nuvemshop/oauth/callback
```

Depois de trocar o callback, reinstale ou autorize novamente o app na loja, porque o `code` OAuth antigo nao deve ser reaproveitado.

### Nuvemshop Webhooks LGPD

```text
https://SUA-URL-DO-RENDER.onrender.com/api/webhooks/nuvemshop/store-redact
https://SUA-URL-DO-RENDER.onrender.com/api/webhooks/nuvemshop/customers-redact
https://SUA-URL-DO-RENDER.onrender.com/api/webhooks/nuvemshop/customers-data-request
```

### Nuvemshop Webhook de pedidos/produtos

```text
https://SUA-URL-DO-RENDER.onrender.com/api/webhooks/nuvemshop/orders
https://SUA-URL-DO-RENDER.onrender.com/api/webhooks/nuvemshop/products
```

## 6. Testes depois do deploy

Health check:

```text
https://SUA-URL-DO-RENDER.onrender.com/api/health
```

Web App:

```text
https://SUA-URL-DO-RENDER.onrender.com/storefront/
```

URL de instalacao da Nuvemshop:

```text
https://SUA-URL-DO-RENDER.onrender.com/api/nuvemshop/oauth/install-url
```

Sincronizar produtos:

```text
POST https://SUA-URL-DO-RENDER.onrender.com/api/admin/nuvemshop/products/sync
```

## 7. Observacoes importantes

- O plano gratuito do Render pode hibernar quando fica sem uso. A primeira chamada depois de um tempo pode demorar.
- Bancos PostgreSQL gratuitos do Render expiram em 30 dias. Para uso real, migre para um plano pago antes de depender desse banco em producao.
- Se o dominio do Render mudar, atualize `FRONTEND_BASE_URL`, Meta Webhook, WhatsApp Flow e Nuvemshop.
- Tokens temporarios da Meta expiram. Para producao, use token permanente via System User no Business Manager.
- O banco local do Docker nao vai junto para o Render. O Render criara um PostgreSQL novo e o Flyway criara as tabelas.
