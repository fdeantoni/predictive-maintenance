# Process the CSV data into a feature set using below functions
library(zoo)

# Function to calculate the time since an event denoted by a "1"
time_since <- function(data) {
    idx <- which(data == 1)
    if(length(idx) == 0) {
        data <- 1:length(data)
    } else {
        for (n in 1:length(idx)) {
            if(n == 1) {
                data[1:idx[1]] <- c(1:idx[1])
            } else if(n == length(idx)) {
                p_idx <- idx[n-1]
                s_idx <- p_idx+1
                e_idx <- length(data)
                l <- e_idx - p_idx
                data[s_idx:e_idx] <- c(1:l)
            } else {
                p_idx <- idx[n-1]
                s_idx <- p_idx+1
                e_idx <- idx[n]
                l <- e_idx - p_idx
                data[s_idx:e_idx] <- c(1:l)
            }
        }
    }
    return(data)
}

convert <- function(data, windows = c(12, 24, 36), lookahead = 84) {
    # set the time column
    features <- data[,1:5]
    # calculate the mean and std for volt, rotate, pressure, and vibration
    for (window in windows) {
        means <- rollapplyr(data[,2:5], window, mean, fill = NA)
        colnames(means) <- paste(colnames(means), paste("mean", window, sep = ""), sep = "_")
        stds <- rollapplyr(data[,2:5], window, sd, fill = NA)
        colnames(stds) <- paste(colnames(stds), paste("std", window, sep = ""), sep = "_")
        features <- cbind(features, means)
        features <- cbind(features, stds)
    }
    # calculate the last service time per component
    features <- cbind(features, data[,10:13])
    features$service1 <- time_since(features$service1)
    features$service2 <- time_since(features$service2)
    features$service3 <- time_since(features$service3)
    features$service4 <- time_since(features$service4)
    # calculate the last error time per error type
    features <- cbind(features, data[,6:9])
    features$error1 <- time_since(features$error1)
    features$error2 <- time_since(features$error2)
    features$error3 <- time_since(features$error3)
    features$error4 <- time_since(features$error4)
    # set the failure label based on lookahead
    labels <- as.numeric(apply(data[,14:17], 1, any))
    failures <- which(labels == 1)
    for (failure in failures) {
        if(failure < lookahead) {
            labels[1:failure] <- 1
        } else {
            i <- failure - lookahead
            labels[i:failure] <- 1
        }
    }
    # combine all and return results
    result <- list("data" = features, "label" = labels)
    return(result)
}

# Machines data
m746 <- convert(read.csv("build/machine_746.csv"))
# m868 <- convert(read.csv("build/machine_868.csv"))
# m918 <- convert(read.csv("build/machine_918.csv"))
# m932 <- convert(read.csv("build/machine_932.csv"))
# m999 <- convert(read.csv("build/machine_999.csv"))

# convert the dataset to features and labels
features <- m746$data[36:nrow(m746$data),-1]
labels <- m746$label[36:length(m746$label)]

# Split between  train and test
library(caret)
set.seed(275)
trainIdx <- createDataPartition(as.factor(labels), p = 0.8, list = FALSE)
features.train <- features[trainIdx,]
features.test <- features[-trainIdx,]
labels.train <- labels[trainIdx]
labels.test <- labels[-trainIdx]

# Prepare data for xgboost
library(Matrix)
train <- list("data" = as(as.matrix(features.train), "sparseMatrix"), "label" = labels.train)
test <- list("data" = as(as.matrix(features.test), "sparseMatrix"), "label" = labels.test)

# Train the xgboost model
require(xgboost)
model <- xgboost(
    data = train$data,
    label = train$label,
    max.depth = 20,
    eta = 1,
    nthread = 2,
    nround = 10,
    objective = "binary:logistic"
)

# Test our model
pred <- predict(model, test$data)
err <- mean(as.numeric(pred > 0.5) != test$label)
print(paste("test-error=", err))

# show real failure with predicted failure
cbind("truth" = test$label[which(test$label > 0)], "pred" = as.numeric(pred[which(test$label > 0)] > 0.5))

# Show confusion matrix
confusion_matrix <- confusionMatrix(as.factor(as.numeric(pred > 0.5)), as.factor(test$label), positive="1")
print(confusion_matrix)

# Show importance matrix
importance_matrix <- xgb.importance(model = model)
print(importance_matrix)
xgb.plot.importance(importance_matrix = importance_matrix)

# Save the model for use in Spark job (you may need to create the models directory first)
xgb.save(model, "build/Machine746.xgboost.model")

