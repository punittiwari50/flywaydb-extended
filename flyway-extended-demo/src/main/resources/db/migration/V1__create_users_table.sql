-- Create users table
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert some initial data
INSERT INTO users (id, username, email) VALUES (1, 'admin', 'admin@example.com');
INSERT INTO users (id, username, email) VALUES (2, 'user1', 'user1@example.com');
