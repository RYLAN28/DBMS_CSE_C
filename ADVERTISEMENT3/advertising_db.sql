-- =====================================================
-- ADVERTISING MANAGEMENT SYSTEM  (PostgreSQL)
-- Run this in pgAdmin: Query Tool (select database -> Tools -> Query Tool)
-- =====================================================

-- =====================================================
-- 1. DDL  (CREATE / ALTER / DROP / TRUNCATE)
-- =====================================================
DROP VIEW IF EXISTS ad_dashboard CASCADE;
DROP VIEW IF EXISTS ad_report CASCADE;
DROP TABLE IF EXISTS ad_update_log CASCADE;
DROP TABLE IF EXISTS payment CASCADE;
DROP TABLE IF EXISTS campaign_ad CASCADE;
DROP TABLE IF EXISTS campaign CASCADE;
DROP TABLE IF EXISTS advertisement CASCADE;
DROP TABLE IF EXISTS platform CASCADE;
DROP TABLE IF EXISTS advertiser CASCADE;

CREATE TABLE advertiser (
    advertiser_id INT PRIMARY KEY,
    name          VARCHAR(50) NOT NULL,
    email         VARCHAR(80) UNIQUE,
    phone         VARCHAR(15)
);

CREATE TABLE platform (
    platform_id   INT PRIMARY KEY,
    platform_name VARCHAR(40) UNIQUE NOT NULL
);

CREATE TABLE advertisement (
    ad_id         INT PRIMARY KEY,
    title         VARCHAR(60) NOT NULL,
    description   VARCHAR(150),
    budget        NUMERIC(10,2) CHECK (budget > 0),
    status        VARCHAR(15) DEFAULT 'Active',
    ad_type       VARCHAR(15),                 -- Digital / TV / Newspaper
    audience      VARCHAR(40),
    advertiser_id INT REFERENCES advertiser(advertiser_id),
    platform_id   INT REFERENCES platform(platform_id),
    start_date    DATE DEFAULT CURRENT_DATE,
    end_date      DATE DEFAULT (CURRENT_DATE + 30)
);

CREATE TABLE campaign (
    campaign_id INT PRIMARY KEY,
    name        VARCHAR(60) NOT NULL
);

-- link table: one campaign can have many ads
CREATE TABLE campaign_ad (
    campaign_id INT REFERENCES campaign(campaign_id),
    ad_id       INT REFERENCES advertisement(ad_id),
    PRIMARY KEY (campaign_id, ad_id)
);

CREATE TABLE payment (
    payment_id INT PRIMARY KEY,
    ad_id      INT REFERENCES advertisement(ad_id),
    amount     NUMERIC(10,2) NOT NULL,
    method     VARCHAR(20),
    status     VARCHAR(10) DEFAULT 'Paid'
);

-- ALTER examples
ALTER TABLE advertiser    ADD COLUMN city VARCHAR(30);
ALTER TABLE advertiser    DROP COLUMN city;
ALTER TABLE payment       RENAME COLUMN method TO payment_method;

-- =====================================================
-- 2. DML  (INSERT / UPDATE / DELETE / SELECT)
-- =====================================================
INSERT INTO advertiser VALUES
(1, 'Aria Kapoor', 'aria@mail.com',  '9876500001'),
(2, 'Rohan Verma', 'rohan@mail.com', '9876500002'),
(3, 'Nisha Shah',  'nisha@mail.com', '9876500003'),
(4, 'Karan Mehta', 'karan@mail.com', '9876500004');   -- has no ads (for LEFT JOIN demo)

INSERT INTO platform VALUES
(1, 'Instagram'), (2, 'YouTube'), (3, 'Google Ads'),
(4, 'Newspaper'), (5, 'Star TV');

INSERT INTO advertisement
(ad_id, title, description, budget, status, ad_type, audience, advertiser_id, platform_id) VALUES
(101, 'Summer Collection', 'Summer sale reels',  42000, 'Active', 'Digital',   'Youth',    1, 1),
(102, 'Monsoon Essentials','Rainy season offers', 28500, 'Active', 'Digital',   'Families', 2, 2),
(103, 'Weekend Wellness',  'Health supplement',   13750, 'Draft',  'Newspaper', 'Adults',   3, 4),
(104, 'Festive Dhamaka',   'Diwali TV spot',      60000, 'Active', 'TV',        'All',      1, 5),
(105, 'Search Boost',      'Google search ads',   20000, 'Paused', 'Digital',   'Youth',    2, 3);

INSERT INTO campaign VALUES
(1, 'Summer Stories'), (2, 'Monsoon Push'), (3, 'Festive Season');

INSERT INTO campaign_ad VALUES
(1, 101), (2, 102), (2, 105), (3, 104), (3, 103);

INSERT INTO payment VALUES
(1, 101, 42000, 'UPI',        'Paid'),
(2, 102, 15000, 'Card',       'Paid'),
(3, 104, 60000, 'NetBanking', 'Paid'),
(4, 103,  5000, 'Cash',       'Pending');

-- UPDATE
UPDATE advertisement SET status = 'Completed' WHERE ad_id = 101;
UPDATE advertisement SET budget = budget + 5000 WHERE ad_type = 'TV';

-- DELETE
DELETE FROM payment WHERE status = 'Pending';

-- SELECT with WHERE / ORDER BY / LIKE / IN / BETWEEN
SELECT * FROM advertisement;
SELECT title, budget FROM advertisement WHERE budget BETWEEN 20000 AND 50000 ORDER BY budget DESC;
SELECT * FROM advertisement WHERE title LIKE 'S%';
SELECT * FROM advertisement WHERE status IN ('Active', 'Paused');

-- Total cost with 18% tax (same as getTotalCost() in Java)
SELECT ad_id, title, budget, budget * 0.18 AS tax, budget * 1.18 AS total_cost
FROM advertisement;

-- =====================================================
-- 3. AGGREGATE FUNCTIONS  (COUNT, SUM, AVG, MIN, MAX, GROUP BY, HAVING)
-- =====================================================
SELECT COUNT(*)    AS total_ads      FROM advertisement;      -- showTotalAds()
SELECT SUM(budget) AS total_budget   FROM advertisement;      -- showTotalBudget()
SELECT AVG(budget) AS average_budget FROM advertisement;
SELECT MIN(budget) AS lowest, MAX(budget) AS highest FROM advertisement;

-- ads per type
SELECT ad_type, COUNT(*) AS no_of_ads, SUM(budget) AS total_budget
FROM advertisement
GROUP BY ad_type;

-- only types whose total budget is above 40000
SELECT ad_type, SUM(budget) AS total_budget
FROM advertisement
GROUP BY ad_type
HAVING SUM(budget) > 40000;

-- =====================================================
-- 4. JOINS
-- =====================================================
-- INNER JOIN: ad with its advertiser and platform
SELECT a.ad_id, a.title, r.name AS advertiser, p.platform_name, a.budget
FROM advertisement a
INNER JOIN advertiser r ON a.advertiser_id = r.advertiser_id
INNER JOIN platform   p ON a.platform_id   = p.platform_id;

-- LEFT JOIN: all advertisers, even those without ads
SELECT r.name, a.title
FROM advertiser r
LEFT JOIN advertisement a ON r.advertiser_id = a.advertiser_id;

-- RIGHT JOIN: all platforms, even unused ones
SELECT p.platform_name, a.title
FROM advertisement a
RIGHT JOIN platform p ON a.platform_id = p.platform_id;

-- FULL JOIN: ads and payments (unpaid ads and orphan payments both show)
SELECT a.title, pay.amount, pay.payment_method
FROM advertisement a
FULL JOIN payment pay ON a.ad_id = pay.ad_id;

-- Campaign -> ads (many-to-many using the link table)
SELECT c.name AS campaign, a.title AS advertisement, a.budget
FROM campaign c
JOIN campaign_ad ca ON c.campaign_id = ca.campaign_id
JOIN advertisement a ON ca.ad_id = a.ad_id
ORDER BY c.name;

-- JOIN + AGGREGATE: total budget per advertiser
SELECT r.name, COUNT(a.ad_id) AS ads, COALESCE(SUM(a.budget), 0) AS total_budget
FROM advertiser r
LEFT JOIN advertisement a ON r.advertiser_id = a.advertiser_id
GROUP BY r.name
ORDER BY total_budget DESC;

-- JOIN + AGGREGATE: total paid per ad
SELECT a.title, SUM(pay.amount) AS total_paid
FROM advertisement a
JOIN payment pay ON a.ad_id = pay.ad_id
GROUP BY a.title;

-- SUBQUERY: ads with budget above average
SELECT title, budget FROM advertisement
WHERE budget > (SELECT AVG(budget) FROM advertisement);

-- VIEW (like showReport())
CREATE OR REPLACE VIEW ad_report AS
SELECT a.ad_id, a.title, r.name AS advertiser, p.platform_name,
       a.budget, a.budget * 1.18 AS total_cost, a.status
FROM advertisement a
JOIN advertiser r ON a.advertiser_id = r.advertiser_id
JOIN platform   p ON a.platform_id   = p.platform_id;

SELECT * FROM ad_report;

ALTER TABLE advertisement
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Live dashboard view used by the Java web server
CREATE OR REPLACE VIEW ad_dashboard AS
SELECT
    a.ad_id,
    a.title,
    a.status,
    a.end_date,
    CASE
        WHEN a.end_date IS NULL THEN NULL
        ELSE GREATEST(a.end_date - CURRENT_DATE, 0)
    END AS days_left,
    CASE
        WHEN COALESCE(SUM(pay.amount), 0) >= a.budget THEN 'Fully Paid'
        WHEN COALESCE(SUM(pay.amount), 0) > 0 THEN 'Partially Paid'
        ELSE 'Unpaid'
    END AS payment_status,
    CASE
        WHEN a.end_date IS NULL OR a.end_date >= CURRENT_DATE THEN 'Showing'
        ELSE 'Expired'
    END AS show_status,
    a.updated_at
FROM advertisement a
LEFT JOIN payment pay ON pay.ad_id = a.ad_id
GROUP BY a.ad_id, a.title, a.status, a.end_date, a.budget, a.updated_at;

-- Status history used by DBFeatures.showLog()
CREATE TABLE ad_update_log (
    log_id     BIGSERIAL PRIMARY KEY,
    ad_id      INT NOT NULL REFERENCES advertisement(ad_id),
    old_status VARCHAR(15),
    new_status VARCHAR(15),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION log_ad_status_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO ad_update_log (ad_id, old_status, new_status)
        VALUES (NEW.ad_id, OLD.status, NEW.status);
    END IF;
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS advertisement_status_update ON advertisement;
CREATE TRIGGER advertisement_status_update
BEFORE UPDATE ON advertisement
FOR EACH ROW
EXECUTE FUNCTION log_ad_status_change();

CREATE OR REPLACE FUNCTION expire_old_ads()
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    expired_count INTEGER;
BEGIN
    UPDATE advertisement
    SET status = 'Completed'
    WHERE end_date < CURRENT_DATE
      AND status IS DISTINCT FROM 'Completed';

    GET DIAGNOSTICS expired_count = ROW_COUNT;
    RETURN expired_count;
END;
$$;

-- =====================================================
-- 5. TCL  (BEGIN / COMMIT / ROLLBACK / SAVEPOINT)
--    Select and run this whole block together
-- =====================================================
BEGIN;

INSERT INTO advertisement (ad_id, title, description, budget, ad_type, advertiser_id, platform_id)
VALUES (106, 'Winter Edit', 'Winter sale', 30000, 'Digital', 3, 1);

SAVEPOINT after_first_insert;

INSERT INTO payment (payment_id, ad_id, amount, payment_method)
VALUES (5, 106, 30000, 'UPI');

-- oops, undo only the payment
ROLLBACK TO SAVEPOINT after_first_insert;

SELECT * FROM advertisement WHERE ad_id = 106;   -- ad exists
SELECT * FROM payment WHERE payment_id = 5;      -- payment is gone

COMMIT;     -- saves the ad permanently

-- ROLLBACK demo: nothing is saved
BEGIN;
DELETE FROM advertisement WHERE ad_id = 106;
SELECT COUNT(*) FROM advertisement;              -- looks deleted here
ROLLBACK;
SELECT COUNT(*) FROM advertisement;              -- ad 106 is back

-- =====================================================
-- 6. EXTRA: TRUNCATE (run only if you want to wipe a table)
-- =====================================================
-- TRUNCATE TABLE payment;
