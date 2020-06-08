# Load libraries
import struct

import pandas
import numpy as np
import pickle

from sklearn import model_selection

from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import classification_report,confusion_matrix

import sys
# Load dataset
# if( len( sys.argv ) < 2 ):
#     print( "training set and validation set files not provided" )
#     sys.exit()
from tqdm import tqdm

trainingSet = "e:\\datasets\\257-1 reduced\\full.bin"
#validationSet = sys.argv[2]

def load_marked_signal_file( file_path, start_idx, seq_len, split ):

    OFFSET = 128
    OUTPUT_SIZE = 1
    INPUT_SIZE = OFFSET * 2 + OUTPUT_SIZE
    SAMPLE_RATE = 96000

    RECORD_FORMAT = "<" + INPUT_SIZE * "h" + OUTPUT_SIZE * "B"
    SAMPLE_VALUE_MUL_FACTOR = 2**15
    MAX_SAMPLE_VALUE = 2**15 - 1
    MIN_SAMPLE_VALUE = -2**15
    RECORD_SIZE = struct.calcsize( RECORD_FORMAT )

    INCLUDE_PROB = 0.5

    with open( file_path, mode='rb') as file:
        file.seek( start_idx * RECORD_SIZE )
        file_content = file.read( seq_len * RECORD_SIZE )
        assert len( file_content ) % RECORD_SIZE == 0, "File: " + file_path + ", start idx: " + str( start_idx ) + ", len: " + str( len( file_content ) )
        record_cnt = len( file_content ) // RECORD_SIZE

        train_size = int( ( record_cnt - (int)(record_cnt * split) ) * INCLUDE_PROB )
        train_cnt = 0
        train_samples = np.zeros( ( train_size, INPUT_SIZE ), dtype=np.float_ )
        train_markings = np.zeros( ( train_size, OUTPUT_SIZE ), dtype=np.int32 )

        test_size = int( record_cnt * INCLUDE_PROB - train_size )
        test_cnt = 0
        test_samples = np.zeros( ( test_size, INPUT_SIZE ), dtype=np.float_ )
        test_markings = np.zeros( ( test_size, OUTPUT_SIZE ), dtype=np.int32 )

        for idx in tqdm( range( 0, record_cnt ) ):
            record = struct.unpack( RECORD_FORMAT, file_content[ idx * RECORD_SIZE : ( idx + 1 ) * RECORD_SIZE ] )
            inputs = np.asarray( record[ :INPUT_SIZE ], dtype=np.float_ )
            outputs = np.asarray( record[ INPUT_SIZE: ], dtype=np.int32 )

            if np.random.rand() < INCLUDE_PROB and ( train_cnt + test_cnt < train_size + test_size ):
                if ( np.random.rand() < split and test_cnt < test_size ) or train_cnt >= train_size:
                    test_samples[ test_cnt ] = inputs / SAMPLE_VALUE_MUL_FACTOR
                    test_markings[ test_cnt ] = outputs
                    test_cnt += 1
                else:
                    train_samples[ train_cnt ] = inputs / SAMPLE_VALUE_MUL_FACTOR
                    train_markings[ train_cnt ] = outputs
                    train_cnt += 1


    return( train_samples, train_markings, test_samples, test_markings )

testing_size = 0.125
seed = 7
X_train, Y_train, X_test, Y_test = load_marked_signal_file( trainingSet, 0, 2 * 10**6, testing_size )

print( X_train.shape )

#X_test = validation_dataset.drop('Marked',axis=1);
#Y_test = validation_dataset['Marked'];
	
# Test options and evaluation metric
scoring = 'f1'

scaler = StandardScaler()
scaler.fit(X_train)
X_train = scaler.transform(X_train, copy = False )
X_test = scaler.transform(X_test, copy = False )

print( "start training" )
mlp = MLPClassifier(hidden_layer_sizes=(96,48),max_iter=500, verbose = True, tol=0.00001, shuffle = True )
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