# Load libraries
import pandas
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
if( len( sys.argv ) < 2 ):
    print "dataset file not set"
    sys.exit()
url = sys.argv[1]
names = [ 's_-64', 's_-63', 's_-62', 's_-61', 's_-60', 's_-59', 's_-58', 's_-57', 's_-56', 's_-55', 's_-54', 's_-53', 's_-52', 's_-51', 's_-50', 's_-49', 's_-48', 's_-47', 's_-46', 's_-45', 's_-44', 's_-43', 's_-42', 's_-41', 's_-40', 's_-39', 's_-38', 's_-37', 's_-36', 's_-35', 's_-34', 's_-33', 's_-32', 's_-31', 's_-30', 's_-29', 's_-28', 's_-27', 's_-26', 's_-25', 's_-24', 's_-23', 's_-22', 's_-21', 's_-20', 's_-19', 's_-18', 's_-17', 's_-16', 's_-15', 's_-14', 's_-13', 's_-12', 's_-11', 's_-10', 's_-9', 's_-8', 's_-7', 's_-6', 's_-5', 's_-4', 's_-3', 's_-2', 's_-1', 's_0', 's_1', 's_2', 's_3', 's_4', 's_5', 's_6', 's_7', 's_8', 's_9', 's_10', 's_11', 's_12', 's_13', 's_14', 's_15', 's_16', 's_17', 's_18', 's_19', 's_20', 's_21', 's_22', 's_23', 's_24', 's_25', 's_26', 's_27', 's_28', 's_29', 's_30', 's_31', 's_32', 's_33', 's_34', 's_35', 's_36', 's_37', 's_38', 's_39', 's_40', 's_41', 's_42', 's_43', 's_44', 's_45', 's_46', 's_47', 's_48', 's_49', 's_50', 's_51', 's_52', 's_53', 's_54', 's_55', 's_56', 's_57', 's_58', 's_59', 's_60', 's_61', 's_62', 's_63', 's_64', 'Marked' ]
dataset = pandas.read_csv(url,names=names)

print(dataset.shape)

# Split-out validation dataset
X = dataset.drop('Marked',axis=1)
Y = dataset['Marked']

validation_size = 0.20
seed = 7
X_train, X_test, Y_train, Y_test = model_selection.train_test_split(X, Y, test_size=validation_size, random_state=seed)
	
# Test options and evaluation metric
seed = 7
scoring = 'accuracy'

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

f1 = open( "./pickle.jar", "wb")
pickle.dump(mlp,f1,2)
f1.close()

f2 = open( "./pickle4scale.jar", "wb")
pickle.dump(scaler,f2,2)
f2.close()