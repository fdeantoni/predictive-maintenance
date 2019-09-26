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
    
## Launch Spark Job

We can launch the Spark streaming job to generate our maintenance predictions. We will launch our job using 
`spark-submit`. Make sure you have [Spark 2.4.4](https://spark.apache.org/downloads.html) installed. Submit the job as 
follows:

    $ $SPARK_HOME/bin/spark-submit \
        --class com.example.machine.Maintenance \
        --master local[*] \
        job/target/scala-2.11/job-assembly-1.0-SNAPSHOT.jar --models explore/build                 
        
*NOTE*: this job will create a `./checkpoint` folder. If restarting from scratch, make sure to remove this folder first 
before starting the job again.

*TIP*: To reduce logging by Spark, you may want to configure `$SPARK_HOME/conf/logging.properties` with the following to 
reduce the amount of logs generated:
    
    log4j.logger.org.apache.spark=ERROR
    log4j.logger.com.example=DEBUG
    
If your Spark installation does not have a `logging.properties` file yet, create one from the template and add the above
lines to it.    

The Spark job will now run and wait for Kafka (which is part of the simulator - see below) to come online. Once it can
connect to Kafka, the prediction job will start processing data. Let's start the simulator now.

## Run Simulator

To simulate a Kafka instance receiving machine data, we will use the provided Simulator. This project launches a test 
Kafka instance and sends data from the CSV file to the correct Kafka topic.

Run the simulator as follows:

    $ sbt simulator/run

Now that the simulator is sending data, you should see predictions being generated by the Spark job. Note that 
predictions are only generated after the job receives at least 36 data points, so it will take a while for the 
predictions to show.

## Validate Predictions

To validate we are getting the same predictions between R and the Spark job, you can check it as follows in R:

    predict(model, as(as.matrix(features["120",]), "sparseMatrix"))

If you run the above in R you will get the prediction for line 120 in the CSV file. Simply look for the prediction
at `tick` 120 generated by the Spark job to validate they are the same.

## Some Comments

This is of course a toy project that makes predictions on the same data on which the model was created (i.e. our
simulator is sending the same data we used for learning). This means the predictions are of course near perfect. 

The Spark micro-batch size must also be a little shorter than the data rate at which new data comes into Kafka. This
to ensure that the batch processing takes place with only one data point at a time. If this is not the case the 
predictions will not match. Of course, this is less then ideal in a real production scenario, but the code can be made 
more efficient to handle batches better.

Nonetheless, it does demonstrate how all the different pieces fit together. It shows how you can generate an XGBoost 
model in R, and then use that model directly in Spark Streaming. 

            