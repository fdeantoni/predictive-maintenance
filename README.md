# Predictive Maintenance

This project demonstrates how to perform simple predictive maintenance with XGBoost. We build the model first in R and 
then export it to a Spark job.

## Prepare Data

The first step is to do the data exploration. For this project we will use the sample data provided by the Predictive 
Maintenance Modeling Guide in [Microsoft R Samples](https://github.com/Microsoft/SQL-Server-R-Services-Samples).

In the `explore` directory, run the script `download.sh` to download the sample data. Once the sample data has been
downloaded to the `explore/build` directory, load the data into postgres so we can easily manipulate it. 

The scripts in this project assume you are running postgres inside a docker container using an image created by running 
the `create-postgres-image.sh` script. Once running, you can enter the interactive `psql` shell as follows: 

    $ sudo docker exec -it -u postgres postgres psql

In a `psql` session create the `maintanance` database:

    create database maintenance;     

Once database is created, connect to it:

    \c maintenance
    
Now load the downloaded data into the database using the `load.sql` script:

    \i explore/load.sql
    
Once the script completes, all data should be loaded into 5 separate tables, including a denormalized `machine_log` 
table. From this table we will export the data of one machine: Machine 746. Run the `export.sql` script to export the
data:

    \i explore/export.sql
    
This will generate file `build/machine_746.csv` which we will use further in R.

## Create Model

Now that we have our machine data, we can create the XGBoost model in R.      

Open up `explore/xgboost_model.R` in [RStudio](https://rstudio.com/products/rstudio/download/). To run the code you 
should have the packages `zoo`, `caret`, `e1071`, and `xgboost` installed.

*NOTE*: If using OSX you will have to install `libomp` for `caret` install to succeed:

    brew install libomp
    
Run all the paragraphs in the `explore/xgboost_model.R`. The very last paragraph will save the XGBoost model to a file
called `explore/build/Machine746.xgboost.model`. We will use this file in the Spark job we will create next.

## Create Spark Streaming Job

Now that we have our XGBoost model saved to a file, we can create a Spark streaming job that will load up this file and 
apply it to make a prediction on any new machine data that comes in.

To create the job, run the following command:

    $ sbt job/assembly
    
This will create a jar file `job/target/scala-2.11/job-assembly-1.0-SNAPSHOT.jar`. 

## Run Simulator

To simulate a Kafka instance receiving machine data, we will use the provided Simulator. This project launches a test 
Kafka instance and sends data from the CSV file to the correct Kafka topic.

Run the simulator as follows:

    $ sbt simulator/run
    
                