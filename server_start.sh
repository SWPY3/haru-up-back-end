#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/docker"

select_service() {
    local services=($(docker compose config --services))
    local show_all=$1

    echo "Select service${show_all:+ to $show_all}:" >&2
    [ -n "$show_all" ] && echo "1. all" >&2

    local start_index=${show_all:+2}
    start_index=${start_index:-1}

    for i in "${!services[@]}"; do
        echo "$((i + start_index)). ${services[$i]}" >&2
    done

    read -p "Enter your choice: " choice

    [ -n "$show_all" ] && [ "$choice" = "1" ] && echo "all" && return

    local service_index=$((choice - start_index))
    [ "$service_index" -ge 0 ] && [ "$service_index" -lt "${#services[@]}" ] && echo "${services[$service_index]}" && return

    echo "Invalid choice. Exiting." >&2
    exit 1
}

up() {
    local service=$(select_service "start")
    local build_flag=""

    # Check if --build flag is passed
    if [[ "$1" == "--build" ]] || [[ "$1" == "-b" ]]; then
        build_flag="--build"
        echo "Starting Docker containers with build..."
    else
        echo "Starting Docker containers..."
    fi

    if [ "$service" = "all" ]; then
        docker compose up -d $build_flag || exit 1
    else
        docker compose up -d $build_flag $service || exit 1
    fi

    echo "Containers started!"
    echo "Application: http://localhost:8080"
    echo "Swagger UI: http://localhost:8080/swagger-ui.html"
}

down() {
    local service=$(select_service "stop")
    echo "Stopping Docker containers..."

    if [ "$service" = "all" ]; then
        docker compose down || exit 1
    else
        docker compose stop $service || exit 1
    fi

    echo "Containers stopped!"
}

restart() {
    local service=$(select_service "restart")
    local build_flag=""

    # Check if --build flag is passed
    if [[ "$1" == "--build" ]] || [[ "$1" == "-b" ]]; then
        build_flag="--build"
        echo "Restarting Docker containers with build..."
    else
        echo "Restarting Docker containers..."
    fi

    if [ "$service" = "all" ]; then
        docker compose down && docker compose up -d $build_flag || exit 1
    else
        docker compose stop $service && docker compose up -d $build_flag $service || exit 1
    fi

    echo "Containers restarted!"
    echo "Application: http://localhost:8080"
    echo "Swagger UI: http://localhost:8080/swagger-ui.html"
}

bash() {
    local service=$(select_service)
    docker ps | grep -q "$service" || { echo "Error: $service container is not running" >&2; exit 1; }

    # Check if container has bash, otherwise use sh
    if docker exec "$service" test -f /bin/bash 2>/dev/null; then
        docker exec -it "$service" /bin/bash
    else
        docker exec -it "$service" /bin/sh
    fi
}

logs() {
    local service=$(select_service)
    echo "Showing logs for $service..."
    docker compose logs -f $service || exit 1
}

clean() {
    local service=$(select_service "clean")
    echo "Cleaning Docker containers and volumes..."

    if [ "$service" = "all" ]; then
        docker compose down -v || exit 1
        echo "All containers and volumes removed!"
    else
        echo "Stopping and removing container: $service"

        # Get container ID before removing
        local container_id=$(docker compose ps -q $service 2>/dev/null)

        # Get volumes used by this container
        local volumes=""
        if [ -n "$container_id" ]; then
            volumes=$(docker inspect $container_id --format '{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}} {{end}}{{end}}' 2>/dev/null)
        fi

        # Stop and remove the container
        docker compose rm -sf $service || exit 1

        # Remove associated volumes
        if [ -n "$volumes" ]; then
            echo "Found volumes: $volumes"
            for volume in $volumes; do
                echo "Removing volume: $volume"
                docker volume rm "$volume" 2>/dev/null && echo "✓ Volume removed: $volume" || {
                    echo "✗ Warning: Could not remove volume $volume"
                }
            done
            echo "Container and volumes removed for service: $service"
        else
            echo "No volumes found for service: $service"
            echo "Container removed!"
        fi
    fi
}

# Main script logic
case "$1" in
    up)
        up "$2"
        ;;
    down)
        down
        ;;
    restart)
        restart "$2"
        ;;
    bash)
        bash
        ;;
    logs)
        logs
        ;;
    clean)
        clean
        ;;
    *)
        echo "Usage: ./server-start.sh {up|down|restart|bash|logs|clean}"
        echo "  up [--build|-b]      Start containers (with optional build)"
        echo "  restart [--build|-b] Restart containers (with optional build)"
        exit 1
        ;;
esac
