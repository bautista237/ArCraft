# ArCraft — Useful Commands

## Run the project
docker start arcraft_db && mvn spring-boot:run

## First time DB setup (only if container doesn't exist yet)
docker run -d \
  --name arcraft_db \
  -e POSTGRES_DB=arcraft \
  -e POSTGRES_USER=arcraft \
  -e POSTGRES_PASSWORD=arcraft \
  -p 5432:5432 \
  postgres:16-alpine

## Check if DB container is running
docker ps

## Stop the DB container
docker stop arcraft_db

## Git
git add . && git commit -m "your message" && git push
