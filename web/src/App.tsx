import React, { useState, useEffect } from 'react';
import { ShoppingCart, Package, Search, Sparkles, CheckCircle2, Clock, MapPin, Truck, Phone, User, Calendar, CreditCard } from 'lucide-react';
import { Product, CartItem, WebOrder } from './types';
import { createCloudOrder, subscribeToOrder } from './firebase';

const SAMPLE_PRODUCTS: Product[] = [
  {
    id: 'p1',
    name: 'Food Parcel / Meal Box',
    category: 'Catering & Meals',
    price: 250,
    description: 'Freshly prepared thali parcel with 4 chapatis, 2 paneer curries, rice, dal & dessert.',
    image: 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=400&q=80',
    unit: 'parcel'
  },
  {
    id: 'p2',
    name: 'Kurta Set (Cotton Navy Blue)',
    category: 'Garments & Apparel',
    price: 850,
    description: '100% Pure Cotton handcrafted ethnic kurta set with fine stitching.',
    image: 'https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?auto=format&fit=crop&w=400&q=80',
    unit: 'piece'
  },
  {
    id: 'p3',
    name: 'Portland Cement (50kg Bag)',
    category: 'Hardware & Materials',
    price: 380,
    description: 'Grade 53 OPC Cement bag for high strength structural construction.',
    image: 'https://images.unsplash.com/photo-1589939705384-5185137a7f0f?auto=format&fit=crop&w=400&q=80',
    unit: 'bag'
  },
  {
    id: 'p4',
    name: 'Executive Office Chair',
    category: 'Furniture & Decor',
    price: 2499,
    description: 'Ergonomic mesh high-back chair with lumbar support and pneumatic height control.',
    image: 'https://images.unsplash.com/photo-1580481072645-022f9a6d1273?auto=format&fit=crop&w=400&q=80',
    unit: 'chair'
  },
  {
    id: 'p5',
    name: 'Hardcover Notebooks (Pack of 5)',
    category: 'Stationery',
    price: 350,
    description: '200 Pages single line spiral bound A4 notebooks with thick paper.',
    image: 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=400&q=80',
    unit: 'pack'
  }
];

export function App() {
  const [cart, setCart] = useState<CartItem[]>([]);
  const [showCheckout, setShowCheckout] = useState(false);
  const [activeOrder, setActiveOrder] = useState<WebOrder | null>(null);
  const [showAiAssistant, setShowAiAssistant] = useState(false);
  const [filterCategory, setFilterCategory] = useState<string>('ALL');

  // Checkout Form State
  const [customerName, setCustomerName] = useState('Nakul');
  const [phone, setPhone] = useState('9876543210');
  const [deliveryAddress, setDeliveryAddress] = useState('MP Nagar Zone 1, Bhopal');
  const [pinCode, setPinCode] = useState('462011');
  const [dueDate, setDueDate] = useState('2026-08-31');
  const [deliveryTime, setDeliveryTime] = useState('14:00 - 16:00');
  const [orderNotes, setOrderNotes] = useState('Please handle with care');
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'UPI' | 'CARD'>('COD');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const cartTotal = cart.reduce((sum, item) => sum + item.product.price * item.quantity, 0);

  const addToCart = (product: Product) => {
    setCart(prev => {
      const existing = prev.find(i => i.product.id === product.id);
      if (existing) {
        return prev.map(i => i.product.id === product.id ? { ...i, quantity: i.quantity + 1 } : i);
      }
      return [...prev, { product, quantity: 1 }];
    });
  };

  const updateQuantity = (productId: string, delta: number) => {
    setCart(prev => prev.map(i => {
      if (i.product.id === productId) {
        const newQ = i.quantity + delta;
        return newQ > 0 ? { ...i, quantity: newQ } : null;
      }
      return i;
    }).filter(Boolean) as CartItem[]);
  };

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (cart.length === 0) return;
    setIsSubmitting(true);

    const orderId = `web_ord_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
    const orderNumber = `#WEB-${Math.floor(1000 + Math.random() * 9000)}`;

    const newOrder: WebOrder = {
      orderId,
      orderNumber,
      source: 'WEBSITE',
      customerName,
      phone,
      deliveryAddress,
      pinCode,
      dueDate,
      deliveryTime,
      orderNotes,
      paymentMethod,
      paymentStatus: paymentMethod === 'COD' ? 'COD' : 'PAID',
      status: 'NEW',
      totalAmount: cartTotal,
      targetDurationMinutes: 45,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      items: cart.map(i => ({
        description: i.product.name,
        quantity: i.quantity,
        price: i.product.price
      }))
    };

    await createCloudOrder(newOrder);
    setIsSubmitting(false);
    setShowCheckout(false);
    setCart([]);
    setActiveOrder(newOrder);
  };

  // Subscribe to live order updates when tracking
  useEffect(() => {
    if (!activeOrder?.orderId) return;
    const unsub = subscribeToOrder(activeOrder.orderId, 'user-default', (updated) => {
      setActiveOrder(updated);
    });
    return () => unsub();
  }, [activeOrder?.orderId]);

  const categories = ['ALL', ...Array.from(new Set(SAMPLE_PRODUCTS.map(p => p.category)))];
  const filteredProducts = filterCategory === 'ALL'
    ? SAMPLE_PRODUCTS
    : SAMPLE_PRODUCTS.filter(p => p.category === filterCategory);

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Top Header */}
      <header style={{ backgroundColor: 'white', borderBottom: '1px solid var(--border)', position: 'sticky', top: 0, zIndex: 30 }}>
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', height: '70px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div style={{ backgroundColor: '#1565c0', color: 'white', width: '40px', height: '40px', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' }}>
              DC
            </div>
            <div>
              <h1 style={{ fontSize: '1.25rem', fontWeight: 800, color: 'var(--text-primary)' }}>DevCraft Store</h1>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Direct Omnichannel Customer Portal</p>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <button className="btn btn-outline" onClick={() => setShowAiAssistant(true)} style={{ color: '#6a1b9a', borderColor: '#e1bee7' }}>
              <Sparkles size={18} />
              <span>AI Assistant</span>
            </button>

            <button className="btn btn-primary" onClick={() => setShowCheckout(true)} disabled={cart.length === 0} style={{ position: 'relative' }}>
              <ShoppingCart size={18} />
              <span>Cart ({cart.reduce((a, b) => a + b.quantity, 0)})</span>
              {cart.length > 0 && (
                <span style={{ marginLeft: '0.5rem', backgroundColor: 'rgba(255,255,255,0.2)', padding: '0.1rem 0.4rem', borderRadius: '4px', fontSize: '0.75rem' }}>
                  ₹{cartTotal}
                </span>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Hero Banner */}
      <section style={{ backgroundColor: '#1e293b', color: 'white', padding: '2.5rem 0' }}>
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1.5rem' }}>
          <div>
            <span className="badge badge-website" style={{ backgroundColor: 'rgba(56,189,248,0.2)', color: '#38bdf8', marginBottom: '0.75rem' }}>
              REALTIME OMNICHANNEL ENGINE
            </span>
            <h2 style={{ fontSize: '2rem', fontWeight: 800, marginBottom: '0.5rem' }}>Place Orders Directly to DevCraft App</h2>
            <p style={{ color: '#94a3b8', maxWidth: '600px', fontSize: '0.95rem' }}>
              Orders placed here synchronize instantly into the merchant's DevCraft Android App with operational SLA timers and live tracking.
            </p>
          </div>
          {activeOrder && (
            <button className="btn" onClick={() => setActiveOrder(activeOrder)} style={{ backgroundColor: '#2e7d32', color: 'white' }}>
              <Package size={18} />
              <span>Track Order {activeOrder.orderNumber}</span>
            </button>
          )}
        </div>
      </section>

      {/* Main Content */}
      <main className="container" style={{ flex: 1, padding: '2rem 1rem' }}>
        {/* Category Filters */}
        <div style={{ display: 'flex', gap: '0.5rem', overflowX: 'auto', paddingBottom: '1rem', marginBottom: '1.5rem' }}>
          {categories.map(cat => (
            <button
              key={cat}
              className={`btn ${filterCategory === cat ? 'btn-primary' : 'btn-outline'}`}
              onClick={() => setFilterCategory(cat)}
              style={{ padding: '0.4rem 1rem', fontSize: '0.85rem' }}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Product Grid */}
        <div className="grid-products">
          {filteredProducts.map(product => {
            const inCart = cart.find(i => i.product.id === product.id);
            return (
              <div key={product.id} style={{ backgroundColor: 'white', borderRadius: '1rem', border: '1px solid var(--border)', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                <img src={product.image} alt={product.name} style={{ width: '100%', height: '180px', objectFit: 'cover' }} />
                <div style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', flex: 1 }}>
                  <span style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--primary)', textTransform: 'uppercase' }}>{product.category}</span>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0.25rem 0' }}>{product.name}</h3>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '1rem', flex: 1 }}>{product.description}</p>
                  
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'auto' }}>
                    <div>
                      <span style={{ fontSize: '1.25rem', fontWeight: 800 }}>₹{product.price}</span>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>/{product.unit}</span>
                    </div>

                    {inCart ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', backgroundColor: '#f1f5f9', borderRadius: '0.5rem', padding: '0.25rem' }}>
                        <button className="btn btn-outline" style={{ padding: '0.2rem 0.5rem' }} onClick={() => updateQuantity(product.id, -1)}>-</button>
                        <span style={{ fontWeight: 700 }}>{inCart.quantity}</span>
                        <button className="btn btn-outline" style={{ padding: '0.2rem 0.5rem' }} onClick={() => updateQuantity(product.id, 1)}>+</button>
                      </div>
                    ) : (
                      <button className="btn btn-primary" onClick={() => addToCart(product)}>
                        Add to Cart
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </main>

      {/* Checkout Modal */}
      {showCheckout && (
        <div className="modal-backdrop" onClick={() => setShowCheckout(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginBottom: '1rem' }}>Complete Web Order</h2>
            
            <form onSubmit={handlePlaceOrder} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div>
                <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>Customer Name</label>
                <input type="text" value={customerName} onChange={e => setCustomerName(e.target.value)} required style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }} />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div>
                  <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>Phone Number</label>
                  <input type="text" value={phone} onChange={e => setPhone(e.target.value)} required style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }} />
                </div>
                <div>
                  <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>PIN Code</label>
                  <input type="text" value={pinCode} onChange={e => setPinCode(e.target.value)} required style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }} />
                </div>
              </div>

              <div>
                <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>Delivery Address</label>
                <textarea value={deliveryAddress} onChange={e => setDeliveryAddress(e.target.value)} required style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }} rows={2} />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div>
                  <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>Delivery Date</label>
                  <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)} required style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }} />
                </div>
                <div>
                  <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>Preferred Time</label>
                  <input type="text" value={deliveryTime} onChange={e => setDeliveryTime(e.target.value)} style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }} />
                </div>
              </div>

              <div>
                <label style={{ fontSize: '0.85rem', fontWeight: 600 }}>Payment Method</label>
                <select value={paymentMethod} onChange={e => setPaymentMethod(e.target.value as any)} style={{ width: '100%', padding: '0.5rem', borderRadius: '0.375rem', border: '1px solid var(--border)', marginTop: '0.25rem' }}>
                  <option value="COD">Cash on Delivery (COD)</option>
                  <option value="UPI">UPI / GPay / PhonePe</option>
                  <option value="CARD">Credit / Debit Card</option>
                </select>
              </div>

              <div style={{ borderTop: '1px solid var(--border)', paddingTop: '1rem', marginTop: '0.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 800, fontSize: '1.1rem', marginBottom: '1rem' }}>
                  <span>Total Payable:</span>
                  <span>₹{cartTotal}</span>
                </div>

                <div style={{ display: 'flex', gap: '0.75rem' }}>
                  <button type="button" className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowCheckout(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={isSubmitting}>
                    {isSubmitting ? 'Placing Order...' : 'Confirm & Place Order'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Live Tracking Modal */}
      {activeOrder && (
        <div className="modal-backdrop" onClick={() => setActiveOrder(null)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div>
                <span className="badge badge-website">LIVE ORDER TRACKING</span>
                <h2 style={{ fontSize: '1.25rem', fontWeight: 800, marginTop: '0.25rem' }}>Order {activeOrder.orderNumber}</h2>
              </div>
              <button className="btn btn-outline" style={{ padding: '0.2rem 0.5rem' }} onClick={() => setActiveOrder(null)}>X</button>
            </div>

            {/* Progress Stepper */}
            <div style={{ backgroundColor: '#f8fafc', padding: '1rem', borderRadius: '0.75rem', marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', fontWeight: 700, marginBottom: '0.5rem' }}>
                <span style={{ color: activeOrder.status === 'NEW' ? '#0369a1' : '#15803d' }}>1. PLACED</span>
                <span style={{ color: ['CONFIRMED', 'PROCESSING', 'READY', 'OUT_FOR_DELIVERY', 'COMPLETED'].includes(activeOrder.status) ? '#15803d' : '#94a3b8' }}>2. CONFIRMED</span>
                <span style={{ color: ['PROCESSING', 'READY', 'OUT_FOR_DELIVERY', 'COMPLETED'].includes(activeOrder.status) ? '#15803d' : '#94a3b8' }}>3. PROCESSING</span>
                <span style={{ color: ['OUT_FOR_DELIVERY', 'COMPLETED'].includes(activeOrder.status) ? '#15803d' : '#94a3b8' }}>4. DELIVERING</span>
              </div>
              <div style={{ height: '8px', backgroundColor: '#e2e8f0', borderRadius: '4px', overflow: 'hidden' }}>
                <div style={{
                  height: '100%',
                  backgroundColor: '#2e7d32',
                  transition: 'width 0.5s ease',
                  width: activeOrder.status === 'NEW' ? '25%' :
                        activeOrder.status === 'CONFIRMED' ? '50%' :
                        activeOrder.status === 'PROCESSING' ? '75%' : '100%'
                }} />
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.9rem' }}>
              <div><strong>Customer:</strong> {activeOrder.customerName} ({activeOrder.phone})</div>
              <div><strong>Address:</strong> {activeOrder.deliveryAddress} - {activeOrder.pinCode}</div>
              <div><strong>Delivery Date:</strong> {activeOrder.dueDate} ({activeOrder.deliveryTime})</div>
              <div><strong>Payment:</strong> {activeOrder.paymentMethod} ({activeOrder.paymentStatus})</div>

              <div style={{ borderTop: '1px solid var(--border)', paddingTop: '0.75rem', marginTop: '0.5rem' }}>
                <strong>Items ({activeOrder.items.length}):</strong>
                {activeOrder.items.map((item, idx) => (
                  <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginTop: '0.25rem' }}>
                    <span>{item.quantity}x {item.description}</span>
                    <span>₹{item.price * item.quantity}</span>
                  </div>
                ))}
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 800, fontSize: '1rem', marginTop: '0.5rem' }}>
                <span>Total Amount:</span>
                <span>₹{activeOrder.totalAmount}</span>
              </div>
            </div>

            <button className="btn btn-primary" style={{ width: '100%', marginTop: '1.25rem' }} onClick={() => setActiveOrder(null)}>
              Done
            </button>
          </div>
        </div>
      )}

      {/* AI Assistant Modal */}
      {showAiAssistant && (
        <div className="modal-backdrop" onClick={() => setShowAiAssistant(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Sparkles size={20} color="#6a1b9a" />
                <h2 style={{ fontSize: '1.1rem', fontWeight: 800 }}>DevCraft Shopping Assistant</h2>
              </div>
              <button className="btn btn-outline" style={{ padding: '0.2rem 0.5rem' }} onClick={() => setShowAiAssistant(false)}>X</button>
            </div>

            <div style={{ backgroundColor: '#f3e5f5', padding: '1rem', borderRadius: '0.75rem', marginBottom: '1rem', fontSize: '0.85rem', color: '#4a148c' }}>
              "Hi! I can help you find products, customize quantities, or answer delivery questions in Hindi, Hinglish, or English."
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              <button className="btn btn-outline" style={{ textAlign: 'left', justifyContent: 'flex-start' }} onClick={() => { addToCart(SAMPLE_PRODUCTS[0]); setShowAiAssistant(false); }}>
                • Add 2 Food Parcels for Bhopal delivery tomorrow
              </button>
              <button className="btn btn-outline" style={{ textAlign: 'left', justifyContent: 'flex-start' }} onClick={() => { addToCart(SAMPLE_PRODUCTS[1]); setShowAiAssistant(false); }}>
                • Add Cotton Navy Blue Kurta Set
              </button>
              <button className="btn btn-outline" style={{ textAlign: 'left', justifyContent: 'flex-start' }} onClick={() => { addToCart(SAMPLE_PRODUCTS[2]); setShowAiAssistant(false); }}>
                • Add 50kg Cement Bag
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
