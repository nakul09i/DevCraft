export interface Product {
  id: string;
  name: string;
  category: string;
  price: number;
  description: string;
  image: string;
  unit: string;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface WebOrder {
  orderId: string;
  orderNumber: string;
  source: 'WEBSITE' | 'SMS' | 'MANUAL';
  customerName: string;
  phone: string;
  deliveryAddress: string;
  pinCode: string;
  dueDate: string;
  deliveryTime: string;
  orderNotes?: string;
  paymentMethod: 'COD' | 'UPI' | 'CARD';
  paymentStatus: 'PENDING' | 'PAID' | 'COD';
  status: 'NEW' | 'CONFIRMED' | 'PROCESSING' | 'READY' | 'OUT_FOR_DELIVERY' | 'COMPLETED' | 'CANCELLED';
  totalAmount: number;
  targetDurationMinutes: number;
  createdAt: number;
  updatedAt: number;
  items: Array<{
    description: string;
    quantity: number;
    price: number;
  }>;
}
