# Load libraries
import pandas
import numpy
import pickle
from pandas.plotting import scatter_matrix
import matplotlib.pyplot as plt
from sklearn import model_selection

from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import classification_report,confusion_matrix

import sys
# Load dataset
if( len( sys.argv ) < 3 ):
    print "training set and validation set files not provided"
    sys.exit()
trainingSet = sys.argv[1]
validationSet = sys.argv[2]

dt = numpy.dtype([( 's_-64', 'f4' ), ( 's_-63', 'f4' ), ( 's_-62', 'f4' ), ( 's_-61', 'f4' ), ( 's_-60', 'f4' ), ( 's_-59', 'f4' ), ( 's_-58', 'f4' ), ( 's_-57', 'f4' ), ( 's_-56', 'f4' ), ( 's_-55', 'f4' ), ( 's_-54', 'f4' ), ( 's_-53', 'f4' ), ( 's_-52', 'f4' ), ( 's_-51', 'f4' ), ( 's_-50', 'f4' ), ( 's_-49', 'f4' ), ( 's_-48', 'f4' ), ( 's_-47', 'f4' ), ( 's_-46', 'f4' ), ( 's_-45', 'f4' ), ( 's_-44', 'f4' ), ( 's_-43', 'f4' ), ( 's_-42', 'f4' ), ( 's_-41', 'f4' ), ( 's_-40', 'f4' ), ( 's_-39', 'f4' ), ( 's_-38', 'f4' ), ( 's_-37', 'f4' ), ( 's_-36', 'f4' ), ( 's_-35', 'f4' ), ( 's_-34', 'f4' ), ( 's_-33', 'f4' ), ( 's_-32', 'f4' ), ( 's_-31', 'f4' ), ( 's_-30', 'f4' ), ( 's_-29', 'f4' ), ( 's_-28', 'f4' ), ( 's_-27', 'f4' ), ( 's_-26', 'f4' ), ( 's_-25', 'f4' ), ( 's_-24', 'f4' ), ( 's_-23', 'f4' ), ( 's_-22', 'f4' ), ( 's_-21', 'f4' ), ( 's_-20', 'f4' ), ( 's_-19', 'f4' ), ( 's_-18', 'f4' ), ( 's_-17', 'f4' ), ( 's_-16', 'f4' ), ( 's_-15', 'f4' ), ( 's_-14', 'f4' ), ( 's_-13', 'f4' ), ( 's_-12', 'f4' ), ( 's_-11', 'f4' ), ( 's_-10', 'f4' ), ( 's_-9', 'f4' ), ( 's_-8', 'f4' ), ( 's_-7', 'f4' ), ( 's_-6', 'f4' ), ( 's_-5', 'f4' ), ( 's_-4', 'f4' ), ( 's_-3', 'f4' ), ( 's_-2', 'f4' ), ( 's_-1', 'f4' ), ( 's_0', 'f4' ), ( 's_1', 'f4' ), ( 's_2', 'f4' ), ( 's_3', 'f4' ), ( 's_4', 'f4' ), ( 's_5', 'f4' ), ( 's_6', 'f4' ), ( 's_7', 'f4' ), ( 's_8', 'f4' ), ( 's_9', 'f4' ), ( 's_10', 'f4' ), ( 's_11', 'f4' ), ( 's_12', 'f4' ), ( 's_13', 'f4' ), ( 's_14', 'f4' ), ( 's_15', 'f4' ), ( 's_16', 'f4' ), ( 's_17', 'f4' ), ( 's_18', 'f4' ), ( 's_19', 'f4' ), ( 's_20', 'f4' ), ( 's_21', 'f4' ), ( 's_22', 'f4' ), ( 's_23', 'f4' ), ( 's_24', 'f4' ), ( 's_25', 'f4' ), ( 's_26', 'f4' ), ( 's_27', 'f4' ), ( 's_28', 'f4' ), ( 's_29', 'f4' ), ( 's_30', 'f4' ), ( 's_31', 'f4' ), ( 's_32', 'f4' ), ( 's_33', 'f4' ), ( 's_34', 'f4' ), ( 's_35', 'f4' ), ( 's_36', 'f4' ), ( 's_37', 'f4' ), ( 's_38', 'f4' ), ( 's_39', 'f4' ), ( 's_40', 'f4' ), ( 's_41', 'f4' ), ( 's_42', 'f4' ), ( 's_43', 'f4' ), ( 's_44', 'f4' ), ( 's_45', 'f4' ), ( 's_46', 'f4' ), ( 's_47', 'f4' ), ( 's_48', 'f4' ), ( 's_49', 'f4' ), ( 's_50', 'f4' ), ( 's_51', 'f4' ), ( 's_52', 'f4' ), ( 's_53', 'f4' ), ( 's_54', 'f4' ), ( 's_55', 'f4' ), ( 's_56', 'f4' ), ( 's_57', 'f4' ), ( 's_58', 'f4' ), ( 's_59', 'f4' ), ( 's_60', 'f4' ), ( 's_61', 'f4' ), ( 's_62', 'f4' ), ( 's_63', 'f4' ), ( 's_64', 'f4' ), ( 'Marked', 'i4' )])
data = numpy.fromfile(trainingSet, dtype=dt)
training_dataset = pandas.DataFrame(data)

data = numpy.fromfile(validationSet, dtype=dt)
validation_dataset = pandas.DataFrame(data)

print( training_dataset.shape )
print( validation_dataset.shape )

# Split-out validation training_dataset
X_train = training_dataset.drop('Marked',axis=1)
Y_train = training_dataset['Marked']


#seed = 7
#X_train, X_test, Y_train, Y_test = model_selection.train_test_split(X, Y, test_size=validation_size, random_state=seed)

X_test = validation_dataset.drop('Marked',axis=1);
Y_test = validation_dataset['Marked'];
	
# Test options and evaluation metric
scoring = 'f1-score'

scaler = StandardScaler()
scaler.fit(X_train)
X_train = scaler.transform(X_train)
X_test = scaler.transform(X_test)

print "start training"
mlp = MLPClassifier(hidden_layer_sizes=(64,32),max_iter=500, verbose = True, tol=0.00001, shuffle = True )
mlp.fit(X_train,Y_train)

predictions = mlp.predict(X_test)

print(confusion_matrix(Y_test,predictions))
print(classification_report(Y_test,predictions))

#f1 = open( "./pickle.jar", "wb")
#pickle.dump(mlp,f1,2)
#f1.close()

#f2 = open( "./pickle4scale.jar", "wb")
#pickle.dump(scaler,f2,2)
#f2.close()