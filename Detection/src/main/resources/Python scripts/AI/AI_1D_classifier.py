import os
import random
import warnings
import datetime


from keras.models import load_model
from keras.layers.core import Dropout, Dense, Flatten, Reshape
from keras.layers.convolutional import Conv1D
from keras.layers.pooling import MaxPooling1D
from keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau, Callback
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
        self.LOG_DIR = "./logs/fit//" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S")

        self.VALIDATION_SPLIT = 0.25
        test_split = 0.125

        self.evaluation_callback = ComputeConfusionMatrixCallback( 10, self )

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
        learning_rate = 0.000625  # initial learning rate

        self.model.compile(optimizer = Adam(learning_rate=learning_rate),
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
        self.model.add( Dense( 64, input_dim = self.INPUTS, activation="relu" ) )
        self.model.add( Dense( 32, activation="relu" ) )
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
        min_learning_rate = 0.00001  # once the learning rate reaches this value, do not decrease it further
        learning_rate_reduction_factor = 0.5  # the factor used when reducing the learning rate -> learning_rate *= learning_rate_reduction_factor
        patience = 3  # how many epochs to wait before reducing the learning rate when the loss plateaus

        # earlystopper = EarlyStopping(patience=5, verbose=1, mode="max", monitor="f1")
        checkpointer = ModelCheckpoint(self.MODEL_PATH, verbose=1, save_best_only=True, mode="max", monitor="f1")
        learning_rate_reduction = ReduceLROnPlateau(monitor='loss', mode="min", patience=patience, verbose=1,
                                                    factor=learning_rate_reduction_factor, min_lr=min_learning_rate)

        # checkpointer = ModelCheckpoint( self.MODEL_PATH, verbose=1, save_best_only=True )

        self.model.fit_generator(generator=self.learning_generator, epochs=epochs,
                                 callbacks=[checkpointer, learning_rate_reduction, self.evaluation_callback],
                                 validation_data=self.validation_generator )

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

        # If the input cannot be divided into sequencial chunks, the last chunk must be completed individually, and will overlap with the previous one
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

        return probabilities
