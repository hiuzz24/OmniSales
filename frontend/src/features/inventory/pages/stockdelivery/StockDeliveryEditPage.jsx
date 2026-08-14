import { lazy, Suspense } from 'react';

const StockDeliveryCreatePage = lazy(() => import('./StockDeliveryCreatePage'));

export default function StockDeliveryEditPage() {
  return (
    <Suspense fallback={null}>
      <StockDeliveryCreatePage mode="edit" />
    </Suspense>
  );
}
