import os
import random
import warnings
import datetime

from keras.layers import Add, concatenate
from keras.models import load_model
from keras.layers.core import Dropout, Dense, Flatten, Reshape
from keras.layers.convolutional import Conv1D
from keras.layers.pooling import MaxPooling1D
from keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau, Callback
from keras import backend as K, Sequential, metrics, Input, Model

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

class ComputeConfusionMatrixCallback(Callback):
    """Predict data from validation and test dataset, calculate the confusion matrix,
       and write it to a file

  Arguments:
      patience: Number of epochs to wait each time before evaluating the performance
      classifier: the BinaryClassifierModelWithGenerator object wrapping the model to be evaluated
  """

    def __init__(self, patience, classifier):
        super(ComputeConfusionMatrixCallback, self).__init__()
        self.patience = patience
        self.classifier = classifier
        self.metrics = {}
        self.epochs_measured = 0

    def on_train_begin(self, logs=None):
        self.wait = self.patience

    def on_epoch_end(self, epoch, logs=None):
        self.metrics[ self.epochs_measured ] = {}
        self.metrics[ self.epochs_measured ]["validation"] = {}
        self.metrics[ self.epochs_measured ]["test"] = {}

        if logs is not None:
            self.add_model_logs( logs )

        if( self.wait == 0 ):
            self.evaluate_model_generator()
            self.wait = self.patience

        self.write_model_metrics()

    def on_epoch_begin(self, epoch, logs=None):
        self.wait -= 1
        self.epochs_measured += 1

    def add_model_logs( self, logs ):
        for metric in logs:
            self.metrics[ self.epochs_measured ]["validation"][ metric ] = logs[ metric ]

    def evaluate_model_generator( self ):
        self.classifier.learning_generator.shuffle = False
        self.classifier.validation_generator.shuffle = False
        self.classifier.test_generator.shuffle = False

        # y_pred_learn = self.classifier.model.predict_generator( generator = self.classifier.learning_generator, verbose = 1 )
        # y_pred_learn = np.where( y_pred_learn < 0.5, 0, 1 )
        # y_true_learn = self.classifier.learning_generator.get_all_y_true()

        y_pred_val = self.classifier.model.predict_generator( generator = self.classifier.validation_generator, verbose = 1 )
        y_pred_val = np.where( y_pred_val < 0.5, 0, 1 )
        y_true_val = self.classifier.validation_generator.get_all_y_true()

        # self.metrics[ self.epochs_measured ]["learn"] = classification_report( np.reshape( y_true_learn, (-1,) ), np.reshape( y_pred_learn, (-1,) ) )
        self.metrics[ self.epochs_measured ]["validation"]["confusion matrix"] = classification_report( np.reshape( y_true_val, (-1,) ), np.reshape( y_pred_val, (-1,) ) )
        # self.metrics[ self.epochs_measured ]["validation"]["IoU"] = K.eval( iou_coef( y_true_val, y_pred_val ) )

        if self.classifier.IS_TEST_DATA_LABELED:
            y_pred_test = self.classifier.model.predict_generator( generator = self.classifier.test_generator, verbose = 1 )
            y_pred_test = np.where( y_pred_test < 0.5, 0, 1 )
            y_true_test = self.classifier.test_generator.get_all_y_true()

            self.metrics[ self.epochs_measured ]["test"]["confusion matrix"] = classification_report( np.reshape( y_true_test, (-1,) ), np.reshape( y_pred_test, (-1,) ) )
            # self.metrics[ self.epochs_measured ]["test"]["IoU"] = K.eval( iou_coef( y_true_val, y_pred_val ) )

        self.classifier.learning_generator.shuffle = True
        self.classifier.validation_generator.shuffle = True
        self.classifier.test_generator.shuffle = True

    def write_model_metrics(self):
        learn_size = self.classifier.learning_generator.get_item_count() * self.classifier.learning_generator.batch_size
        validation_size = self.classifier.validation_generator.get_item_count() * self.classifier.validation_generator.batch_size
        test_size = self.classifier.test_generator.get_item_count() * self.classifier.test_generator.batch_size

        os.makedirs(self.classifier.LOG_DIR, exist_ok = True )
        results_file = open(self.classifier.LOG_DIR + "\\results.txt", "w")

        results_file.write(self.classifier.MODEL_PATH)
        results_file.write("\nNumber of learning samples: " + str(learn_size))
        results_file.write("\nNumber of validation samples: " + str(validation_size))
        if self.classifier.IS_TEST_DATA_LABELED:
            results_file.write("\nNumber of testing samples: " + str(test_size))

        results_file.write("\n")
        for epoch in self.metrics:
            results_file.write( "\n  Epoch %u \n" % epoch )
            for dataset in self.metrics[ epoch ]:
                results_file.write( "%s dataset\n" % dataset )
                for metric in self.metrics[ epoch ][ dataset ]:
                    results_file.write( metric + "\n")
                    results_file.write( str( self.metrics[ epoch ][ dataset ][ metric ] ) )
                    results_file.write("\n")
                results_file.write("\n")
        results_file.write("\n")

        results_file.close()


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
        self.LOG_DIR = "./logs/fit//" + model_path + "-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")

        self.VALIDATION_SPLIT = 0.0
        test_split = 0.0

        self.evaluation_callback = ComputeConfusionMatrixCallback( -1, self )

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

        # If this is a variable input length convolution, Dumnezeu cu mila
        if( self.model.input_shape[ 1 ] is None and self.model.output_shape[ 1 ] is None ):
            return True
        return False


    def compile_model( self ):
        learning_rate = 0.001  # initial learning rate

        self.model.compile(optimizer = Adam(learning_rate=learning_rate),
                           loss = "mae",
                           metrics = ['mse','mae']
                           )

    def create_model( self ):
        print( "Build model" )

        self.model = Sequential()
        # self.model.add( Dense( 128, input_dim = self.INPUTS, activation="relu" ) )
        # self.model.add( Dense( 96, activation="relu" ) )
        # # self.model.add( Dense( 32, activation="relu" ) )
        # self.model.add( Dense( 32, activation="tanh" ) )
        # self.model.add( Dense( self.OUTPUTS , activation="linear") )

        self.model.add( Conv1D( filters = 128, kernel_size = self.INPUTS - self.OUTPUTS + 1 , activation = 'relu', input_shape = ( None, 1 ) ) )
        self.model.add( Conv1D( filters = 64, kernel_size = 1, activation = 'relu' ) )
        self.model.add( Conv1D( filters = 16, kernel_size = 1, activation = 'tanh' ) )
        self.model.add( Conv1D( filters = 1, kernel_size = 1, activation = 'linear' ) )

        # inputs = Input(shape = (self.INPUTS,) )
        # layer_1 = Dense( 96, activation="relu")(inputs)
        # added1 = concatenate( [ inputs, layer_1 ] )
        # layer_2 = Dense( 48, activation = "relu")(added1)
        # added2 = concatenate( [ inputs, layer_2 ] )
        # layer_3 = Dense( 16, activation = "tanh")(added2)
        # outputs = Dense( 1, activation="linear")(layer_3)
        #
        # self.model = Model(inputs = inputs, outputs = outputs)

        print( self.model.output_shape )

        self.compile_model()
        self.model.summary()

    def fit_model( self, epochs = 50 ):
        print( "Fit model" )

        if self.predict_only:
            print("Not possible in predict only mode")
            return

        # Fit model
        min_learning_rate = 0.0001  # once the learning rate reaches this value, do not decrease it further
        learning_rate_reduction_factor = 0.707  # the factor used when reducing the learning rate -> learning_rate *= learning_rate_reduction_factor
        patience = 5  # how many epochs to wait before reducing the learning rate when the loss plateaus

        # earlystopper = EarlyStopping(patience=5, verbose=1, mode="max", monitor="f1")
        checkpointer = ModelCheckpoint(self.MODEL_PATH, verbose=1, save_best_only=True, mode="min", monitor="loss")
        learning_rate_reduction = ReduceLROnPlateau(monitor='loss', mode="min", patience=patience, verbose=1,
                                                    factor=learning_rate_reduction_factor, min_lr=min_learning_rate)

        # checkpointer = ModelCheckpoint( self.MODEL_PATH, verbose=1, save_best_only=True )

        self.model.fit_generator(generator=self.learning_generator, epochs=epochs,
                                 callbacks=[checkpointer, learning_rate_reduction, self.evaluation_callback],
                                 validation_data=self.validation_generator )

    def predict_markings( self, input_signal ):
        input_length = input_signal.shape[ 0 ]
        output_length = ( input_length - self.INPUTS + self.OUTPUTS )

        inputs = np.reshape( input_signal, ( 1, input_length, 1 ) )

        # Flatten the predictions
        preds = self.model.predict( inputs, verbose = 0 )
        assert preds.shape[ 1 ] == output_length, "Output shape different than the configured model INPUT/OUTPUT sizes. Expected %d, got %d. Check the model structure" % ( output_length, preds.shape[ 1 ] )
        preds = np.reshape( preds, newshape = ( output_length, ) )

        return preds
