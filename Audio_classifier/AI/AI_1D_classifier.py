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
from keras.optimizers import Adam

from AI.AI_audio_data_generator import MarkedAudioDataGenerator
import AI.AI_utils as utils

seed = 42
random.seed = seed
np.random.seed = seed


class BinaryClassifierModelWithGenerator:
    def __init__( self,
                  inputs,
                  outputs,
                  offset,
                  sample_rate,
                  batch_size,
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

        self.MODEL_PATH = "./1d_classifier_model_" + str( self.SAMPLE_RATE ) + ".h5"
        self.LOG_DIR = "./logs/fit//" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")

        self.VALIDATION_SPLIT = 0.125
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
        self.model = load_model( self.MODEL_PATH, custom_objects={'Precision': metrics.Precision(),
                                                                  'Recall': metrics.Recall(),
                                                                  'metrics': [ "Precision", "Recall", "accuracy" ]} )
        assert self.INPUTS == self.model.input_shape[ 2 ]
        assert self.OUTPUTS == self.model.output_shape[ 2 ]


    def create_model( self ):
        print( "Build model" )

        # Build U-Net model
        self.model = Sequential()
        # self.model.add( Dense( 64, input_shape= ( 1, self.INPUTS ), activation="relu" ) )
        # self.model.add( Dense( 32, activation="relu" ) )
        # self.model.add( Dense( self.OUTPUTS , activation="sigmoid") )
        self.model.add( Conv1D( filters = 256, kernel_size = self.INPUTS - self.OUTPUTS + 1, activation = 'relu', input_shape = ( self.INPUTS, 1 ) ) )
        self.model.add( Dropout( 0.05 ) )
        self.model.add( Conv1D( filters = 128, kernel_size = 1, activation = 'relu' ) )
        # self.model.add( MaxPooling1D( pool_size = 2, data_format = "channels_first" ) )
        assert self.model.output_shape[ 1 ] >= self.OUTPUTS
        # self.model.add( Conv1D( filters = 1, kernel_size = self.model.output_shape[ 2 ] - self.OUTPUTS + 1, activation = 'sigmoid', data_format = "channels_first" ) )
        # self.model.add( Flatten() )
        # self.model.add( Reshape( ( 1, -1 ) ) )
        # self.model.add( Dropout( 0.05 ) )
        # self.model.add( Dense( self.OUTPUTS , activation="sigmoid") )
        self.model.add( Conv1D( filters = 1, kernel_size = 1, activation = 'sigmoid' ) )
        print( self.model.output_shape )

        #self.model.compile(optimizer='adam', loss=iou_coef_loss, metrics=[ iou_coef_loss, dice_coef_loss, "accuracy" ])
        self.model.compile(optimizer = Adam(learning_rate=0.01), loss="binary_crossentropy", metrics=[ "accuracy", metrics.Precision(), metrics.Recall() ], sample_weight_mode="temporal" )
        self.model.summary()

    def fit_model( self, epochs = 50 ):
        print( "Fit model" )

        if self.predict_only:
            print("Not possible in predict only mode")
            return

        # Fit model
        earlystopper = EarlyStopping(patience=5, verbose=1, monitor="accuracy")
        checkpointer = ModelCheckpoint(self.MODEL_PATH, verbose=1, save_best_only=True, monitor="accuracy")
        # checkpointer = ModelCheckpoint( self.MODEL_PATH, verbose=1, save_best_only=True )

        self.model.fit_generator(generator=self.learning_generator, epochs=epochs,
                                 callbacks=[earlystopper, checkpointer],
                                 validation_data=self.validation_generator )

        self.epochs_measured += epochs

    def predict_markings( self, input_signal ):
        length = len( input_signal )
        i = 0
        preds = []

        while( i < length - self.INPUTS ):
            pred = self.model.predict( input_signal[ i : i + self.INPUTS ] , verbose=1)
            preds = preds + pred
            i += self.OUTPUTS

        return preds
