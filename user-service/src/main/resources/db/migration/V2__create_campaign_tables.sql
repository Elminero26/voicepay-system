CREATE TABLE IF NOT EXISTS commerces (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS campaigns (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    start_date TIMESTAMP NOT NULL,
    status VARCHAR(255) NOT NULL,
    max_retries INT NOT NULL,
    commerce_id BIGINT NOT NULL,
    CONSTRAINT fk_campaigns_commerce FOREIGN KEY (commerce_id) REFERENCES commerces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS campaign_members (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    associated_debt NUMERIC(15, 2) NOT NULL,
    call_status VARCHAR(255) NOT NULL CHECK (call_status IN ('PENDING', 'RINGING', 'COMPLETED', 'NO_ANSWER', 'BUSY')),
    CONSTRAINT fk_members_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE,
    CONSTRAINT fk_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
