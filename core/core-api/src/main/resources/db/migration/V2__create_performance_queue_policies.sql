CREATE TABLE PERFORMANCE_QUEUE_POLICIES (
    performance_id NUMBER(19, 0) NOT NULL,
    queue_mode VARCHAR2(20),
    queue_level VARCHAR2(20),
    max_active_users NUMBER(10, 0),
    admit_limit_per_tick NUMBER(10, 0),
    entry_token_ttl_seconds NUMBER(10, 0),
    session_ttl_seconds NUMBER(10, 0),
    preopen_queue_start_at TIMESTAMP,
    waiting_room_message VARCHAR2(255),
    reason VARCHAR2(255),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR2(255) NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR2(255),
    CONSTRAINT pk_performance_queue_policies PRIMARY KEY (performance_id),
    CONSTRAINT fk_performance_queue_policies_performance
        FOREIGN KEY (performance_id) REFERENCES PERFORMANCES (id)
);
