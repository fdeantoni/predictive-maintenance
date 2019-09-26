#!/bin/bash

# Script to download source data

SAMPLE_URL="https://github.com/microsoft/SQL-Server-R-Services-Samples/raw/master/PredictiveMaintenanceModelingGuide/Data"

mkdir -p build

cd build || exit
wget $SAMPLE_URL/telemetry.csv
wget $SAMPLE_URL/errors.csv
wget $SAMPLE_URL/maint.csv
wget $SAMPLE_URL/machines.csv
wget $SAMPLE_URL/failures.csv

