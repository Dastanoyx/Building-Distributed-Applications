-- Demo data, so a fresh `docker compose up` has something to sell.
-- ON CONFLICT DO NOTHING makes the migration safe to re-run against an existing database.

INSERT INTO product (id, sku, name, price, stock) VALUES
    (gen_random_uuid(), 'KEY-001', 'Mechanical keyboard',  129.00, 40),
    (gen_random_uuid(), 'MOU-002', 'Wireless mouse',        49.00, 120),
    (gen_random_uuid(), 'HED-003', 'Noise-cancelling headset', 199.00, 25),
    (gen_random_uuid(), 'SCR-004', '27-inch monitor',      329.00, 12),
    (gen_random_uuid(), 'CAB-005', 'USB-C cable',            9.99, 500),
    (gen_random_uuid(), 'DOK-006', 'Docking station',      189.00, 0)
ON CONFLICT (sku) DO NOTHING;
