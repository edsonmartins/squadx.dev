-- Align the seeded admin password with the documented default credentials.
UPDATE users
SET password = '$2a$10$ZXVo/O3Y.2mN6kz6oPgp0.q2Bk3tNz1oVUHuq3l1FDboAMzj/GxAa'
WHERE email = 'admin@squadx.dev';
