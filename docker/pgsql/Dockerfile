FROM postgres:17.2

# Set environment variables
ENV POSTGRES_USER=postgres \
    POSTGRES_DB=postgres \
    TZ=Asia/Seoul \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

# Set working directory
WORKDIR /postgres_17_6

# Copy configuration files
COPY conf/postgresql.conf /etc/postgresql/postgresql.conf

# Copy initialization scripts
COPY initdb/ /docker-entrypoint-initdb.d/

# Set shared memory size and ulimits
RUN echo "kernel.shmmax = 1073741824" >> /etc/sysctl.conf && \
    echo "kernel.shmall = 262144" >> /etc/sysctl.conf

# Expose PostgreSQL port
EXPOSE 5432

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
    CMD pg_isready -U postgres -d postgres -h 127.0.0.1

# Set stop signal
STOPSIGNAL SIGINT

# Run PostgreSQL with custom config
CMD ["postgres", "-c", "config_file=/etc/postgresql/postgresql.conf"]
