import { useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  BadgeCheck,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
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
const FALLBACK_IMAGE = 'https://placehold.co/900x1200/f3faf6/047857.png?text=L%26LFashion';
const COLOR_SWATCHES = {
  AMARELO: '#fde047',
  AZUL: '#1d4ed8',
  BEGE: '#d6b98c',
  BRANCO: '#ffffff',
  CINZA: '#9ca3af',
  DOURADO: '#c8a13b',
  LARANJA: '#f59e0b',
  LILAS: '#d8b4fe',
  MARFIM: '#f8f1df',
  MARROM: '#9a6a2f',
  NUDE: '#e6c7ad',
  'OFF WHITE': '#faf7ed',
  PINK: '#f472b6',
  PRATA: '#cbd5e1',
  PRETO: '#111827',
  ROSA: '#f9a8d4',
  ROXO: '#7c3aed',
  VERDE: '#22c55e',
  VERMELHO: '#ef4444'
};

const viewTitle = {
  home: 'Boas-vindas',
  categories: 'Categorias',
  products: 'Produtos',
  detail: 'Detalhe do produto',
  added: 'Adicionado',
  cart: 'Carrinho',
  customer: 'Dados da cliente',
  address: 'Endereço de entrega',
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
  const [variantImageSelected, setVariantImageSelected] = useState(false);
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
        const statusAccess = searchParams.get('access');
        const statusToken = searchParams.get('token');
        const statusPhone = searchParams.get('phone');
        if (statusAccess) {
          const result = await api.getOrderStatusByAccess(statusAccess);
          if (result.order && !result.multiple) {
            setOrderStatus(result.order);
          } else {
            setOrderStatusList(result);
          }
          return;
        }
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
        if (!statusAccess && !statusToken && !statusPhone) {
          throw new Error('Token de acompanhamento não informado.');
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
      setSelectedVariant(null);
      setVariantImageSelected(false);
      setQuantity(1);
      navigate('detail');
    });
  }

  async function addSelectedItem() {
    if (!selectedVariant) {
      setError('Selecione uma variação disponível.');
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
          // Mantém o erro original visível.
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
          // Mantém o erro original do checkout visível para a cliente.
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
            variantImageSelected={variantImageSelected}
            setVariantImageSelected={setVariantImageSelected}
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
          <h1>L&LFashion</h1>
          <p>Moda feminina no atacado</p>
        </div>
      </div>
      <div className="heroPanel">
        <h2>Monte seu pedido com fotos, estoque claro e checkout seguro.</h2>
        <p>Pedido mínimo no atacado: <strong>R$ 200,00</strong></p>
        {cart?.items?.length > 0 && <small>Você já tem {cart.items.length} item(ns) no carrinho.</small>}
      </div>
      <div className="actionList">
        <ActionCard icon={<ShoppingBag />} title="Comprar por categoria" text="Explore nossas peças disponíveis" onClick={onCategories} />
        <ActionCard icon={<Sparkles />} title="Ver novidades" text="Produtos com estoque atualizado" onClick={onProducts} />
        <ActionCard icon={<Heart />} title="Ver promoções" text="Oportunidades para montar seu pedido" onClick={onPromos} />
        <ActionCard icon={<ShoppingCart />} title="Ver carrinho" text="Revise itens e pedido mínimo" onClick={onCart} active={cart?.items?.length > 0} />
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
        <p>Mostrando apenas itens com estoque disponível.</p>
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
      {products.length === 0 && <EmptyState text="Nenhum produto disponível nesta categoria." />}
    </section>
  );
}

function ProductDetailScreen({ product, selectedVariant, setSelectedVariant, variantImageSelected, setVariantImageSelected, quantity, setQuantity, onAdd }) {
  const [carouselIndex, setCarouselIndex] = useState(0);

  useEffect(() => {
    setCarouselIndex(0);
  }, [product?.nuvemshopProductId]);

  if (!product) return null;
  const maxQuantity = selectedVariant?.stock || 0;
  const quantities = Array.from({ length: Math.min(maxQuantity, 12) }, (_, index) => index + 1);
  const carouselImages = productGalleryImages(product);
  const carouselImage = carouselImages[carouselIndex] || product.imageUrl;
  const detailImage = variantImageSelected && selectedVariant?.imageUrl ? selectedVariant.imageUrl : carouselImage;
  const showCarousel = !selectedVariant && carouselImages.length > 1;
  const colorOptions = colorChoices(product.variants);
  const selectedColor = selectedVariant ? variantColorLabel(selectedVariant) : '';
  const sizeOptions = selectedColor ? sizeChoices(product.variants, selectedColor) : [];

  function showPreviousImage() {
    setCarouselIndex((current) => (current - 1 + carouselImages.length) % carouselImages.length);
  }

  function showNextImage() {
    setCarouselIndex((current) => (current + 1) % carouselImages.length);
  }

  function chooseVariant(variant) {
    if (!variant?.available) return;
    setSelectedVariant(variant);
    setVariantImageSelected(true);
    setQuantity(1);
  }

  function chooseColor(option) {
    chooseVariant(option.variants.find((variant) => variant.available));
  }

  function chooseSize(size) {
    const variant = product.variants.find((item) => (
      item.available
      && variantColorLabel(item) === selectedColor
      && variantSizeLabel(item) === size
    ));
    chooseVariant(variant);
  }

  return (
    <section className="screen">
      <div className="detailMedia">
        <SafeImage className="detailImage" src={detailImage} alt={product.productName} />
        {showCarousel && (
          <>
            <button className="carouselNav previous" type="button" onClick={showPreviousImage} aria-label="Foto anterior">
              <ChevronLeft size={20} />
            </button>
            <button className="carouselNav next" type="button" onClick={showNextImage} aria-label="Próxima foto">
              <ChevronRight size={20} />
            </button>
            <div className="carouselDots" aria-label="Fotos do produto">
              {carouselImages.map((imageUrl, index) => (
                <button
                  type="button"
                  key={`${imageUrl}-${index}`}
                  className={index === carouselIndex ? 'selected' : ''}
                  onClick={() => setCarouselIndex(index)}
                  aria-label={`Ver foto ${index + 1}`}
                />
              ))}
            </div>
          </>
        )}
      </div>
      <div className="productHeader">
        <h2>{product.productName}</h2>
        <p>A partir de {money(product.startingPrice)}</p>
        <span>Estoque total: {product.totalStock}</span>
      </div>
      <FieldGroup label={selectedColor ? <>Cor: <strong>{selectedColor}</strong></> : 'Cor'}>
        <div className="colorSwatches">
          {colorOptions.map((option) => (
            <button
              type="button"
              key={option.color}
              className={`colorSwatch ${selectedColor === option.color ? 'selected' : ''} ${isLightSwatch(option.color) ? 'light' : ''}`}
              style={{ '--swatch-color': swatchColor(option.color) }}
              disabled={!option.available}
              onClick={() => chooseColor(option)}
              aria-label={`Cor ${option.color}`}
              title={option.color}
            >
              <span />
            </button>
          ))}
        </div>
      </FieldGroup>
      {selectedColor && (
        <FieldGroup label={selectedVariant ? <>Tamanho: <strong>{variantSizeLabel(selectedVariant)}</strong></> : 'Tamanho'}>
          <div className="sizeChips">
            {sizeOptions.map((option) => (
              <button
                type="button"
                key={option.size}
                className={selectedVariant && variantSizeLabel(selectedVariant) === option.size ? 'selected' : ''}
                disabled={!option.available}
                onClick={() => chooseSize(option.size)}
              >
                {option.size}
              </button>
            ))}
          </div>
          {selectedVariant && <small className="variantStockNote">Estoque: {selectedVariant.stock}</small>}
        </FieldGroup>
      )}
      {selectedVariant && (
        <>
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
            <strong>{money(selectedVariant.price * quantity)}</strong>
          </div>
        </>
      )}
      <button className="primaryButton detailActionButton" onClick={onAdd} disabled={!selectedVariant || maxQuantity <= 0}>
        {selectedVariant ? 'Adicionar ao carrinho' : 'Escolha uma variação'}
      </button>
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
      <button className="ghostButton" onClick={onCheckout}>{cart?.canCheckout ? 'Finalizar pedido' : 'Ver pedido mínimo'}</button>
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
              <small>{item.variantName || 'Único'}</small>
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
      {cart?.items?.length === 0 && <EmptyState text="Seu carrinho está vazio." />}
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
      <div className="safeNote"><BadgeCheck size={18} /> Seus dados serão usados apenas para processar o pedido.</div>
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
      setCepMessage('Digite os 8 números do CEP para preencher o endereço.');
      return undefined;
    }
    if (digits.length > 8) {
      setCepTone('danger');
      setCepMessage('CEP deve ter 8 números.');
      return undefined;
    }

    setCepTone('info');
    setCepMessage('Buscando endereço pelo CEP...');
    const timer = window.setTimeout(async () => {
      try {
        await onLookupCep(setAddressForm, digits);
        setCepTone('success');
        setCepMessage('Endereço preenchido pelo CEP. Informe número e complemento, se houver.');
      } catch (err) {
        setCepTone('danger');
        setCepMessage(err.message || 'CEP não encontrado. Confira os números e tente novamente.');
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
          <label><span>Número</span><input value={addressForm.number} onChange={(event) => update('number', event.target.value)} required /></label>
          <label><span>Complemento</span><input value={addressForm.complement} onChange={(event) => update('complement', event.target.value)} /></label>
        </div>
        <label><span>Bairro</span><input value={addressForm.neighborhood} onChange={(event) => update('neighborhood', event.target.value)} required /></label>
        <div className="twoCols">
          <label><span>Cidade</span><input value={addressForm.city} onChange={(event) => update('city', event.target.value)} required /></label>
          <label><span>Estado</span><input value={addressForm.state} onChange={(event) => update('state', event.target.value)} required /></label>
        </div>
        <div className="safeNote"><Truck size={18} /> Depois do endereço, mostramos as opções de frete.</div>
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
        <p>Opções disponíveis para o endereço informado.</p>
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
            <Clipboard size={17} /> Copiar código Pix
          </button>
          {copied && <small className="copyFeedback">Código Pix copiado com sucesso.</small>}
        </div>
      ) : (
        url && <a className="primaryButton linkButton" href={url} target="_blank" rel="noreferrer">Abrir checkout Pix</a>
      )}
      {statusUrl && <a className="secondaryButton linkButton" href={statusUrl}>Acompanhar pedido</a>}
      <a className="ghostButton linkButton" href="https://wa.me/5521997547418" target="_blank" rel="noreferrer">Voltar para o WhatsApp</a>
      <small className="muted">O pagamento será concluído no ambiente seguro da Nuvemshop.</small>
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
            <div className={`orderStatusLine ${statusToneClass(order.publicStatus)}`}>
              <small>Status</small>
              <strong>{order.statusTitle}</strong>
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
    ['SEPARATING_ORDER', 'Separação'],
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
  const orderStopped = ['CANCELLED', 'REFUNDED'].includes(order.publicStatus);

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
          <small>L&LFashion</small>
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
            <span>{item.productName}<small>{item.variantName || 'Único'} | {item.quantity} un</small></span>
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
            <span>Código de rastreio</span>
            <strong>{order.shippingTrackingNumber}</strong>
            <button className="secondaryButton" onClick={copyTracking}><Clipboard size={17} /> Copiar código</button>
            {order.shippingTrackingUrl && (
              <a className="primaryButton linkButton" href={order.shippingTrackingUrl} target="_blank" rel="noreferrer">
                Acompanhar entrega <ExternalLink size={16} />
              </a>
            )}
          </div>
        ) : (
          <div className="safeNote">
            <Truck size={18} />
            {orderStopped ? 'Pedido cancelado. Não há rastreio para este pedido.' : 'Aguardando atualização da loja sobre o rastreio.'}
          </div>
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
              <Clipboard size={17} /> Copiar código Pix
            </button>
            {pixCopied && <small className="copyFeedback">Código Pix copiado com sucesso.</small>}
          </div>
        ) : canOpenCheckout ? (
          <a className="primaryButton linkButton" href={order.checkoutUrl} target="_blank" rel="noreferrer">
            Abrir link de pagamento
          </a>
        ) : (
          <div className="safeNote"><ShieldCheck size={18} /> Pagamento indisponível para o status atual deste pedido.</div>
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

function variantColorLabel(variant) {
  return variant?.color || variant?.variantName || 'Único';
}

function productGalleryImages(product) {
  const urls = [
    product?.imageUrl,
    ...(product?.variants || []).map((variant) => variant.imageUrl)
  ].filter(Boolean);
  return Array.from(new Set(urls));
}

function variantSizeLabel(variant) {
  return variant?.size || 'Único';
}

function colorChoices(variants = []) {
  const groups = new Map();
  for (const variant of variants) {
    const color = variantColorLabel(variant);
    if (!groups.has(color)) {
      groups.set(color, []);
    }
    groups.get(color).push(variant);
  }
  return Array.from(groups.entries()).map(([color, items]) => ({
    color,
    variants: items,
    available: items.some((variant) => variant.available)
  }));
}

function sizeChoices(variants = [], color) {
  const groups = new Map();
  for (const variant of variants.filter((item) => variantColorLabel(item) === color)) {
    const size = variantSizeLabel(variant);
    if (!groups.has(size)) {
      groups.set(size, []);
    }
    groups.get(size).push(variant);
  }
  return Array.from(groups.entries()).map(([size, items]) => ({
    size,
    available: items.some((variant) => variant.available)
  }));
}

function swatchColor(color) {
  const normalized = normalizeColor(color);
  if (COLOR_SWATCHES[normalized]) {
    return COLOR_SWATCHES[normalized];
  }
  const partial = Object.keys(COLOR_SWATCHES).find((knownColor) => normalized.includes(knownColor));
  return partial ? COLOR_SWATCHES[partial] : '#e5e7eb';
}

function isLightSwatch(color) {
  const normalized = normalizeColor(color);
  return ['BRANCO', 'OFF WHITE', 'MARFIM', 'BEGE', 'NUDE'].some((knownColor) => normalized.includes(knownColor));
}

function normalizeColor(color) {
  return String(color || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toUpperCase();
}

function MinimumProgress({ cart }) {
  if (!cart) return null;
  return (
    <div className="minimumCard">
      <div>
        <b>{cart.canCheckout ? 'Pedido mínimo atingido' : 'Progresso do pedido mínimo'}</b>
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
        <p>Carregando L&LFashion</p>
      </div>
    </main>
  );
}

function money(value) {
  const numeric = Number(value || 0);
  return BRL.format(numeric);
}

function formatDate(value) {
  if (!value) return 'Não informada';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Não informada';
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

function statusToneClass(status) {
  if (['CANCELLED', 'REFUNDED', 'ERROR'].includes(status)) {
    return 'danger';
  }
  if (['DELIVERED', 'PAYMENT_CONFIRMED', 'SHIPPED'].includes(status)) {
    return 'success';
  }
  return '';
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
    message: 'Este carrinho já gerou um pedido.'
  };
}

function hasCartStockProblem(cart) {
  return !!cart?.items?.some((item) => Number(item.quantity || 0) > Number(item.stockAvailable || 0));
}
