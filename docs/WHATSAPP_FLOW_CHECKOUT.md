# WhatsApp Flow Checkout

Este documento descreve o fluxo por WhatsApp Flow da LLFashion, mantido como fallback/opcional.

O fluxo principal atual é o Checkout React Web App documentado em [REACT_WEBAPP_CHECKOUT.md](REACT_WEBAPP_CHECKOUT.md). Use o Flow apenas para testes, compatibilidade ou cenários antigos.

## Objetivo

O cliente nao deve escolher quantidade livre no carrinho nativo do catalogo quando isso puder gerar uma compra acima do estoque.

O fluxo principal agora e:

1. Cliente manda "oi", "comprar", "catalogo", "novidades", "promocoes" ou envia um carrinho antigo.
2. Backend abre o WhatsApp Flow.
3. Flow mostra menu, categorias e produtos com base no `product_mapping`.
4. Cliente escolhe produto, variacao e quantidade.
5. Backend so retorna quantidades ate o estoque disponivel.
6. Item e adicionado ao carrinho local.
7. Cliente pode adicionar mais produtos, ajustar quantidade, remover item ou cancelar.
8. Checkout so avanca quando atingir o pedido minimo.
9. Flow coleta nome, CPF/CNPJ, e-mail e endereco.
10. Backend valida estoque novamente, cria Draft Order na Nuvemshop e envia o link de pagamento.

## Arquivo JSON do Flow

Cole este arquivo no editor JSON do Flow da Meta:

```text
docs/whatsapp-flow-checkout.json
```

No PowerShell, para copiar direto:

```powershell
Get-Content "C:\Users\lucas\Downloads\whatsapp-checkout-nuvemshop\whatsapp-checkout-nuvemshop\docs\whatsapp-flow-checkout.json" -Raw | Set-Clipboard
```

## Endpoint do Flow

Configure o ponto de extremidade do Flow para:

```text
https://SEU-NGROK.ngrok-free.dev/api/whatsapp/flows/data-exchange
```

Exemplo:

```text
https://cross-penalize-staring.ngrok-free.dev/api/whatsapp/flows/data-exchange
```

## Variaveis obrigatorias

```env
WHATSAPP_FLOWS_ENABLED=true
WHATSAPP_FLOW_ID=id_do_flow_publicado_na_meta
WHATSAPP_FLOW_CTA=Comprar agora
WHATSAPP_FLOW_MODE=published
WHATSAPP_ACCESS_TOKEN=token_da_meta
WHATSAPP_PHONE_NUMBER_ID=id_do_numero_da_loja
WHATSAPP_FLOW_PRIVATE_KEY_PATH=C:\caminho\para\flow-private-key.pem
CHECKOUT_MINIMUM_ORDER_TOTAL=200.00
```

Para teste com Flow em rascunho:

```env
WHATSAPP_FLOW_MODE=draft
```

## Telas do Flow

- `MAIN_MENU`: menu inicial.
- `CATEGORIES`: categorias.
- `PRODUCT_LIST`: produtos disponiveis.
- `CHOOSE_VARIANT`: variacoes com estoque.
- `CHOOSE_QUANTITY`: quantidades permitidas.
- `ITEM_ADDED`: item adicionado ao carrinho.
- `CART_REVIEW`: revisao do carrinho.
- `SELECT_CART_ITEM`: escolha de item para ajuste.
- `ADJUST_QUANTITY`: nova quantidade ou remover item.
- `MINIMUM_NOT_REACHED`: pedido minimo nao atingido.
- `CUSTOMER_DATA`: nome, CPF/CNPJ e e-mail.
- `ADDRESS_DATA`: CEP, rua, numero, complemento, bairro, cidade e estado.
- `SHIPPING_OPTIONS`: orienta frete e Pix pelo checkout Nuvemshop.
- `CONFIRM_ORDER`: confirmacao final antes de criar pedido.
- `ORDER_CREATED`: pedido criado.
- `CANCELLED`: pedido cancelado antes de criar Draft Order.
- `OUT_OF_STOCK`: item indisponivel.
- `HUMAN_ATTENDANT`: cliente pediu atendimento.

## Categorias

As categorias usam os dados ja existentes em `product_mapping`. Como a tabela atual nao guarda categorias oficiais da Nuvemshop, a classificacao e feita pelo nome do produto, variacao e SKU.

Exemplos:

- `SAIAS`: contem "SAIA".
- `VESTIDOS`: contem "VESTIDO".
- `PROMOCOES`: contem "PROMO", "SALE", "OFF" ou "LIQUIDA".
- `NOVIDADES`: contem "NOVIDADE", "NEW" ou "LANCAMENTO".

Para uma categorizacao perfeita, uma etapa futura deve sincronizar categorias/tags oficiais da Nuvemshop para a base local.

## Frete e Pix

Nesta etapa, o backend coleta o endereco e envia esses dados para o Draft Order.

O calculo final de frete e o pagamento Pix continuam no checkout seguro da Nuvemshop, usando o `checkout_url` retornado pelo Draft Order.

## Cancelamento

O Flow permite cancelar antes de criar o Draft Order. Ao cancelar:

- o carrinho local aberto e marcado como cancelado;
- nenhum pedido e criado na Nuvemshop;
- o cliente ve a tela `CANCELLED`.

Se o Draft Order ja foi criado, este fluxo nao cancela automaticamente o pedido na Nuvemshop.
