#!/bin/bash
IMAGE=timescale/timescaledb:latest-pg11
docker run -d --name postgres -p 5432:5432 -e POSTGRES_PASSWORD=postgres -v "${PWD}":/explore $IMAGE
sleep 4
