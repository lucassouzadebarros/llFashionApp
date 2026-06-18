# Checkout React Web App LLFashion

Este é o fluxo principal atual de compra.

O WhatsApp não renderiza mais o carrinho principal. Ele envia um link para o Web App React, e o cliente monta o pedido em uma tela mobile com fotos, categorias, estoque, pedido mínimo, endereço, frete e checkout Nuvemshop.

## URL do Web App

Local:

```text
http://localhost:8080/storefront/
```

Com sessão por telefone:

```text
http://localhost:8080/storefront/?phone=5521999999999
```

Com ngrok:

```text
https://SEU-NGROK.ngrok-free.dev/storefront/?phone=5521999999999
```

Configure o link público no `.env`:

```env
FRONTEND_BASE_URL=https://SEU-NGROK.ngrok-free.dev/storefront/
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
CART_EXPIRATION_MINUTES=120
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
```

## Como testar

1. Suba o banco com `docker compose up -d`.
2. Rode a aplicação com Java 17.
3. Abra `http://localhost:8080/storefront/`.
4. Clique em `Comprar por categoria`.
5. Escolha uma categoria, por exemplo `Saias`.
6. Abra um produto.
7. Escolha uma variação disponível.
8. Escolha uma quantidade. A tela limita a quantidade ao estoque.
9. Clique em `Adicionar ao carrinho`.
10. Adicione produtos até passar de R$ 200,00.
11. Clique em `Finalizar pedido`.
12. Preencha nome, CPF/CNPJ, e-mail e telefone.
13. Preencha endereço.
14. Escolha uma forma de envio.
15. Revise o pedido.
16. Clique em `Gerar link de pagamento` apenas quando quiser criar o Draft Order real na Nuvemshop.

## Fluxo pelo WhatsApp

Quando o cliente mandar `oi`, `comprar`, `catalogo`, `novidades`, `promocoes` ou `carrinho`, o backend responde com:

```text
Bem-vinda a LLFashion Moda!

Trabalhamos com moda feminina no atacado.
Pedido minimo no atacado: R$ 200,00.

Para montar seu pedido com fotos, tamanhos e estoque atualizado, acesse o link abaixo:
{FRONTEND_BASE_URL}?phone={telefone}
```

Se ainda chegar carrinho nativo do catálogo do WhatsApp (`type = order`), a aplicação responde com o link do Web App para refazer/revisar a compra no checkout visual.

## Endpoints usados pelo React

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
POST   /api/storefront/checkout/{cartToken}/address
GET    /api/storefront/address/cep/{cep}
POST   /api/storefront/checkout/{cartToken}/shipping-options
POST   /api/storefront/checkout/{cartToken}/select-shipping
POST   /api/storefront/checkout/{cartToken}/create-payment-link
GET    /api/orders/status/{statusPublicToken}
GET    /api/orders/status/customer?phone={phone}
POST   /api/whatsapp/send-start-link
```

## Imagens

As imagens vêm da Nuvemshop e são salvas em `product_mapping.image_url` durante a sincronização.

Se o Web App mostrar o placeholder `LLFashion Moda`, rode novamente:

```http
POST http://localhost:8080/api/admin/nuvemshop/products/sync
```

Depois confira:

```http
GET http://localhost:8080/api/storefront/products?categoryId=saias
```

Se `imageUrl` ainda vier vazio ou com placeholder, o produto provavelmente está sem imagem sincronizada/cadastrada na Nuvemshop.

## Regras mantidas no backend

- Pedido mínimo de R$ 200,00.
- Carrinho com vários produtos.
- Produto sem estoque não aparece como comprável.
- Variação sem estoque não aparece como comprável.
- Quantidade não passa do estoque disponível.
- Estoque é validado ao adicionar item, alterar quantidade e antes de criar o Draft Order.
- O link final de pagamento vem do `checkout_url` ou `abandoned_checkout_url` retornado pela Nuvemshop.

## Recuperacao segura do carrinho

O link recomendado para o cliente agora usa um token opaco:

```text
https://SEU-NGROK.ngrok-free.dev/storefront/?token={cartToken}
```

O backend cria esse `cartToken` e nao expoe telefone, id do cliente, id do carrinho nem id do pedido. O formato antigo com `?phone=` continua existindo apenas para teste local/manual.

## Acompanhamento e rastreio do pedido

Depois que o link de pagamento e criado, o pedido local recebe um `statusPublicToken`. A tela publica de acompanhamento fica em:

```text
https://SEU-NGROK.ngrok-free.dev/storefront/pedido/status?token={statusPublicToken}
```

Essa tela mostra numero do pedido ou draft order, status de pagamento, linha do tempo operacional, metodo/prazo de envio, codigo ou link de rastreio quando disponivel, resumo dos itens, botao para abrir o pagamento Pix e botao para falar com atendente.

Quando a cliente mandar `status`, `meu pedido`, `rastreio` ou `acompanhar pedido` no WhatsApp, o backend busca os pedidos pelo telefone recebido no webhook.

Se houver apenas um pedido, o detalhe pode abrir direto. Se houver mais de um, o Web App mostra uma lista com numero, data, total, pagamento, envio e botao `Ver detalhes`.

O botao de pagamento so aparece quando o pedido ainda pode ser pago. Quando existir `pix_copy_paste`, a tela mostra Pix copia e cola e botao para copiar. Se a Nuvemshop/API nao retornar Pix direto, o fallback continua sendo o `checkout_url`.

## Endpoints adicionados

```http
POST   /api/carts/start-from-whatsapp?phone={phone}
GET    /api/carts/{cartToken}
POST   /api/carts/{cartToken}/items
PATCH  /api/carts/{cartToken}/items/{itemId}
DELETE /api/carts/{cartToken}/items/{itemId}
POST   /api/carts/{cartToken}/checkout
GET    /api/orders/status/{statusPublicToken}
GET    /api/orders/status/latest?phone={phone}
GET    /api/orders/status/customer?phone={phone}
POST   /api/webhooks/nuvemshop/order
POST   /api/webhooks/nuvemshop/fulfillment
POST   /api/webhooks/nuvemshop/products
POST   /api/webhooks/nuvemshop/product
POST   /api/webhooks/nuvemshop/stock
```

## Concorrencia e idempotencia

O carrinho ganhou `@Version` e o checkout usa trava pessimista no `cartToken` durante a criacao do link. Se a cliente clicar duas vezes em gerar pagamento, o backend retorna o link ja existente em vez de criar outro Draft Order.

Os webhooks flexiveis da Nuvemshop salvam o payload em `webhook_event_log` e tentam atualizar `payment_status`, `shipping_status`, `shipping_tracking_number`, `shipping_tracking_url`, `shipping_method` e numero do pedido local quando esses campos vierem no JSON.

Para estoque e imagens, use os webhooks de produto/estoque acima quando a Nuvemshop enviar `product_id`, `product` ou `id`. Alem disso, a aplicacao possui sincronizacao periodica:

```env
STOCK_SYNC_ENABLED=true
STOCK_SYNC_INTERVAL_MINUTES=10
```

Mesmo com webhook e scheduler, o checkout continua validando estoque novamente antes de criar o Draft Order.
