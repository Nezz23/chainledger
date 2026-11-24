-- One row per block; the hash is unique so a block can never be stored twice.
create table blocks (
    block_index     bigint      primary key,
    hash            varchar(64) not null unique,
    previous_hash   varchar(64) not null,
    merkle_root     varchar(64) not null,
    difficulty_bits integer     not null check (difficulty_bits between 1 and 64),
    nonce           bigint      not null,
    mined_at        bigint      not null
);

create table transactions (
    id                varchar(64) primary key,
    block_index       bigint      not null references blocks (block_index) on delete cascade,
    position          integer     not null,
    sender            varchar(40) not null,
    sender_public_key text        not null,
    recipient         varchar(40) not null,
    amount            bigint      not null check (amount > 0),
    fee               bigint      not null check (fee >= 0),
    tx_nonce          bigint      not null,
    created_at        bigint      not null,
    signature         text        not null,
    unique (block_index, position)
);

-- Address history is the main read path besides loading the chain.
create index transactions_sender_idx on transactions (sender);
create index transactions_recipient_idx on transactions (recipient);
