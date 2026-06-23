const API_BASE = import.meta.env.VITE_API_BASE || '';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true',
      ...(options.headers || {})
    },
    ...options
  });

  const text = await response.text();
  const contentType = response.headers.get('content-type') || '';
  const isJson = contentType.toLowerCase().includes('application/json');
  let data = null;

  if (text && isJson) {
    data = JSON.parse(text);
  } else if (text && !isJson) {
    const preview = text.replace(/\s+/g, ' ').slice(0, 180);
    throw new Error(
      response.ok
        ? 'O servidor retornou uma resposta HTML em vez de JSON. Recarregue a página e tente novamente.'
        : `O servidor retornou uma resposta não JSON (${response.status}). ${preview}`
    );
  }

  if (!response.ok) {
    throw new Error(data?.message || 'Não foi possível concluir a operação.');
  }
  return data;
}

export const api = {
  startSession(phone) {
    const query = phone ? `?phone=${encodeURIComponent(phone)}` : '';
    return request(`/api/storefront/session/start${query}`);
  },
  getCategories() {
    return request('/api/storefront/categories');
  },
  getProducts(categoryId) {
    const query = categoryId ? `?categoryId=${encodeURIComponent(categoryId)}` : '';
    return request(`/api/storefront/products${query}`);
  },
  getProduct(productId) {
    return request(`/api/storefront/products/${productId}`);
  },
  getCart(cartToken) {
    return request(`/api/storefront/cart/${cartToken}`);
  },
  addItem(cartToken, nuvemshopVariantId, quantity) {
    return request(`/api/storefront/cart/${cartToken}/items`, {
      method: 'POST',
      body: JSON.stringify({ nuvemshopVariantId, quantity })
    });
  },
  updateItem(cartToken, itemId, quantity) {
    return request(`/api/storefront/cart/${cartToken}/items/${itemId}`, {
      method: 'PUT',
      body: JSON.stringify({ quantity })
    });
  },
  removeItem(cartToken, itemId) {
    return request(`/api/storefront/cart/${cartToken}/items/${itemId}`, {
      method: 'DELETE'
    });
  },
  saveCustomer(cartToken, payload) {
    return request(`/api/storefront/checkout/${cartToken}/customer`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },
  lookupCep(cep) {
    return request(`/api/storefront/address/cep/${encodeURIComponent(cep)}`);
  },
  saveAddress(cartToken, payload) {
    return request(`/api/storefront/checkout/${cartToken}/address`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },
  shippingOptions(cartToken) {
    return request(`/api/storefront/checkout/${cartToken}/shipping-options`, {
      method: 'POST',
      body: '{}'
    });
  },
  selectShipping(cartToken, shippingCode) {
    return request(`/api/storefront/checkout/${cartToken}/select-shipping`, {
      method: 'POST',
      body: JSON.stringify({ shippingCode })
    });
  },
  createPaymentLink(cartToken) {
    return request(`/api/storefront/checkout/${cartToken}/create-payment-link`, {
      method: 'POST',
      body: '{}'
    });
  },
  getOrderStatus(statusToken) {
    return request(`/api/orders/status/${encodeURIComponent(statusToken)}`);
  },
  getOrderStatusByAccess(accessToken) {
    return request(`/api/orders/status/access/${encodeURIComponent(accessToken)}`);
  },
  getOrdersByPhone(phone) {
    return request(`/api/orders/status/customer?phone=${encodeURIComponent(phone)}`);
  }
};
