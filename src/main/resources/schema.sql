-- DDL for users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

-- DDL for weather table
CREATE TABLE weather (
    id BIGSERIAL PRIMARY KEY,
    location_name VARCHAR(255),
    current_temp_c DOUBLE PRECISION
);

-- DDL for mood table
CREATE TABLE mood (
    id BIGSERIAL PRIMARY KEY,
    mood VARCHAR(255),
    date TIMESTAMP,
    mood_rating INTEGER,
    notes TEXT,
    weather_id BIGINT,
    user_id BIGINT,
    CONSTRAINT fk_weather FOREIGN KEY(weather_id) REFERENCES weather(id),
    CONSTRAINT fk_user FOREIGN KEY(user_id) REFERENCES users(id)
);
