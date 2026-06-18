import { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  BadgeCheck,
  CheckCircle2,
  Clipboard,
  CircleDollarSign,
  ExternalLink,
  Headphones,
  Heart,
  Home,
  Loader2,
  Minus,
  PackageSearch,
  Plus,
  Search,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Truck,
  X
} from 'lucide-react';
import { api } from './api.js';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const FALLBACK_IMAGE = 'https://placehold.co/900x1200/f3faf6/047857.png?text=LLFashion+Moda';

const viewTitle = {
  home: 'Boas-vindas',
  categories: 'Categorias',
  products: 'Produtos',
  detail: 'Detalhe do produto',
  added: 'Adicionado',
  cart: 'Carrinho',
  customer: 'Dados da cliente',
  address: 'Endereco de entrega',
  shipping: 'Formas de envio',
  summary: 'Resumo e pagamento',
  payment: 'Pagamento'
};

export default function App() {
  const [view, setView] = useState('home');
  const [history, setHistory] = useState([]);
  const [cartToken, setCartToken] = useState(localStorage.getItem('llfashion_cart_token'));
  const [cart, setCart] = useState(null);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState('todos');
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [selectedVariant, setSelectedVariant] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [shippingOptions, setShippingOptions] = useState([]);
  const [checkout, setCheckout] = useState(null);
  const [orderStatus, setOrderStatus] = useState(null);
  const [orderStatusList, setOrderStatusList] = useState(null);
  const [toast, setToast] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const searchParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const phone = useMemo(() => searchParams.get('phone') || '', [searchParams]);
  const urlCartToken = useMemo(() => searchParams.get('token') || '', [searchParams]);
  const isStatusPage = useMemo(() => window.location.pathname.includes('/pedido/status'), []);

  useEffect(() => {
    boot();
  }, []);

  useEffect(() => {
    if (!toast) return undefined;
    const timeout = window.setTimeout(() => setToast(''), 3500);
    return () => window.clearTimeout(timeout);
  }, [toast]);

  async function boot() {
    try {
      setLoading(true);
      if (isStatusPage) {
        const statusToken = searchParams.get('token');
        const statusPhone = searchParams.get('phone');
        if (statusToken) {
          setOrderStatus(await api.getOrderStatus(statusToken));
          return;
        }
        if (statusPhone) {
          const result = await api.getOrdersByPhone(statusPhone);
          if (result.order && !result.multiple) {
            setOrderStatus(result.order);
          } else {
            setOrderStatusList(result);
          }
          return;
        }
        if (!statusToken && !statusPhone) {
          throw new Error('Token de acompanhamento nao informado.');
        }
        return;
      }
      const session = await loadSession();
      if (isClosedCart(session.cart)) {
        localStorage.removeItem('llfashion_cart_token');
        if (session.cart.statusUrl) {
          window.location.replace(session.cart.statusUrl);
          return;
        }
        setCartToken(session.cartToken);
        setCart(session.cart);
        setCheckout(cartToCheckout(session.cart));
        setView('payment');
        return;
      }
      const categoryList = await api.getCategories();
      setCartToken(session.cartToken);
      localStorage.setItem('llfashion_cart_token', session.cartToken);
      setCart(session.cart);
      setCategories(categoryList);
      await loadProducts('todos');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  async function loadSession() {
    if (urlCartToken) {
      const linkedCart = await api.getCart(urlCartToken);
      return { cartToken: urlCartToken, cart: linkedCart };
    }

    if (phone) {
      return api.startSession(phone);
    }

    if (!cartToken) {
      return api.startSession(phone);
    }

    try {
      const existingCart = await api.getCart(cartToken);
      return { cartToken, cart: existingCart };
    } catch (err) {
      localStorage.removeItem('llfashion_cart_token');
      return api.startSession(phone);
    }
  }

  async function loadProducts(categoryId) {
    const items = await api.getProducts(categoryId);
    setProducts(items);
  }

  function navigate(nextView) {
    setHistory((current) => [...current, view]);
    setView(nextView);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function goBack() {
    const previous = history[history.length - 1];
    if (!previous) {
      setView('home');
      return;
    }
    setHistory((current) => current.slice(0, -1));
    setView(previous);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function withBusy(action) {
    try {
      setBusy(true);
      setError('');
      await action();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function chooseCategory(categoryId) {
    await withBusy(async () => {
      setSelectedCategory(categoryId);
      await loadProducts(categoryId);
      navigate('products');
    });
  }

  async function openProduct(productId) {
    await withBusy(async () => {
      const product = await api.getProduct(productId);
      setSelectedProduct(product);
      const firstVariant = product.variants.find((variant) => variant.available);
      setSelectedVariant(firstVariant || null);
      setQuantity(1);
      navigate('detail');
    });
  }

  async function addSelectedItem() {
    if (!selectedVariant) {
      setError('Selecione uma variacao disponivel.');
      return;
    }
    await withBusy(async () => {
      const updatedCart = await api.addItem(cartToken, selectedVariant.nuvemshopVariantId, quantity);
      setCart(updatedCart);
      setToast('Adicionado ao carrinho.');
      navigate('added');
    });
  }

  async function updateCartItem(item, nextQuantity) {
    if (nextQuantity <= 0) {
      return removeCartItem(item.id);
    }
    await withBusy(async () => {
      try {
        const updatedCart = await api.updateItem(cartToken, item.id, nextQuantity);
        setCart(updatedCart);
      } catch (err) {
        try {
          setCart(await api.getCart(cartToken));
        } catch (_) {
          // Mantem o erro original visivel.
        }
        throw err;
      }
    });
  }

  async function removeCartItem(itemId) {
    await withBusy(async () => {
      const updatedCart = await api.removeItem(cartToken, itemId);
      setCart(updatedCart);
    });
  }

  async function saveCustomer(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    await withBusy(async () => {
      const updatedCart = await api.saveCustomer(cartToken, {
        fullName: form.get('fullName'),
        cpfCnpj: form.get('cpfCnpj'),
        email: form.get('email'),
        phone: form.get('phone')
      });
      setCart(updatedCart);
      navigate('address');
    });
  }

  async function lookupCep(setAddressForm, cep) {
    if (!cep || cep.replace(/\D/g, '').length !== 8) return;
    try {
      setBusy(true);
      setError('');
      const address = await api.lookupCep(cep.replace(/\D/g, ''));
      setAddressForm((current) => ({
        ...current,
        street: address.logradouro || current.street,
        neighborhood: address.bairro || current.neighborhood,
        city: address.localidade || current.city,
        state: address.uf || current.state
      }));
      return address;
    } catch (err) {
      setError(err.message);
      throw err;
    } finally {
      setBusy(false);
    }
  }

  async function saveAddress(addressForm) {
    await withBusy(async () => {
      const updatedCart = await api.saveAddress(cartToken, addressForm);
      const options = await api.shippingOptions(cartToken);
      setCart(updatedCart);
      setShippingOptions(options);
      navigate('shipping');
    });
  }

  async function selectShipping(option) {
    await withBusy(async () => {
      const updatedCart = await api.selectShipping(cartToken, option.code);
      setCart(updatedCart);
      navigate('summary');
    });
  }

  async function createPaymentLink() {
    await withBusy(async () => {
      let response;
      try {
        response = await api.createPaymentLink(cartToken);
      } catch (err) {
        try {
          setCart(await api.getCart(cartToken));
        } catch (_) {
          // Mantem o erro original do checkout visivel para a cliente.
        }
        throw err;
      }
      setCheckout(response);
      localStorage.removeItem('llfashion_cart_token');
      setCart((current) => current ? {
        ...current,
        status: 'PAYMENT_LINK_GENERATED',
        checkoutUrl: response.checkoutUrl,
        statusPublicToken: response.statusPublicToken,
        statusUrl: response.statusUrl,
        total: response.total
      } : current);
      navigate('payment');
    });
  }

  if (loading) {
    return <LoadingScreen />;
  }

      if (isStatusPage) {
        return (
          <main className="appShell">
            <div className="phoneFrame">
              <TopBar title="Acompanhar pedido" canGoBack={false} onBack={() => {}} cart={null} onCart={() => {}} />
              {error && <Banner tone="danger" message={error} onClose={() => setError('')} />}
              {orderStatusList && <OrderListScreen result={orderStatusList} />}
              {orderStatus && <OrderStatusScreen order={orderStatus} />}
            </div>
          </main>
    );
  }

  const visibleCart = isClosedCart(cart) ? null : cart;

  return (
    <main className="appShell">
      <div className="phoneFrame">
        <TopBar title={viewTitle[view]} canGoBack={view !== 'home'} onBack={goBack} cart={visibleCart} onCart={() => navigate('cart')} />
        {error && <Banner tone="danger" message={error} onClose={() => setError('')} />}
        {toast && <Banner tone="success" message={toast} onClose={() => setToast('')} />}
        {busy && <div className="busyLine"><Loader2 size={16} className="spin" /> Processando</div>}

        {view === 'home' && (
          <HomeScreen
            cart={visibleCart}
            onCategories={() => navigate('categories')}
            onProducts={() => chooseCategory('todos')}
            onPromos={() => chooseCategory('promocoes')}
            onCart={() => navigate('cart')}
          />
        )}
        {view === 'categories' && <CategoriesScreen categories={categories} onChoose={chooseCategory} />}
        {view === 'products' && (
          <ProductsScreen
            category={categories.find((item) => item.id === selectedCategory)}
            products={products}
            onProduct={openProduct}
          />
        )}
        {view === 'detail' && (
          <ProductDetailScreen
            product={selectedProduct}
            selectedVariant={selectedVariant}
            setSelectedVariant={setSelectedVariant}
            quantity={quantity}
            setQuantity={setQuantity}
            onAdd={addSelectedItem}
          />
        )}
        {view === 'added' && (
          <AddedScreen
            cart={cart}
            onMore={() => navigate('categories')}
            onCart={() => navigate('cart')}
            onCheckout={() => navigate(cart?.canCheckout ? 'customer' : 'cart')}
          />
        )}
        {view === 'cart' && (
          <CartScreen
            cart={cart}
            onUpdate={updateCartItem}
            onRemove={removeCartItem}
            onMore={() => navigate('categories')}
            onCheckout={() => navigate('customer')}
          />
        )}
        {view === 'customer' && <CustomerScreen cart={cart} onSubmit={saveCustomer} />}
        {view === 'address' && <AddressScreen cart={cart} onLookupCep={lookupCep} onSubmit={saveAddress} />}
        {view === 'shipping' && <ShippingScreen options={shippingOptions} onChoose={selectShipping} />}
        {view === 'summary' && <SummaryScreen cart={cart} onPayment={createPaymentLink} onCart={() => navigate('cart')} />}
        {view === 'payment' && <PaymentScreen checkout={checkout} cart={cart} />}
      </div>
    </main>
  );
}

function TopBar({ title, canGoBack, onBack, cart, onCart }) {
  return (
    <header className="topBar">
      <button className="iconButton" onClick={canGoBack ? onBack : undefined} aria-label={canGoBack ? 'Voltar' : 'Inicio'}>
        {canGoBack ? <ArrowLeft size={21} /> : <Home size={20} />}
      </button>
      <strong>{title}</strong>
      {cart ? (
        <button className="cartButton" onClick={onCart} aria-label="Carrinho">
          <ShoppingCart size={20} />
          {!!cart?.items?.length && <span>{cart.items.length}</span>}
        </button>
      ) : (
        <span className="topBarSpacer" />
      )}
    </header>
  );
}

function HomeScreen({ cart, onCategories, onProducts, onPromos, onCart }) {
  return (
    <section className="screen homeScreen">
      <div className="brandMark">
        <div className="logoCircle">LL</div>
        <div>
          <h1>LLFashion Moda</h1>
          <p>Moda feminina no atacado</p>
        </div>
      </div>
      <div className="heroPanel">
        <h2>Monte seu pedido com fotos, estoque claro e checkout seguro.</h2>
        <p>Pedido minimo no atacado: <strong>R$ 200,00</strong></p>
        {cart?.items?.length > 0 && <small>Voce ja tem {cart.items.length} item(ns) no carrinho.</small>}
      </div>
      <div className="actionList">
        <ActionCard icon={<ShoppingBag />} title="Comprar por categoria" text="Explore nossas pecas disponiveis" onClick={onCategories} />
        <ActionCard icon={<Sparkles />} title="Ver novidades" text="Produtos com estoque atualizado" onClick={onProducts} />
        <ActionCard icon={<Heart />} title="Ver promocoes" text="Oportunidades para montar seu pedido" onClick={onPromos} />
        <ActionCard icon={<ShoppingCart />} title="Ver carrinho" text="Revise itens e pedido minimo" onClick={onCart} active={cart?.items?.length > 0} />
        <ActionCard icon={<Headphones />} title="Falar com atendente" text="Atendimento humano pelo WhatsApp" onClick={() => window.open('https://wa.me/5521997547418', '_blank')} />
      </div>
      <button className="primaryButton" onClick={onCategories}>Continuar</button>
    </section>
  );
}

function ActionCard({ icon, title, text, onClick, active }) {
  return (
    <button className={`actionCard ${active ? 'active' : ''}`} onClick={onClick}>
      <span>{icon}</span>
      <b>{title}</b>
      <small>{text}</small>
    </button>
  );
}

function SafeImage({ src, alt = '', ...props }) {
  const [failed, setFailed] = useState(false);
  const finalSrc = !failed && src ? src : FALLBACK_IMAGE;
  return <img {...props} src={finalSrc} alt={alt} onError={() => setFailed(true)} />;
}

function CategoriesScreen({ categories, onChoose }) {
  return (
    <section className="screen">
      <SearchBox placeholder="Buscar categoria" />
      <div className="categoryList">
        {categories.map((category) => (
          <button className="categoryCard" key={category.id} onClick={() => onChoose(category.id)}>
            <SafeImage src={category.imageUrl} alt="" loading="lazy" />
            <span>
              <b>{category.name}</b>
              <small>{category.description}</small>
            </span>
            <em>{category.totalProducts}</em>
          </button>
        ))}
      </div>
    </section>
  );
}

function ProductsScreen({ category, products, onProduct }) {
  return (
    <section className="screen">
      <div className="sectionTitle">
        <h2>{category?.name || 'Produtos'}</h2>
        <p>Mostrando apenas itens com estoque disponivel.</p>
      </div>
      <div className="productList">
        {products.map((product) => (
          <button className="productCard" key={product.nuvemshopProductId} onClick={() => onProduct(product.nuvemshopProductId)}>
            <SafeImage src={product.imageUrl} alt={product.productName} loading="lazy" />
            <span>
              <b>{product.productName}</b>
              <small>A partir de {money(product.startingPrice)}</small>
              <em>Estoque: {product.totalStock}</em>
            </span>
            <Plus size={20} />
          </button>
        ))}
      </div>
      {products.length === 0 && <EmptyState text="Nenhum produto disponivel nesta categoria." />}
    </section>
  );
}

function ProductDetailScreen({ product, selectedVariant, setSelectedVariant, quantity, setQuantity, onAdd }) {
  if (!product) return null;
  const maxQuantity = selectedVariant?.stock || 0;
  const quantities = Array.from({ length: Math.min(maxQuantity, 12) }, (_, index) => index + 1);

  return (
    <section className="screen">
      <SafeImage className="detailImage" src={selectedVariant?.imageUrl || product.imageUrl} alt={product.productName} />
      <div className="productHeader">
        <h2>{product.productName}</h2>
        <p>A partir de {money(product.startingPrice)}</p>
        <span>Estoque total: {product.totalStock}</span>
      </div>
      <FieldGroup label="Variacao">
        <div className="variantGrid">
          {product.variants.map((variant) => (
            <button
              key={variant.nuvemshopVariantId}
              className={selectedVariant?.nuvemshopVariantId === variant.nuvemshopVariantId ? 'selected' : ''}
              disabled={!variant.available}
              onClick={() => {
                setSelectedVariant(variant);
                setQuantity(1);
              }}
            >
              <b>{variant.variantName || 'Unico'}</b>
              <small>{money(variant.price)} | Estoque {variant.stock}</small>
            </button>
          ))}
        </div>
      </FieldGroup>
      <FieldGroup label="Quantidade">
        <div className="quantityStepper">
          <button onClick={() => setQuantity(Math.max(1, quantity - 1))} disabled={quantity <= 1}><Minus size={18} /></button>
          <strong>{quantity}</strong>
          <button onClick={() => setQuantity(Math.min(maxQuantity, quantity + 1))} disabled={quantity >= maxQuantity}><Plus size={18} /></button>
        </div>
        <div className="quantityChips">
          {quantities.map((item) => (
            <button key={item} className={quantity === item ? 'selected' : ''} onClick={() => setQuantity(item)}>{item}</button>
          ))}
        </div>
      </FieldGroup>
      <div className="partialTotal">
        <span>Total parcial</span>
        <strong>{money((selectedVariant?.price || 0) * quantity)}</strong>
      </div>
      <button className="primaryButton" onClick={onAdd} disabled={!selectedVariant || maxQuantity <= 0}>Adicionar ao carrinho</button>
    </section>
  );
}

function AddedScreen({ cart, onMore, onCart, onCheckout }) {
  return (
    <section className="screen centerScreen">
      <BadgeCheck size={54} className="successIcon" />
      <h2>Adicionado ao carrinho</h2>
      <p>Subtotal atual: <strong>{money(cart?.subtotal)}</strong></p>
      <MinimumProgress cart={cart} />
      <button className="primaryButton" onClick={onMore}>Adicionar mais produtos</button>
      <button className="secondaryButton" onClick={onCart}>Ver carrinho</button>
      <button className="ghostButton" onClick={onCheckout}>{cart?.canCheckout ? 'Finalizar pedido' : 'Ver pedido minimo'}</button>
    </section>
  );
}

function CartScreen({ cart, onUpdate, onRemove, onMore, onCheckout }) {
  const hasStockProblem = hasCartStockProblem(cart);
  return (
    <section className="screen">
      <MinimumProgress cart={cart} />
      <div className="cartList">
        {cart?.items?.map((item) => (
          <article className="cartItem" key={item.id}>
            <SafeImage src={item.imageUrl} alt="" loading="lazy" />
            <div>
              <b>{item.productName}</b>
              <small>{item.variantName || 'Unico'}</small>
              <div className="miniStepper">
                <button onClick={() => onUpdate(item, item.quantity - 1)}><Minus size={14} /></button>
                <span>{item.quantity}</span>
                <button onClick={() => onUpdate(item, item.quantity + 1)} disabled={item.quantity >= item.stockAvailable}><Plus size={14} /></button>
              </div>
            </div>
            <aside>
              <strong>{money(item.totalPrice)}</strong>
              <button onClick={() => onRemove(item.id)} aria-label="Remover"><X size={16} /></button>
            </aside>
          </article>
        ))}
      </div>
      {cart?.items?.length === 0 && <EmptyState text="Seu carrinho esta vazio." />}
      {hasStockProblem && (
        <div className="safeNote">
          <PackageSearch size={18} /> Ajuste a quantidade dos itens sem estoque suficiente antes de finalizar.
        </div>
      )}
      <CartTotals cart={cart} />
      <button className="secondaryButton" onClick={onMore}>Adicionar mais produtos</button>
      <button className="primaryButton" onClick={onCheckout} disabled={!cart?.canCheckout || hasStockProblem}>Finalizar pedido</button>
    </section>
  );
}

function CustomerScreen({ cart, onSubmit }) {
  return (
    <FormScreen summary={cart} onSubmit={onSubmit} buttonText="Continuar">
      <TextInput name="fullName" label="Nome completo" placeholder="Maria Silva Santos" defaultValue={cart?.customerName || ''} />
      <TextInput name="cpfCnpj" label="CPF ou CNPJ" placeholder="123.456.789-09" />
      <TextInput name="email" label="E-mail" placeholder="maria@loja.com.br" type="email" defaultValue={cart?.customerEmail || ''} />
      <TextInput name="phone" label="Telefone / WhatsApp" placeholder="(21) 99999-9999" defaultValue={cart?.customerPhone || ''} />
      <div className="safeNote"><BadgeCheck size={18} /> Seus dados serao usados apenas para processar o pedido.</div>
    </FormScreen>
  );
}

function AddressScreen({ cart, onLookupCep, onSubmit }) {
  const [addressForm, setAddressForm] = useState({
    postalCode: cart?.postalCode || '',
    street: cart?.addressStreet || '',
    number: cart?.addressNumber || '',
    complement: cart?.addressComplement || '',
    neighborhood: cart?.addressNeighborhood || '',
    city: cart?.addressCity || '',
    state: cart?.addressState || ''
  });
  const [cepMessage, setCepMessage] = useState('');
  const [cepTone, setCepTone] = useState('info');

  useEffect(() => {
    const digits = addressForm.postalCode.replace(/\D/g, '');
    if (digits.length === 0) {
      setCepMessage('');
      return undefined;
    }
    if (digits.length < 8) {
      setCepTone('info');
      setCepMessage('Digite os 8 numeros do CEP para preencher o endereco.');
      return undefined;
    }
    if (digits.length > 8) {
      setCepTone('danger');
      setCepMessage('CEP deve ter 8 numeros.');
      return undefined;
    }

    setCepTone('info');
    setCepMessage('Buscando endereco pelo CEP...');
    const timer = window.setTimeout(async () => {
      try {
        await onLookupCep(setAddressForm, digits);
        setCepTone('success');
        setCepMessage('Endereco preenchido pelo CEP. Informe numero e complemento, se houver.');
      } catch (err) {
        setCepTone('danger');
        setCepMessage(err.message || 'CEP nao encontrado. Confira os numeros e tente novamente.');
      }
    }, 450);

    return () => window.clearTimeout(timer);
  }, [addressForm.postalCode]);

  function update(name, value) {
    setAddressForm((current) => ({ ...current, [name]: value }));
  }

  return (
    <section className="screen">
      <OrderSummaryCompact cart={cart} />
      <form className="formCard" onSubmit={(event) => { event.preventDefault(); onSubmit(addressForm); }}>
        <label>
          <span>CEP</span>
          <div className="inlineField">
            <input value={addressForm.postalCode} onChange={(event) => update('postalCode', event.target.value)} placeholder="01234-567" required />
            <button type="button" onClick={() => onLookupCep(setAddressForm, addressForm.postalCode).catch(() => {})}>Buscar CEP</button>
          </div>
          {cepMessage && <small className={`fieldHint ${cepTone}`}>{cepMessage}</small>}
        </label>
        <label><span>Rua</span><input value={addressForm.street} onChange={(event) => update('street', event.target.value)} required /></label>
        <div className="twoCols">
          <label><span>Numero</span><input value={addressForm.number} onChange={(event) => update('number', event.target.value)} required /></label>
          <label><span>Complemento</span><input value={addressForm.complement} onChange={(event) => update('complement', event.target.value)} /></label>
        </div>
        <label><span>Bairro</span><input value={addressForm.neighborhood} onChange={(event) => update('neighborhood', event.target.value)} required /></label>
        <div className="twoCols">
          <label><span>Cidade</span><input value={addressForm.city} onChange={(event) => update('city', event.target.value)} required /></label>
          <label><span>Estado</span><input value={addressForm.state} onChange={(event) => update('state', event.target.value)} required /></label>
        </div>
        <div className="safeNote"><Truck size={18} /> Depois do endereco, mostramos as opcoes de frete.</div>
        <button className="primaryButton" type="submit">Continuar</button>
      </form>
    </section>
  );
}

function ShippingScreen({ options, onChoose }) {
  return (
    <section className="screen">
      <div className="sectionTitle">
        <h2>Escolha a forma de envio</h2>
        <p>Opcoes disponiveis para o endereco informado.</p>
      </div>
      <div className="shippingList">
        {options.map((option) => (
          <button className="shippingCard" key={option.code} onClick={() => onChoose(option)}>
            <span><Truck size={20} /></span>
            <div>
              <b>{option.name}</b>
              <small>{option.description}</small>
              <em>{option.eta}</em>
            </div>
            <strong>{money(option.price)}</strong>
          </button>
        ))}
      </div>
    </section>
  );
}

function SummaryScreen({ cart, onPayment, onCart }) {
  const hasStockProblem = hasCartStockProblem(cart);
  return (
    <section className="screen">
      <OrderSummaryCompact cart={cart} expanded />
      <InfoCard icon={<Truck />} title={cart?.selectedShippingName || 'Envio'} text={`${cart?.selectedShippingEta || ''} ${money(cart?.shippingPrice)}`} />
      <InfoCard icon={<CircleDollarSign />} title="Pagamento" text="Pix ou checkout seguro pela Nuvemshop" />
      <CartTotals cart={cart} />
      <button className="secondaryButton" onClick={onCart}>Alterar carrinho</button>
      <button className="primaryButton" onClick={onPayment} disabled={hasStockProblem}>Gerar link de pagamento</button>
    </section>
  );
}

function PaymentScreen({ checkout, cart }) {
  const url = checkout?.checkoutUrl || cart?.checkoutUrl;
  const statusUrl = checkout?.statusUrl;
  const pixCode = checkout?.pixCopyPaste || cart?.pixCopyPaste;
  const pixQrCodeUrl = checkout?.pixQrCodeUrl || cart?.pixQrCodeUrl;
  const [copied, setCopied] = useState(false);

  async function copyPixCode() {
    if (!pixCode) return;
    await navigator.clipboard.writeText(pixCode);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 2500);
  }

  return (
    <section className="screen centerScreen">
      <BadgeCheck size={58} className="successIcon" />
      <h2>Link de pagamento gerado</h2>
      <p>Total do pedido: <strong>{money(checkout?.total || cart?.total)}</strong></p>
      {pixCode ? (
        <div className="pixBox">
          {pixQrCodeUrl && <SafeImage src={pixQrCodeUrl} alt="QR Code Pix" className="pixQr" />}
          <span>Pix copia e cola</span>
          <code>{pixCode}</code>
          <button className="primaryButton" onClick={copyPixCode} type="button">
            <Clipboard size={17} /> Copiar codigo Pix
          </button>
          {copied && <small className="copyFeedback">Codigo Pix copiado com sucesso.</small>}
        </div>
      ) : (
        url && <a className="primaryButton linkButton" href={url} target="_blank" rel="noreferrer">Abrir checkout Pix</a>
      )}
      {statusUrl && <a className="secondaryButton linkButton" href={statusUrl}>Acompanhar pedido</a>}
      <a className="ghostButton linkButton" href="https://wa.me/5521997547418" target="_blank" rel="noreferrer">Voltar para o WhatsApp</a>
      <small className="muted">O pagamento sera concluido no ambiente seguro da Nuvemshop.</small>
    </section>
  );
}

function OrderListScreen({ result }) {
  if (!result?.found) {
    return <EmptyState text={result?.message || 'Nenhum pedido encontrado.'} />;
  }

  return (
    <section className="screen statusScreen">
      <div className="sectionTitle">
        <h2>Escolha o pedido</h2>
        <p>{result.message}</p>
      </div>
      <div className="orderList">
        {result.orders?.map((order) => (
          <article className="orderListCard" key={order.statusPublicToken}>
            <div>
              <small>Pedido</small>
              <strong>{order.publicOrderNumber}</strong>
            </div>
            <div>
              <small>Data</small>
              <span>{formatDate(order.createdAt)}</span>
            </div>
            <div>
              <small>Total</small>
              <span>{money(order.total)}</span>
            </div>
            <div>
              <small>Pagamento</small>
              <span>{order.paymentStatus}</span>
            </div>
            <div>
              <small>Envio</small>
              <span>{order.shippingStatus}</span>
            </div>
            <a className="primaryButton linkButton" href={order.statusUrl}>Ver detalhes</a>
          </article>
        ))}
      </div>
    </section>
  );
}

function OrderStatusScreen({ order }) {
  const steps = [
    ['ORDER_RECEIVED', 'Pedido criado'],
    ['WAITING_PAYMENT', 'Pagamento'],
    ['PAYMENT_CONFIRMED', 'Confirmado'],
    ['SEPARATING_ORDER', 'Separacao'],
    ['SHIPPED', 'Envio'],
    ['DELIVERED', 'Entrega']
  ];
  const activeIndex = Math.max(0, steps.findIndex(([status]) => status === order.publicStatus));
  const displayIndex = activeIndex < 0 ? 0 : activeIndex;

  async function copyTracking() {
    if (!order.shippingTrackingNumber) return;
    await navigator.clipboard.writeText(order.shippingTrackingNumber);
  }

  const [pixCopied, setPixCopied] = useState(false);
  const hasPixCode = Boolean(order.pixCopyPaste);
  const canOpenCheckout = order.canPay && order.checkoutUrl && !hasPixCode;

  async function copyPixCode() {
    if (!order.pixCopyPaste) return;
    await navigator.clipboard.writeText(order.pixCopyPaste);
    setPixCopied(true);
    window.setTimeout(() => setPixCopied(false), 2500);
  }

  return (
    <section className="screen statusScreen">
      <div className="statusHero">
        <div>
          <small>LLFashion Moda</small>
          <h1>{order.statusTitle}</h1>
          <p>{order.statusMessage}</p>
        </div>
        <ShieldCheck size={36} />
      </div>

      <div className="statusNumberCard">
        <span>Pedido</span>
        <strong>{displayOrderNumber(order)}</strong>
      </div>

      <div className="timelineCard">
        {steps.map(([status, label], index) => (
          <div className={`timelineStep ${index <= displayIndex ? 'done' : ''}`} key={status}>
            <span>{index <= displayIndex ? <CheckCircle2 size={16} /> : index + 1}</span>
            <small>{label}</small>
          </div>
        ))}
      </div>

      <div className="trackingGrid">
        <StatusInfo icon={<CircleDollarSign />} title="Pagamento" value={order.paymentStatus} />
        <StatusInfo icon={<Truck />} title="Envio" value={order.shippingStatus} />
      </div>

      <section className="statusCard">
        <div className="summaryHeader">
          <b>Resumo do pedido</b>
          <small>{order.items?.length || 0} item(ns)</small>
        </div>
        {order.items?.map((item, index) => (
          <div className="summaryItem" key={`${item.productName}-${index}`}>
            <SafeImage src={item.imageUrl} alt="" />
            <span>{item.productName}<small>{item.variantName || 'Unico'} | {item.quantity} un</small></span>
            <b>{money(item.totalPrice)}</b>
          </div>
        ))}
        <div className="statusTotal">
          <span>Total do pedido</span>
          <strong>{money(order.total)}</strong>
        </div>
      </section>

      <section className="statusCard">
        <div className="statusCardTitle">
          <Truck size={18} />
          <b>Entrega</b>
        </div>
        <p><strong>{order.shippingMethod}</strong></p>
        <small>{order.shippingEta}</small>
        {order.shippingTrackingNumber ? (
          <div className="trackingBox">
            <span>Codigo de rastreio</span>
            <strong>{order.shippingTrackingNumber}</strong>
            <button className="secondaryButton" onClick={copyTracking}><Clipboard size={17} /> Copiar codigo</button>
            {order.shippingTrackingUrl && (
              <a className="primaryButton linkButton" href={order.shippingTrackingUrl} target="_blank" rel="noreferrer">
                Acompanhar entrega <ExternalLink size={16} />
              </a>
            )}
          </div>
        ) : (
          <div className="safeNote"><Truck size={18} /> Aguardando atualizacao da loja sobre o rastreio.</div>
        )}
      </section>

      <section className="statusCard">
        <div className="statusCardTitle">
          <CircleDollarSign size={18} />
          <b>Pagamento Pix</b>
        </div>
        <p>{order.paymentStatus}</p>
        {hasPixCode ? (
          <div className="pixBox">
            {order.pixQrCodeUrl && <SafeImage src={order.pixQrCodeUrl} alt="QR Code Pix" className="pixQr" />}
            <span>Pix copia e cola</span>
            <code>{order.pixCopyPaste}</code>
            <button className="primaryButton" onClick={copyPixCode} type="button">
              <Clipboard size={17} /> Copiar codigo Pix
            </button>
            {pixCopied && <small className="copyFeedback">Codigo Pix copiado com sucesso.</small>}
          </div>
        ) : canOpenCheckout ? (
          <a className="primaryButton linkButton" href={order.checkoutUrl} target="_blank" rel="noreferrer">
            Abrir link de pagamento
          </a>
        ) : (
          <div className="safeNote"><ShieldCheck size={18} /> Pagamento indisponivel para o status atual deste pedido.</div>
        )}
      </section>

      <a className="secondaryButton linkButton" href="https://wa.me/5521997547418" target="_blank" rel="noreferrer">
        Falar com atendente no WhatsApp
      </a>
    </section>
  );
}

function StatusInfo({ icon, title, value }) {
  return (
    <div className="statusInfo">
      <span>{icon}</span>
      <small>{title}</small>
      <b>{value}</b>
    </div>
  );
}

function FormScreen({ summary, onSubmit, buttonText, children }) {
  return (
    <section className="screen">
      <OrderSummaryCompact cart={summary} />
      <form className="formCard" onSubmit={onSubmit}>
        {children}
        <button className="primaryButton" type="submit">{buttonText}</button>
      </form>
    </section>
  );
}

function TextInput({ label, name, placeholder, type = 'text', defaultValue = '' }) {
  return (
    <label>
      <span>{label}</span>
      <input name={name} placeholder={placeholder} type={type} defaultValue={defaultValue} required />
    </label>
  );
}

function FieldGroup({ label, children }) {
  return (
    <div className="fieldGroup">
      <h3>{label}</h3>
      {children}
    </div>
  );
}

function MinimumProgress({ cart }) {
  if (!cart) return null;
  return (
    <div className="minimumCard">
      <div>
        <b>{cart.canCheckout ? 'Pedido minimo atingido' : 'Progresso do pedido minimo'}</b>
        <small>{money(cart.subtotal)} de {money(cart.minimumOrderValue)}</small>
      </div>
      <div className="progressTrack"><span style={{ width: `${cart.progressPercent || 0}%` }} /></div>
      {!cart.canCheckout && <em>Faltam {money(cart.missingAmount)}</em>}
    </div>
  );
}

function CartTotals({ cart }) {
  return (
    <div className="totalsCard">
      <span><small>Subtotal dos produtos</small><b>{money(cart?.subtotal)}</b></span>
      <span><small>Frete</small><b>{money(cart?.shippingPrice)}</b></span>
      <span><small>Total</small><strong>{money(cart?.total)}</strong></span>
    </div>
  );
}

function OrderSummaryCompact({ cart, expanded = false }) {
  return (
    <div className="summaryCard">
      <div className="summaryHeader">
        <b>Resumo do pedido</b>
        <small>{cart?.items?.length || 0} item(ns)</small>
      </div>
      {expanded && cart?.items?.slice(0, 4).map((item) => (
        <div className="summaryItem" key={item.id}>
          <SafeImage src={item.imageUrl} alt="" />
          <span>{item.productName}<small>{item.quantity} un</small></span>
          <b>{money(item.totalPrice)}</b>
        </div>
      ))}
      <CartTotals cart={cart} />
    </div>
  );
}

function InfoCard({ icon, title, text }) {
  return (
    <div className="infoCard">
      <span>{icon}</span>
      <div>
        <b>{title}</b>
        <small>{text}</small>
      </div>
    </div>
  );
}

function SearchBox({ placeholder }) {
  return (
    <div className="searchBox">
      <Search size={18} />
      <input placeholder={placeholder} />
    </div>
  );
}

function Banner({ tone, message, onClose }) {
  return (
    <div className={`banner ${tone}`}>
      <span>{message}</span>
      <button onClick={onClose}><X size={16} /></button>
    </div>
  );
}

function EmptyState({ text }) {
  return (
    <div className="emptyState">
      <PackageSearch size={40} />
      <p>{text}</p>
    </div>
  );
}

function LoadingScreen() {
  return (
    <main className="appShell">
      <div className="phoneFrame loading">
        <Loader2 className="spin" size={34} />
        <p>Carregando LLFashion Moda</p>
      </div>
    </main>
  );
}

function money(value) {
  const numeric = Number(value || 0);
  return BRL.format(numeric);
}

function formatDate(value) {
  if (!value) return 'Nao informada';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Nao informada';
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function displayOrderNumber(order) {
  const orderNumber = String(order?.nuvemshopOrderNumber || '').trim();
  if (orderNumber && orderNumber !== '0') {
    return `#${orderNumber}`;
  }
  if (order?.nuvemshopDraftOrderId) {
    return `#${order.nuvemshopDraftOrderId}`;
  }
  return `#${String(order?.localOrderId || '').slice(0, 8)}`;
}

function isClosedCart(cart) {
  return !!cart && ['PAYMENT_LINK_GENERATED', 'PAYMENT_PENDING', 'ORDER_CREATED', 'CANCELLED', 'EXPIRED', 'ERROR']
    .includes(cart.status);
}

function cartToCheckout(cart) {
  return {
    cartToken: cart.cartToken,
    checkoutUrl: cart.checkoutUrl,
    statusPublicToken: cart.statusPublicToken,
    statusUrl: cart.statusUrl,
    total: cart.total,
    message: 'Este carrinho ja gerou um pedido.'
  };
}

function hasCartStockProblem(cart) {
  return !!cart?.items?.some((item) => Number(item.quantity || 0) > Number(item.stockAvailable || 0));
}
