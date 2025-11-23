DROP TABLE IF EXISTS "user_accounts";
DROP TABLE IF EXISTS "users";
DROP TABLE IF EXISTS "transaction_journal";
DROP TABLE IF EXISTS "processed_transactions";
DROP TABLE IF EXISTS "transaction_outbox_dlq";
DROP TABLE IF EXISTS "transaction_outbox";
DROP TABLE IF EXISTS "idempotency_keys";
DROP TABLE IF EXISTS "accounts";

CREATE TABLE "accounts" (
    "id" UUID PRIMARY KEY,
    "balance" DECIMAL(19, 2) NOT NULL,
    "status" VARCHAR(20) NOT NULL
);

CREATE TABLE "idempotency_keys" (
    "key" UUID PRIMARY KEY, 
    "created_at" TIMESTAMP NOT NULL
);

CREATE TABLE "transaction_outbox" (
    "idempotency_key" UUID PRIMARY KEY,
    "transaction_id" UUID NOT NULL UNIQUE,
    "payload" VARCHAR(2048) NOT NULL,
    "status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    "attempts" INT DEFAULT 0 NOT NULL,
    "failure_count" INT DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP NOT NULL
);

CREATE TABLE "transaction_outbox_dlq" (
    "id" UUID PRIMARY KEY,
    "payload" VARCHAR(2048) NOT NULL,
    "reason" VARCHAR(1024),
    "moved_at" TIMESTAMP NOT NULL
);

CREATE TABLE "processed_transactions" (
    "idempotency_key" UUID PRIMARY KEY,
    "processed_at" TIMESTAMP NOT NULL
);

CREATE TABLE "transaction_journal" (
    "sequence_id" BIGSERIAL PRIMARY KEY,
    "idempotency_key" UUID NOT NULL UNIQUE,
    "transaction_id" UUID NOT NULL,
    "timestamp" TIMESTAMP NOT NULL,
    "command_type" VARCHAR(50) NOT NULL,
    "account_id_from" UUID NOT NULL,
    "account_id_to" UUID,
    "amount" DECIMAL(19, 2)
);

CREATE TABLE "users" (
    "id" UUID PRIMARY KEY,
    "nickname" VARCHAR(255) NOT NULL
);

CREATE TABLE "user_accounts" (
    "user_id" UUID NOT NULL,
    "account_id" UUID NOT NULL,
    PRIMARY KEY ("user_id", "account_id"),
    FOREIGN KEY ("user_id") REFERENCES "users"("id"),
    FOREIGN KEY ("account_id") REFERENCES "accounts"("id")
);
