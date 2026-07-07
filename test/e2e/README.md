# Test/E2E

## Structure

```
e2e/
├── auth/                         Authentication UI
│   └── auth.spec.js              Login page (validation, success, error)
├── audit/                        Audit Logs UI
│   └── audit.spec.js             Audit log page
├── catalog/                      Product Catalog UI
│   ├── category.spec.js         Category management page
│   ├── product.spec.js          Product listing page (search, filter, pagination)
│   ├── product-create.spec.js    Product create page (validation, happy path)
│   ├── product-detail.spec.js    Product detail page (tabs, delete)
│   ├── product-edit.spec.js       Product edit page (update, validation)
│   └── product-import.spec.js     Excel import flow
├── channel/                      Channel Management UI
│   └── channel.spec.js          Channel connection page
├── customer/                     Customer Management UI
│   └── customer.spec.js         Customer page
├── inventory/                    Inventory Management UI
│   ├── inventory-list.spec.js    Inventory list page
│   ├── inventory-detail.spec.js  Inventory item detail page
│   ├── stock-delivery.spec.js   Stock delivery page
│   ├── stock-receive.spec.js    Stock receive page
│   ├── stocktake.spec.js        Stocktake page
│   └── stock-transfer.spec.js    Stock transfer page
├── order/                       Order Management UI
│   └── order.spec.js            Order page
└── warehouse/                    Warehouse & Supplier UI
    ├── warehouse.spec.js        Warehouse management page
    └── supplier.spec.js         Supplier page
```

## Running Tests

```bash
npm run test:e2e      # E2E only (defined in package.json)
npm test             # All tests
npm run test:headed  # Visual mode
```

## Naming Convention

- Part A: Listing page tests (`A1`, `A2`, ...)
- Part B: Create page tests (`B1`, `B2`, ...)
- Part C: Detail page tests (`C1`, `C2`, ...)
- Part D: Edit page tests (`D1`, `D2`, ...)
- `PC-E2E-*`: Product Create specific tests

## Helpers

- `loginAsManager(page)` from `utils/product-helpers.js`
- `loginAsOwner(page)`, `loginAsOperations(page)` from `utils/warehouse-helpers.js`
