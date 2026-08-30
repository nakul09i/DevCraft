import { initializeApp } from 'firebase/app';
import { getFirestore, collection, addDoc, doc, setDoc, onSnapshot } from 'firebase/firestore';
import { WebOrder } from './types';

// Read configuration safely from environment variables (Vite)
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSy_DEV_CRAFT_MOCK_KEY",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "devcraft-app.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "devcraft-app",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "devcraft-app.appspot.com",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "1234567890",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:1234567890:web:abcdef"
};

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);

// Save order to cloud Firestore under merchant account
export async function createCloudOrder(order: WebOrder, userId: string = "user-default"): Promise<string> {
  try {
    const ordersRef = collection(db, 'users', userId, 'orders');
    const orderDoc = doc(ordersRef, order.orderId);
    await setDoc(orderDoc, {
      ...order,
      userId,
      isDeleted: false,
      syncState: "SYNCED"
    });
    
    // Also store locally in localStorage for backup
    const local = JSON.parse(localStorage.getItem('devcraft_web_orders') || '[]');
    localStorage.setItem('devcraft_web_orders', JSON.stringify([order, ...local]));
    return order.orderId;
  } catch (e) {
    console.warn("Cloud write failed, saving to localStorage backup", e);
    const local = JSON.parse(localStorage.getItem('devcraft_web_orders') || '[]');
    localStorage.setItem('devcraft_web_orders', JSON.stringify([order, ...local]));
    return order.orderId;
  }
}

// Live tracking listener
export function subscribeToOrder(orderId: string, userId: string = "user-default", onUpdate: (order: WebOrder) => void) {
  try {
    const orderDoc = doc(db, 'users', userId, 'orders', orderId);
    return onSnapshot(orderDoc, (snap) => {
      if (snap.exists()) {
        onUpdate(snap.data() as WebOrder);
      }
    });
  } catch (e) {
    console.warn("Firestore subscription unavailable", e);
    return () => {};
  }
}
