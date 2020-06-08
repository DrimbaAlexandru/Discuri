import os
import random
import warnings
import datetime


from keras.models import load_model
from keras.layers.core import Dropout, Dense, Flatten, Reshape
from keras.layers.convolutional import Conv1D
from keras.layers.pooling import MaxPooling1D
from keras.callbacks import EarlyStopping, ModelCheckpoint
from keras import backend as K, Sequential, metrics

import numpy as np
import tensorflow as tf
from keras.optimizers import Adam, SGD, RMSprop, Adamax
from sklearn.metrics import classification_report
from tensorflow_core.python.keras.optimizer_v2.learning_rate_schedule import ExponentialDecay

from AI.AI_audio_data_generator import MarkedAudioDataGenerator
import AI.AI_utils as utils

seed = 42
random.seed = seed
np.random.seed = seed

# m * x * y
# m - number of images
# x, y - image dimensions
def iou_coef(y_true, y_pred, smooth=1):
    intersection = K.sum(y_true * y_pred, axis=[1,2])
    union = K.sum(y_true,[1,2])+K.sum(y_pred,[1,2])-intersection
    iou = K.mean((intersection + smooth) / (union + smooth), axis=0)
    return iou

def iou_coef_loss(yt,yp,smooth=1):
    return 1 - iou_coef(yt,yp,smooth)

def f1(y_true, y_pred):
    y_pred = K.round(y_pred)
    tp = K.sum(K.cast(y_true*y_pred, 'float'), axis=0)
    tn = K.sum(K.cast((1-y_true)*(1-y_pred), 'float'), axis=0)
    fp = K.sum(K.cast((1-y_true)*y_pred, 'float'), axis=0)
    fn = K.sum(K.cast(y_true*(1-y_pred), 'float'), axis=0)

    p = tp / (tp + fp + K.epsilon())
    r = tp / (tp + fn + K.epsilon())

    f1 = 2*p*r / (p+r+K.epsilon())
    f1 = tf.where(tf.is_nan(f1), tf.zeros_like(f1), f1)
    return K.mean(f1)

def f1_loss(y_true, y_pred):
    tp = K.sum(K.cast(y_true*y_pred, 'float'), axis=0)
    tn = K.sum(K.cast((1-y_true)*(1-y_pred), 'float'), axis=0)
    fp = K.sum(K.cast((1-y_true)*y_pred, 'float'), axis=0)
    fn = K.sum(K.cast(y_true*(1-y_pred), 'float'), axis=0)

    p = tp / (tp + fp + K.epsilon())
    r = tp / (tp + fn + K.epsilon())

    f1 = 2*p*r / (p+r+K.epsilon())
    f1 = tf.where(tf.is_nan(f1), tf.zeros_like(f1), f1)
    return 1 - K.mean(f1)

class BinaryClassifierModelWithGenerator:
    def __init__( self,
                  inputs,
                  outputs,
                  offset,
                  sample_rate,
                  model_path,
                  batch_size = None,
                  train_path_in = None,     # Folder containing multiple signal files
                  test_path_in = None,      # Folder containing multiple signal files
                  train_test_same = False,
                  test_data_labeled = None,
                  ):

        self.INPUTS = inputs
        self.OUTPUTS = outputs
        self.OFFSET = offset
        self.SAMPLE_RATE = sample_rate
        self.batch_size = batch_size

        self.IS_TEST_DATA_LABELED = test_data_labeled

        self.MODEL_PATH = model_path
        self.LOG_DIR = "./logs/fit//" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")

        self.VALIDATION_SPLIT = 0.25
        test_split = 0.125

        self.epochs_measured = 0

        self.predict_only = train_path_in is None \
                            or ( test_path_in is None and not train_test_same ) \
                            or test_data_labeled is None

        if self.predict_only:
            return

        # Get training data files
        # Returns a list of file names in the training path
        training_files = next( os.walk( train_path_in ) )[2]

        training_sequences = []
        testing_sequences = []

        for file in training_files:
            file_sample_count = utils.get_marked_signal_file_length( train_path_in + file )
            batches_per_file = ( file_sample_count + batch_size - 1 ) // batch_size
            for start_idx in range( 0, batches_per_file ):
                training_sequences.append( ( train_path_in + file, start_idx * batch_size ) )

        np.random.shuffle( training_sequences )

        if( not train_test_same ):
            # Get test IDs
            # Returns a list of file names in the testing path
            testing_files = next( os.walk( test_path_in ) )[2]

            for file in testing_files:
                file_sample_count = utils.get_marked_signal_file_length( test_path_in + file )
                batches_per_file = ( file_sample_count + batch_size - 1 ) // batch_size
                for start_idx in range( 0, batches_per_file ):
                    testing_sequences.append( ( test_path_in + file, start_idx * batch_size ) )
        else:
            # Use data from the training set for testing
            testing_sequences = training_sequences[ :int( len( training_sequences ) * test_split ) ]
            training_sequences = training_sequences[ int( len( training_sequences ) * test_split ): ]

        learning_sequences = training_sequences[ int( len( training_sequences ) * self.VALIDATION_SPLIT ): ]
        validation_sequences = training_sequences[ :int( len( training_sequences ) * self.VALIDATION_SPLIT ) ]


        self.learning_generator = MarkedAudioDataGenerator( self.INPUTS,
                                                            self.OUTPUTS,
                                                            self.OFFSET,
                                                            learning_sequences,
                                                            batch_size,
                                                            True )
        self.validation_generator = MarkedAudioDataGenerator( self.INPUTS,
                                                              self.OUTPUTS,
                                                              self.OFFSET,
                                                              validation_sequences,
                                                              batch_size,
                                                              True )
        self.test_generator = MarkedAudioDataGenerator( self.INPUTS,
                                                        self.OUTPUTS,
                                                        self.OFFSET,
                                                        testing_sequences,
                                                        batch_size,
                                                        True )

    def save_model( self ):
        # Save the model
        self.model.save( self.MODEL_PATH )

    def load_model( self ):
        self.model = load_model( self.MODEL_PATH, compile = False )
        self.compile_model()

        if( self.INPUTS == self.model.input_shape[ 1 ] and self.OUTPUTS == self.model.output_shape[ 1 ] ):
            return True
        return False


    def compile_model( self ):
        self.model.compile(optimizer = Adam(),
                           loss = "binary_crossentropy",
                           metrics=[ "accuracy",
                                     metrics.Precision(),
                                     metrics.Recall(),
                                     f1 ]#,
                          # sample_weight_mode="temporal"
                           )

    def create_model( self ):
        print( "Build model" )

        # Build U-Net model
        self.model = Sequential()
        self.model.add( Dense( 128, input_dim = self.INPUTS, activation="relu" ) )
        self.model.add( Dense( 48, activation="relu" ) )
        self.model.add( Dense( self.OUTPUTS , activation="sigmoid") )
        # self.model.add( Conv1D( filters = 128, kernel_size = self.INPUTS - self.OUTPUTS + 1 , activation = 'relu', input_shape = ( self.INPUTS, 1 ) ) )
        # self.model.add( Dropout( 0.1 ) )
        # self.model.add( Conv1D( filters = 64, kernel_size = 1, activation = 'relu' ) )
        # self.model.add( Dropout( 0.05 ) )
        # self.model.add( Conv1D( filters = 32, kernel_size = 1, activation = 'relu' ) )
        # self.model.add( MaxPooling1D( pool_size = 2, data_format = "channels_first" ) )
        # assert self.model.output_shape[ 1 ] >= self.OUTPUTS
        # self.model.add( Conv1D( filters = 1, kernel_size = self.model.output_shape[ 1 ] - self.OUTPUTS + 1, activation = 'sigmoid' ) )
        # self.model.add( Conv1D( filters = 1, kernel_size = 1, activation = 'sigmoid' ) )
        print( self.model.output_shape )

        self.compile_model()
        self.model.summary()

    def fit_model( self, epochs = 50 ):
        print( "Fit model" )

        if self.predict_only:
            print("Not possible in predict only mode")
            return

        # Fit model
        earlystopper = EarlyStopping(patience=5, verbose=1, monitor="f1")
        checkpointer = ModelCheckpoint(self.MODEL_PATH, verbose=1, save_best_only=True, monitor="f1")
        # checkpointer = ModelCheckpoint( self.MODEL_PATH, verbose=1, save_best_only=True )

        self.model.fit_generator(generator=self.learning_generator, epochs=epochs,
                                 callbacks=[earlystopper, checkpointer],
                                 validation_data=self.validation_generator )

        self.epochs_measured += epochs

    def predict_markings( self, input_signal ):
        input_length = input_signal.shape[ 0 ]
        output_length = ( input_length - self.INPUTS + self.OUTPUTS )
        batch_size = ( output_length + self.OUTPUTS - 1 ) // self.OUTPUTS

        inputs = np.zeros( ( batch_size, ) + self.model.input_shape[ 1 : ] )
        probabilities = np.zeros( output_length )

        i = 0
        item_nr = 0

        while( i <= input_length - self.INPUTS ):
            inputs[ item_nr ] = np.reshape( np.asarray( input_signal[ i : i + self.INPUTS ] ), self.model.input_shape[ 1 : ] )
            item_nr += 1
            i += self.OUTPUTS

        # If the input cannot be divided into sequen cial chunks, the last chunk must be completed individually, and will overlap with the previous one
        if( ( input_length - ( self.INPUTS - self.OUTPUTS ) ) % self.OUTPUTS != 0 ):
            assert item_nr == batch_size - 1
            inputs[ batch_size - 1 ] = np.reshape( input_signal[ input_length - self.INPUTS : ], self.model.input_shape[ 1 : ] )
        else:
            assert item_nr == batch_size

        # Flatten the predictions
        preds = self.model.predict( inputs, verbose = 0, batch_size = batch_size )
        preds = np.reshape( preds, newshape = ( -1, ) )

        # The last batch item needs individual processing, because its input might not be in sequence with the previous inputs
        probabilities[ 0 : ( batch_size - 1 ) * self.OUTPUTS ] = preds[  0 : ( batch_size - 1 ) * self.OUTPUTS ]
        probabilities[ output_length - self.OUTPUTS : ] = preds[  ( batch_size - 1 ) * self.OUTPUTS : ]

        return preds
